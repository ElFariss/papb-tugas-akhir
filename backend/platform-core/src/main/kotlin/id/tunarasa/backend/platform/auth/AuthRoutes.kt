package id.tunarasa.backend.platform.auth

import org.jetbrains.exposed.v1.core.eq

import id.tunarasa.backend.platform.db.DatabaseFactory
import id.tunarasa.backend.platform.db.RolesTable
import id.tunarasa.backend.platform.db.UserSyncLogTable
import id.tunarasa.backend.platform.db.UsersTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

@Serializable
data class SyncUserRequest(
    val fullName: String? = null,
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val role: String,
    val institutionId: Int? = null,
    val fullName: String? = null,
    val status: String,
)

object AuthRoutes {
    fun register(route: Route) {
        route.route("/api/v1/auth") {
            authenticate("supabase-jwt") {
                // GET /api/v1/auth/me - Get current user profile
                get("/me") {
                    val principal = call.userPrincipal()
                        ?: return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Unauthorized"),
                        )

                    val userDetails = DatabaseFactory.dbQuery {
                        UsersTable
                            .join(RolesTable, JoinType.INNER, UsersTable.roleId, RolesTable.id)
                            .selectAll()
                            .where { UsersTable.id eq principal.userId }
                            .singleOrNull()
                            ?.let { row ->
                                UserResponse(
                                    id = row[UsersTable.id],
                                    email = row[UsersTable.email],
                                    role = row[RolesTable.name],
                                    institutionId = row[UsersTable.institutionId],
                                    fullName = row[UsersTable.fullName],
                                    status = row[UsersTable.status],
                                )
                            }
                    }

                    if (userDetails == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "User profile not synced yet. Call /api/v1/auth/sync first."),
                        )
                    } else {
                        call.respond(HttpStatusCode.OK, userDetails)
                    }
                }

                // POST /api/v1/auth/sync - Sync Supabase user to local DB
                post("/sync") {
                    val principal = call.userPrincipal()
                        ?: return@post call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Unauthorized"),
                        )

                    val req = call.receiveNullable<SyncUserRequest>()

                    val syncedUser = DatabaseFactory.dbQuery {
                        // Check if default 'user' role exists
                        val defaultRoleId = RolesTable
                            .selectAll()
                            .where { RolesTable.name eq "user" }
                            .singleOrNull()
                            ?.get(RolesTable.id) ?: 3

                        val existingUser = UsersTable
                            .selectAll()
                            .where { UsersTable.id eq principal.userId }
                            .singleOrNull()

                        if (existingUser == null) {
                            // Insert new local user
                            UsersTable.insert {
                                it[id] = principal.userId
                                it[email] = principal.email
                                it[roleId] = defaultRoleId
                                it[fullName] = req?.fullName
                                it[status] = "active"
                                it[createdAt] = Instant.now()
                                it[updatedAt] = Instant.now()
                            }

                            UserSyncLogTable.insert {
                                it[userId] = principal.userId
                                it[eventType] = "user_created"
                                it[status] = "success"
                                it[syncedAt] = Instant.now()
                            }
                        } else {
                            // Update existing user full_name if provided
                            if (req?.fullName != null) {
                                UsersTable.update({ UsersTable.id eq principal.userId }) {
                                    it[fullName] = req.fullName
                                    it[updatedAt] = Instant.now()
                                }
                            }

                            UserSyncLogTable.insert {
                                it[userId] = principal.userId
                                it[eventType] = "user_synced"
                                it[status] = "success"
                                it[syncedAt] = Instant.now()
                            }
                        }

                        // Fetch updated profile
                        UsersTable
                            .join(RolesTable, JoinType.INNER, UsersTable.roleId, RolesTable.id)
                            .selectAll()
                            .where { UsersTable.id eq principal.userId }
                            .single()
                            .let { row ->
                                UserResponse(
                                    id = row[UsersTable.id],
                                    email = row[UsersTable.email],
                                    role = row[RolesTable.name],
                                    institutionId = row[UsersTable.institutionId],
                                    fullName = row[UsersTable.fullName],
                                    status = row[UsersTable.status],
                                )
                            }
                    }

                    call.respond(HttpStatusCode.OK, syncedUser)
                }
            }
        }
    }
}
