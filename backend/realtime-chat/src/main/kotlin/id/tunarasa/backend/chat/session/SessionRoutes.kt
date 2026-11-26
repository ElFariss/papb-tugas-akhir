package id.tunarasa.backend.chat.session

import id.tunarasa.backend.chat.ChatConfig
import id.tunarasa.backend.chat.http.ChatAuth
import id.tunarasa.backend.chat.http.SESSION_ID_REGEX
import id.tunarasa.backend.chat.http.array
import id.tunarasa.backend.chat.http.badRequest
import id.tunarasa.backend.chat.http.clientIp
import id.tunarasa.backend.chat.http.envelopeOk
import id.tunarasa.backend.chat.http.handle
import id.tunarasa.backend.chat.http.int
import id.tunarasa.backend.chat.http.readJsonObject
import id.tunarasa.backend.chat.http.respondJson
import id.tunarasa.backend.chat.http.userAgent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * `/api/v1/session/...`        (admin only; reference had no per-route auth but is behind the admin app)
 * `/api/v1/public-session/...` (no auth; for guests, feeds the Grafana gauge)
 *
 * Migrated from `session.py` and `public_session.py`.
 */
internal object SessionRoutes {

    fun install(parent: Route, sessions: SessionService, auth: ChatAuth, config: ChatConfig) {
        parent.route("/session") {
            post("/update-active") {
                call.handle(envelope = true) {
                    auth.requireAdmin(call)
                    val body = call.readJsonObject()
                    val ids = body.sessionIds(config.maxTrackedSessionsPerRequest)
                    body.int("active_users", "activeUsers", min = 0) // accepted for parity, ignored (reference ignored it too)
                    val n = sessions.replace(ids)
                    call.respondJson(envelopeOk(metrics(n, "Successfully updated $n active sessions")))
                }
            }
            get("/metrics") {
                call.handle(envelope = true) {
                    auth.requireAdmin(call)
                    val n = sessions.count()
                    call.respondJson(envelopeOk(metrics(n, "Current active sessions: $n")))
                }
            }
            post("/start/{session_id}") {
                call.handle(envelope = true) {
                    auth.requireAdmin(call)
                    val id = call.sessionPathId()
                    val n = sessions.start(id, call.userAgent(), call.clientIp())
                    call.respondJson(envelopeOk(metrics(n, "Session started: $id")))
                }
            }
            delete("/end/{session_id}") {
                call.handle(envelope = true) {
                    auth.requireAdmin(call)
                    val id = call.sessionPathId()
                    val n = sessions.end(id)
                    call.respondJson(envelopeOk(metrics(n, "Session ended: $id")))
                }
            }
        }

        parent.route("/public-session") {
            post("/track-sessions") {
                call.handle {
                    val ids = call.readJsonObject().sessionIds(config.maxTrackedSessionsPerRequest)
                    val n = sessions.track(ids)
                    call.respondJson(
                        buildJsonObject {
                            put("success", true)
                            put("active_sessions", n)
                            put("tracked_sessions", kotlinx.serialization.json.JsonArray(ids.map { JsonPrimitive(it) }))
                            put("timestamp", Instant.now().toString())
                        },
                    )
                }
            }
            post("/start-session/{session_id}") {
                call.handle {
                    val id = call.sessionPathId()
                    val n = sessions.start(id, call.userAgent(), call.clientIp())
                    call.respondJson(publicBody(id, n, "Session started: $id"))
                }
            }
            delete("/end-session/{session_id}") {
                call.handle {
                    val id = call.sessionPathId()
                    val n = sessions.end(id)
                    call.respondJson(publicBody(id, n, "Session ended: $id"))
                }
            }
            get("/session-count") {
                call.handle {
                    call.respondJson(
                        buildJsonObject {
                            put("success", true)
                            put("active_sessions", sessions.count())
                            put("timestamp", Instant.now().toString())
                        },
                    )
                }
            }
        }
    }

    private fun ApplicationCall.sessionPathId(): String {
        val id = parameters["session_id"].orEmpty()
        if (!SESSION_ID_REGEX.matches(id)) badRequest("Invalid session_id (letters, digits, '_' and '-' only, max 255)")
        return id
    }

    private fun JsonObject.sessionIds(max: Int): Set<String> {
        val arr = array("session_ids", required = true)!!
        if (arr.size > max) badRequest("session_ids may contain at most $max entries")
        return arr.map { el ->
            val p = el as? JsonPrimitive
            val v = p?.takeIf { it.isString }?.content ?: badRequest("session_ids must contain strings")
            if (!SESSION_ID_REGEX.matches(v)) badRequest("session_ids contains an invalid id")
            v
        }.toSet()
    }

    private fun metrics(active: Int, message: String): JsonObject = buildJsonObject {
        put("active_sessions", active)
        put("updated_at", Instant.now().toString())
        put("message", message)
    }

    private fun publicBody(id: String, active: Int, message: String): JsonObject = buildJsonObject {
        put("success", true)
        put("session_id", id)
        put("active_sessions", active)
        put("message", message)
        put("timestamp", Instant.now().toString())
    }
}
