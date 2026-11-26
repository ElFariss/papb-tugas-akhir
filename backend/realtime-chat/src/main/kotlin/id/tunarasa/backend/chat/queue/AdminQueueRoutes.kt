package id.tunarasa.backend.chat.queue

import id.tunarasa.backend.chat.chat.SERVICE_MODES
import id.tunarasa.backend.chat.http.ChatAuth
import id.tunarasa.backend.chat.http.badRequest
import id.tunarasa.backend.chat.http.handle
import id.tunarasa.backend.chat.http.pathInt
import id.tunarasa.backend.chat.http.readJsonObject
import id.tunarasa.backend.chat.http.respondJson
import id.tunarasa.backend.chat.http.string
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonPrimitive

/**
 * Admin queue. Every route requires an admin/superadmin bearer token.
 *
 * - `GET  /api/v1/chat/admin/conversations[?status=&serviceMode=]`
 * - `POST /api/v1/chat/admin/conversations/{id}/assign`
 * - `POST /api/v1/chat/admin/respond` `{conversationId, response, responseType}`
 *
 * (`POST .../conversation/{id}/messages` and `.../resolve` live in `ChatRoutes` next to the conversation reads.)
 */
internal object AdminQueueRoutes {
    private val STATUSES = setOf("active", "waiting", "in_progress", "resolved")

    fun install(parent: Route, queue: AdminQueueService, auth: ChatAuth) {
        parent.route("/chat/admin") {
            get("/conversations") {
                call.handle {
                    auth.requireAdmin(call)
                    val status = call.request.queryParameters["status"]?.takeIf { it.isNotBlank() }
                    val mode = call.request.queryParameters["serviceMode"]?.takeIf { it.isNotBlank() }
                    if (status != null && status !in STATUSES) badRequest("status must be one of ${STATUSES.joinToString("|")}")
                    if (mode != null && mode !in SERVICE_MODES) badRequest("serviceMode must be one of ${SERVICE_MODES.joinToString("|")}")
                    call.respondJson(queue.list(status, mode))
                }
            }
            post("/conversations/{id}/assign") {
                call.handle {
                    val admin = auth.requireAdmin(call)
                    call.respondJson(queue.assign(call.pathInt("id"), admin))
                }
            }
            post("/respond") {
                call.handle {
                    val admin = auth.requireAdmin(call)
                    val body = call.readJsonObject()
                    // The Next.js client sends conversationId as a string.
                    val rawId = (body["conversationId"] as? JsonPrimitive)?.content ?: badRequest("conversationId is required")
                    val convId = rawId.toIntOrNull()?.takeIf { it > 0 } ?: badRequest("Invalid conversation ID")
                    val response = body.string("response", required = true, min = 1, max = 10_000)!!
                    val type = body.string("responseType", required = true, min = 1, max = 50)!!
                    call.respondJson(queue.respond(convId, admin, response, type))
                }
            }
        }
    }
}
