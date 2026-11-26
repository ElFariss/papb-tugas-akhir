package id.tunarasa.backend.platform.admin

import id.tunarasa.backend.platform.auth.authorizeRoles
import id.tunarasa.backend.platform.auth.userPrincipal
import id.tunarasa.backend.platform.db.AdminInvitationsTable
import id.tunarasa.backend.platform.db.AppSettingsTable
import id.tunarasa.backend.platform.db.DatabaseFactory
import id.tunarasa.backend.platform.db.InstitutionsTable
import id.tunarasa.backend.platform.db.RolesTable
import id.tunarasa.backend.platform.db.UsersTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Serializable
data class ServiceConfigResponse(
    val serviceMode: String,
    val maxWordLength: Int = 50,
    val maxQueryLength: Int = 500,
)

@Serializable
data class AppSettingDto(
    val key: String,
    val value: String,
    val description: String? = null,
)

@Serializable
data class CreateInvitationRequest(
    val email: String,
    val roleName: String = "institution_admin",
    val institutionId: Int? = null,
)

@Serializable
data class AcceptInvitationRequest(
    val token: String,
)

@Serializable
data class AdminUserDto(
    val id: String,
    val email: String,
    val roleName: String,
    val institutionId: Int? = null,
    val fullName: String? = null,
    val status: String,
)

@Serializable
data class UpdateUserRoleRequest(
    val roleName: String? = null,
    val institutionId: Int? = null,
    val status: String? = null,
)

