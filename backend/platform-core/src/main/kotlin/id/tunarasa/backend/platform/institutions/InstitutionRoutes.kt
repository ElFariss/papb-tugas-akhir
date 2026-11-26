package id.tunarasa.backend.platform.institutions

import org.jetbrains.exposed.v1.core.eq

import id.tunarasa.backend.platform.auth.authorizeRoles
import id.tunarasa.backend.platform.auth.canAccessInstitution
import id.tunarasa.backend.platform.auth.userPrincipal
import id.tunarasa.backend.platform.db.DatabaseFactory
import id.tunarasa.backend.platform.db.InstitutionSettingsTable
import id.tunarasa.backend.platform.db.InstitutionsTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

@Serializable
data class InstitutionResponse(
    val id: Int,
    val name: String,
    val slug: String,
    val status: String,
)

@Serializable
data class CreateInstitutionRequest(
    val name: String,
    val slug: String? = null,
)

@Serializable
data class UpdateInstitutionRequest(
    val name: String? = null,
    val status: String? = null,
)

@Serializable
data class InstitutionSettingDto(
    val key: String,
    val value: String,
)

@Serializable
data class UpdateInstitutionSettingsRequest(
    val settings: List<InstitutionSettingDto>,
)

object InstitutionRoutes {
    fun register(route: Route) {
        route.route("/api/v1/institutions") {
            // GET /api/v1/institutions - Publicly list active institutions
            get {
                val list = DatabaseFactory.dbQuery {
                    InstitutionsTable
                        .selectAll()
                        .where { InstitutionsTable.status eq "active" }
                        .map {
                            InstitutionResponse(
                                id = it[InstitutionsTable.id],
                                name = it[InstitutionsTable.name],
                                slug = it[InstitutionsTable.slug],
                                status = it[InstitutionsTable.status],
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, list)
            }

            // GET /api/v1/institutions/{id}
            get("/{id}") {
                val instId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid institution ID"))

                val inst = DatabaseFactory.dbQuery {
                    InstitutionsTable
                        .selectAll()
                        .where { InstitutionsTable.id eq instId }
                        .singleOrNull()
                        ?.let {
                            InstitutionResponse(
                                id = it[InstitutionsTable.id],
                                name = it[InstitutionsTable.name],
                                slug = it[InstitutionsTable.slug],
                                status = it[InstitutionsTable.status],
                            )
                        }
                }

                if (inst == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Institution not found"))
                } else {
                    call.respond(HttpStatusCode.OK, inst)
                }
            }

            // Authenticated Admin Routes
            authenticate("supabase-jwt") {
                // POST /api/v1/institutions - Create institution (super_admin only)
                authorizeRoles("super_admin") {
                    post {
                        val req = call.receive<CreateInstitutionRequest>()
                        val generatedSlug = (req.slug ?: req.name)
                            .lowercase()
                            .replace(Regex("[^a-z0-9]+"), "-")
                            .trim('-')

                        val newId = DatabaseFactory.dbQuery {
                            InstitutionsTable.insert {
                                it[name] = req.name
                                it[slug] = generatedSlug
                                it[status] = "active"
                                it[createdAt] = Instant.now()
                                it[updatedAt] = Instant.now()
                            }[InstitutionsTable.id]
                        }

                        call.respond(
                            HttpStatusCode.Created,
                            InstitutionResponse(id = newId, name = req.name, slug = generatedSlug, status = "active"),
                        )
                    }
                }

                // PUT /api/v1/institutions/{id} - Update institution
                authorizeRoles("super_admin", "institution_admin") {
                    put("/{id}") {
                        val principal = call.userPrincipal()!!
                        val instId = call.parameters["id"]?.toIntOrNull()
                            ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid institution ID"))

                        if (!principal.canAccessInstitution(instId)) {
                            return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden: Cannot manage another institution"))
                        }

                        val req = call.receive<UpdateInstitutionRequest>()

                        DatabaseFactory.dbQuery {
                            InstitutionsTable.update({ InstitutionsTable.id eq instId }) {
                                if (req.name != null) it[name] = req.name
                                if (req.status != null && principal.roleName == "super_admin") it[status] = req.status
                                it[updatedAt] = Instant.now()
                            }
                        }

                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    }

                    // GET /api/v1/institutions/{id}/settings
                    get("/{id}/settings") {
                        val principal = call.userPrincipal()!!
                        val instId = call.parameters["id"]?.toIntOrNull()
                            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid institution ID"))

                        if (!principal.canAccessInstitution(instId)) {
                            return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden: Cannot access another institution settings"))
                        }

                        val settings = DatabaseFactory.dbQuery {
                            InstitutionSettingsTable
                                .selectAll()
                                .where { InstitutionSettingsTable.institutionId eq instId }
                                .map {
                                    InstitutionSettingDto(
                                        key = it[InstitutionSettingsTable.key],
                                        value = it[InstitutionSettingsTable.value],
                                    )
                                }
                        }

                        call.respond(HttpStatusCode.OK, settings)
                    }

                    // PUT /api/v1/institutions/{id}/settings
                    put("/{id}/settings") {
                        val principal = call.userPrincipal()!!
                        val instId = call.parameters["id"]?.toIntOrNull()
                            ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid institution ID"))

                        if (!principal.canAccessInstitution(instId)) {
                            return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden: Cannot manage another institution settings"))
                        }

                        val req = call.receive<UpdateInstitutionSettingsRequest>()

                        DatabaseFactory.dbQuery {
                            for (item in req.settings) {
                                val existing = InstitutionSettingsTable
                                    .selectAll()
                                    .where { (InstitutionSettingsTable.institutionId eq instId) and (InstitutionSettingsTable.key eq item.key) }
                                    .singleOrNull()

                                if (existing == null) {
                                    InstitutionSettingsTable.insert {
                                        it[institutionId] = instId
                                        it[key] = item.key
                                        it[value] = item.value
                                        it[updatedAt] = Instant.now()
                                    }
                                } else {
                                    InstitutionSettingsTable.update({ (InstitutionSettingsTable.institutionId eq instId) and (InstitutionSettingsTable.key eq item.key) }) {
                                        it[value] = item.value
                                        it[updatedAt] = Instant.now()
                                    }
                                }
                            }
                        }

                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    }
                }
            }
        }
    }
}
