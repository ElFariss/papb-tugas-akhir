package id.tunarasa.backend.chat.chat

import id.tunarasa.backend.chat.http.ApiJson
import id.tunarasa.backend.chat.http.ChatAuth
import id.tunarasa.backend.chat.http.sessionIdOrNull
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.sse.sse
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.ktor.sse.ServerSentEvent

/**
 * Live updates (WebSocket + SSE) replacing the reference's 2-second polling of
 * `GET /api/chat/conversation/{id}/messages?lastMessageId=`.
 *
 * In-process only: with more than one server instance, publish through Redis pub/sub instead
 * (swap [LiveUpdateHub.publish] / [LiveUpdateHub.events]); the routes do not change.
 */
internal data class ChatEvent(
    /** `message.created`, `conversation.updated`, `queue.updated`. */
    val type: String,
    val conversationId: Int,
    /** Events with `adminOnly = true` (pending LLM recommendations) never reach anonymous subscribers. */
    val adminOnly: Boolean,
    val data: JsonObject,
) {
    fun toJson(): String = ApiJson.encodeToString(
        JsonElement.serializer(),
        buildJsonObject {
            put("type", type)
            put("conversationId", conversationId)
            put("data", data)
        },
    )
}

internal class LiveUpdateHub {
    private val flow = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<ChatEvent> = flow.asSharedFlow()

    /** Never suspends and never fails; a slow subscriber loses the oldest events (it can refetch via REST). */
    fun publish(event: ChatEvent) {
        flow.tryEmit(event)
    }
}

/**
 * Channels (all under `/api/v1/chat`):
 * - `WS  /ws/conversation/{id}`   and `SSE /sse/conversation/{id}`   one conversation
 * - `WS  /ws/admin`               and `SSE /sse/admin`               every conversation (admin only)
 *
 * Auth: admin = `Authorization: Bearer` (or `?token=` for clients that cannot set headers on a socket).
 * Anonymous = `X-Session-Id` header or `?sessionId=` equal to the conversation's session.
 * A rejected WebSocket is closed with VIOLATED_POLICY; a rejected SSE stream sends one `error` event and ends.
 */
internal fun Route.chatLiveRoutes(chat: ChatService, hub: LiveUpdateHub, auth: ChatAuth) {
    route("/chat") {
        webSocket("/ws/conversation/{id}") {
            val convId = call.parameters["id"]?.toIntOrNull()
            val admin = auth.adminOrNull(call, queryTokenFallback = true)
            val allowed = convId != null && chat.canAccess(convId, admin != null, call.sessionIdOrNull())
            if (!allowed || convId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not allowed"))
                return@webSocket
            }
            val sender = launch {
                hub.events
                    .filter { it.conversationId == convId && (admin != null || !it.adminOnly) }
                    .collect { send(Frame.Text(it.toJson())) }
            }
            send(Frame.Text("""{"type":"connected","conversationId":$convId}"""))
            try {
                for (frame in incoming) {
                    // Server push only. A client "ping" text keeps proxies awake; everything else is ignored.
                    if (frame is Frame.Text && frame.readText() == "ping") send(Frame.Text("""{"type":"pong"}"""))
                }
            } finally {
                sender.cancel()
            }
        }

        webSocket("/ws/admin") {
            if (auth.adminOrNull(call, queryTokenFallback = true) == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not allowed"))
                return@webSocket
            }
            val sender = launch { hub.events.collect { send(Frame.Text(it.toJson())) } }
            send(Frame.Text("""{"type":"connected","scope":"admin"}"""))
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text && frame.readText() == "ping") send(Frame.Text("""{"type":"pong"}"""))
                }
            } finally {
                sender.cancel()
            }
        }

        sse("/sse/conversation/{id}") {
            val convId = call.parameters["id"]?.toIntOrNull()
            val admin = auth.adminOrNull(call, queryTokenFallback = true)
            val allowed = convId != null && chat.canAccess(convId, admin != null, call.sessionIdOrNull())
            if (!allowed || convId == null) {
                send(ServerSentEvent(data = "not_allowed", event = "error"))
                return@sse
            }
            val beat = launch {
                while (true) {
                    delay(15_000)
                    send(ServerSentEvent(data = "ping", event = "ping"))
                }
            }
            try {
                send(ServerSentEvent(data = """{"type":"connected","conversationId":$convId}""", event = "connected"))
                hub.events
                    .filter { it.conversationId == convId && (admin != null || !it.adminOnly) }
                    .collect { send(ServerSentEvent(data = it.toJson(), event = it.type)) }
            } finally {
                beat.cancel()
            }
        }

        sse("/sse/admin") {
            if (auth.adminOrNull(call, queryTokenFallback = true) == null) {
                send(ServerSentEvent(data = "not_allowed", event = "error"))
                return@sse
            }
            val beat = launch {
                while (true) {
                    delay(15_000)
                    send(ServerSentEvent(data = "ping", event = "ping"))
                }
            }
            try {
                send(ServerSentEvent(data = """{"type":"connected","scope":"admin"}""", event = "connected"))
                hub.events.collect { send(ServerSentEvent(data = it.toJson(), event = it.type)) }
            } finally {
                beat.cancel()
            }
        }
    }
}