object AdminRoutes {
    fun register(route: Route) {
        // Public / Global endpoint
        route.get("/api/v1/service-config") {
            val mode = DatabaseFactory.dbQuery {
                AppSettingsTable
                    .selectAll()
                    .where { AppSettingsTable.key eq "service_mode" }
                    .singleOrNull()
                    ?.get(AppSettingsTable.value) ?: "full_llm_bot"
            }

            call.respond(
                HttpStatusCode.OK,
                ServiceConfigResponse(serviceMode = mode),
            )
        }

        // Authenticated Admin endpoints
        route.route("/api/v1/admin") {
            authenticate("supabase-jwt") {
                // Settings Management (super_admin)
                authorizeRoles("super_admin") {
                    get("/settings") {
                        val settings = DatabaseFactory.dbQuery {
                            AppSettingsTable.selectAll().map {
                                AppSettingDto(
                                    key = it[AppSettingsTable.key],
                                    value = it[AppSettingsTable.value],
                                    description = it[AppSettingsTable.description],
                                )
                            }
                        }
                        call.respond(HttpStatusCode.OK, settings)
                    }

                    put("/settings") {
                        val req = call.receive<AppSettingDto>()
                        DatabaseFactory.dbQuery {
                            AppSettingsTable.update({ AppSettingsTable.key eq req.key }) {
                                it[value] = req.value
                                if (req.description != null) it[description] = req.description
                                it[updatedAt] = Instant.now()
                            }
                        }
                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    }
                }

                // User Management
                authorizeRoles("super_admin", "institution_admin") {
                    get("/users") {
                        val principal = call.userPrincipal()!!
                        val users = DatabaseFactory.dbQuery {
                            val query = UsersTable
                                .join(RolesTable, JoinType.INNER, UsersTable.roleId, RolesTable.id)
                                .selectAll()

                            if (principal.roleName == "institution_admin") {
                                query.where { UsersTable.institutionId eq (principal.institutionId ?: -1) }
                            }

                            query.map {
                                AdminUserDto(
                                    id = it[UsersTable.id],
                                    email = it[UsersTable.email],
                                    roleName = it[RolesTable.name],
                                    institutionId = it[UsersTable.institutionId],
                                    fullName = it[UsersTable.fullName],
                                    status = it[UsersTable.status],
                                )
                            }
                        }

                        call.respond(HttpStatusCode.OK, users)
                    }
                }

                authorizeRoles("super_admin") {
                    patch("/users/{id}") {
                        val targetUserId = call.parameters["id"]
                            ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing user ID"))

                        val req = call.receive<UpdateUserRoleRequest>()

                        DatabaseFactory.dbQuery {
                            val targetRoleId = if (req.roleName != null) {
                                RolesTable
                                    .selectAll()
                                    .where { RolesTable.name eq req.roleName }
                                    .singleOrNull()
                                    ?.get(RolesTable.id)
                            } else null

                            UsersTable.update({ UsersTable.id eq targetUserId }) {
                                if (targetRoleId != null) it[roleId] = targetRoleId
                                if (req.institutionId != null) it[institutionId] = req.institutionId
                                if (req.status != null) it[status] = req.status
                                it[updatedAt] = Instant.now()
                            }
                        }

                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    }
                }

                // Invitations Management
                authorizeRoles("super_admin", "institution_admin") {
                    post("/invitations") {
                        val principal = call.userPrincipal()!!
                        val req = call.receive<CreateInvitationRequest>()

                        val targetInstId = if (principal.roleName == "super_admin") {
                            req.institutionId
                        } else {
                            principal.institutionId
                        }

                        val generatedToken = UUID.randomUUID().toString()
                        val expires = Instant.now().plus(48, ChronoUnit.HOURS)

                        val instName = DatabaseFactory.dbQuery {
                            val role = RolesTable
                                .selectAll()
                                .where { RolesTable.name eq req.roleName }
                                .singleOrNull()
                                ?.get(RolesTable.id) ?: 2

                            val instName = targetInstId?.let { id ->
                                InstitutionsTable
                                    .selectAll()
                                    .where { InstitutionsTable.id eq id }
                                    .singleOrNull()
                                    ?.get(InstitutionsTable.name)
                            }

                            AdminInvitationsTable.insert {
                                it[email] = req.email
                                it[roleId] = role
                                it[institutionId] = targetInstId
                                it[token] = generatedToken
                                it[status] = "pending"
                                it[expiresAt] = expires
                                it[createdBy] = principal.userId
                                it[createdAt] = Instant.now()
                            }

                            instName
                        }

                        // Send invitation email asynchronously/synchronously via Resend API
                        InvitationMailer.sendInvitationEmail(req.email, generatedToken, instName)

                        call.respond(
                            HttpStatusCode.Created,
                            mapOf("token" to generatedToken, "status" to "invited", "expiresAt" to expires.toString()),
                        )
                    }

                    post("/invitations/accept") {
                        val principal = call.userPrincipal()!!
                        val req = call.receive<AcceptInvitationRequest>()

                        val (success, message) = DatabaseFactory.dbQuery {
                            val invite = AdminInvitationsTable
                                .selectAll()
                                .where {
                                    (AdminInvitationsTable.token eq req.token) and
                                        (AdminInvitationsTable.status eq "pending")
                                }
                                .singleOrNull()

                            if (invite == null) {
                                return@dbQuery Pair(false, "Invalid or expired invitation token")
                            }

                            if (invite[AdminInvitationsTable.expiresAt].isBefore(Instant.now())) {
                                AdminInvitationsTable.update({ AdminInvitationsTable.token eq req.token }) {
                                    it[status] = "expired"
                                }
                                return@dbQuery Pair(false, "Invitation token has expired")
                            }

                            val assignedRoleId = invite[AdminInvitationsTable.roleId]
                            val assignedInstId = invite[AdminInvitationsTable.institutionId]

                            // Update user's role and institution
                            UsersTable.update({ UsersTable.id eq principal.userId }) {
                                it[roleId] = assignedRoleId
                                it[institutionId] = assignedInstId
                                it[updatedAt] = Instant.now()
                            }

                            // Mark invitation as accepted
                            AdminInvitationsTable.update({ AdminInvitationsTable.token eq req.token }) {
                                it[status] = "accepted"
                            }

                            Pair(true, "Invitation accepted successfully")
                        }

                        if (success) {
                            call.respond(HttpStatusCode.OK, mapOf("message" to message))
                        } else {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to message))
                        }
                    }
                }
            }
        }
    }
}
