package id.tunarasa.backend.chat.chat

import id.tunarasa.backend.chat.http.ChatAuth
import id.tunarasa.backend.chat.http.SESSION_ID_REGEX
import id.tunarasa.backend.chat.http.array
import id.tunarasa.backend.chat.http.badRequest
import id.tunarasa.backend.chat.http.bool
import id.tunarasa.backend.chat.http.clientIp
import id.tunarasa.backend.chat.http.double
import id.tunarasa.backend.chat.http.enumOf
import id.tunarasa.backend.chat.http.handle
import id.tunarasa.backend.chat.http.int
import id.tunarasa.backend.chat.http.notFound
import id.tunarasa.backend.chat.http.pathInt
import id.tunarasa.backend.chat.http.queryIntOrNull
import id.tunarasa.backend.chat.http.readJsonObject
import id.tunarasa.backend.chat.http.respondJson
import id.tunarasa.backend.chat.http.sessionIdOrNull
import id.tunarasa.backend.chat.http.string
import id.tunarasa.backend.chat.http.userAgent
import id.tunarasa.backend.chat.queue.AdminQueueService
import id.tunarasa.contracts.ServiceMode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/**
 * `/api/v1/chat/...`. Migrated from the Next.js routes under `app/api/chat/...` and `conversation.py`.
 *
 * | Method | Path                                          | Caller                      |
 * |--------|-----------------------------------------------|-----------------------------|
 * | POST   | /chat/send-message                            | anonymous (admin-validation mode) |
 * | POST   | /chat/save-conversation                       | anonymous / admin           |
 * | GET    | /chat/conversation/{id}                       | owner session or admin      |
 * | GET    | /chat/conversation/{id}/messages[?lastMessageId=] | owner session or admin  |
 * | POST   | /chat/conversation/{id}/messages              | admin                       |
 * | POST   | /chat/conversation/{id}/resolve               | admin                       |
 *
 * Parity aliases (same handlers): `POST /conversation/save`, `GET /conversation/{id}`.
 */
internal object ChatRoutes {
    private const val MAX_SAVE_MESSAGES = 500

    fun install(parent: Route, chat: ChatService, queue: AdminQueueService, auth: ChatAuth) {
        parent.route("/chat") {
            post("/send-message") { call.handle { call.respondJson(sendMessage(call, chat)) } }
            post("/save-conversation") { call.handle { call.respondJson(save(call, chat, auth)) } }
            get("/conversation/{id}") { call.handle { call.respondJson(getConversation(call, chat, auth)) } }
            get("/conversation/{id}/messages") { call.handle { call.respondJson(getMessages(call, chat, auth)) } }
            post("/conversation/{id}/messages") {
                call.handle {
                    val admin = auth.requireAdmin(call)
                    val convId = call.pathInt("id")
                    val body = call.readJsonObject()
                    val text = body.string("message", required = true, min = 1, max = 10_000)!!
                    // `adminId` in the body is ignored: the acting admin is the authenticated principal.
                    val out = queue.postMessage(
                        convId = convId,
                        admin = admin,
                        message = text,
                        isRecommendation = body.bool("isLLMRecommendation") ?: false,
                        parentMessageId = body.int("parentMessageId", min = 1),
                        approvingRecommendation = body.bool("isApprovingLLMRecommendation") ?: false,
                    )
                    call.respondJson(out)
                }
            }
            post("/conversation/{id}/resolve") {
                call.handle {
                    val admin = auth.requireAdmin(call)
                    call.respondJson(queue.resolve(call.pathInt("id"), admin))
                }
            }
        }
        // Parity with FastAPI `/api/v1/conversation/...`
        parent.route("/conversation") {
            post("/save") { call.handle { call.respondJson(save(call, chat, auth)) } }
            get("/{id}") { call.handle { call.respondJson(getConversation(call, chat, auth)) } }
        }
    }

    private suspend fun sendMessage(call: ApplicationCall, chat: ChatService): JsonObject {
        val body = call.readJsonObject()
        // Reference limits the RAG question to 500 chars; accept the same here instead of failing later in AI-2.
        val message = body.string("message", required = true, min = 1, max = 500)!!
        val sessionId = body.string("sessionId", "session_id", required = true, pattern = SESSION_ID_REGEX)!!
        val mode = body.string("serviceMode", "service_mode")
        if (mode == ServiceMode.FULL_LLM_BOT.wire) badRequest("Full LLM bot mode should be handled by the client (use /rag/ask, then /chat/save-conversation)")
        if (mode != ServiceMode.BOT_WITH_ADMIN_VALIDATION.wire) badRequest("Invalid service mode")
        val inputMethod = enumOf("inputMethod", body.string("inputMethod", "input_method"), INPUT_METHODS, "text")
        return chat.sendMessage(
            SendMessageCommand(
                message = message,
                sessionId = sessionId,
                inputMethod = inputMethod,
                institutionId = body.int("institution_id", "institutionId", min = 1),
                institutionSlug = body.string("institution_slug", "institutionSlug", max = 100),
                userAgent = call.userAgent(),
                ip = call.clientIp(),
            ),
        )
    }

    private suspend fun save(call: ApplicationCall, chat: ChatService, auth: ChatAuth): JsonObject {
        val admin = auth.adminOrNull(call)
        val body = call.readJsonObject()
        val sessionId = body.string("session_id", "sessionId", required = true, pattern = SESSION_ID_REGEX)!!
        val mode = enumOf("service_mode", body.string("service_mode", "serviceMode"), SERVICE_MODES, ServiceMode.FULL_LLM_BOT.wire)
        val arr = body.array("messages", required = true)!!
        if (arr.isEmpty()) badRequest("messages must contain at least 1 item")
        if (arr.size > MAX_SAVE_MESSAGES) badRequest("messages may contain at most $MAX_SAVE_MESSAGES items")
        val now = Instant.now()
        val messages = arr.map { el ->
            val m = el as? JsonObject ?: badRequest("messages must contain objects")
            // Client-supplied `admin_id` is ignored (reference trusted it: any caller could impersonate an admin).
            SaveMessage(
                content = m.string("message_content", "content", required = true, min = 1, max = 10_000)!!,
                type = ChatService.mapSaveType(m.string("message_type", "type", required = true)!!, admin),
                inputMethod = enumOf("input_method", m.string("input_method", "inputMethod"), INPUT_METHODS, "text"),
                confidencePct = m.double("confidence", min = 0.0, max = 1.0)?.let { (it * 100).toInt() },
                createdAt = parseInstantOr(m.string("created_at", "timestamp"), now),
            )
        }
        return chat.saveConversation(
            sessionId = sessionId,
            serviceMode = mode,
            institutionId = body.int("institution_id", "institutionId", min = 1),
            messages = messages,
            userAgent = call.userAgent(),
            ip = call.clientIp(),
        )
    }

    private fun parseInstantOr(raw: String?, fallback: Instant): Instant {
        if (raw == null) return fallback
        return try {
            Instant.parse(raw)
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(raw).toInstant()
            } catch (_: Exception) {
                fallback // reference: unparsable timestamp -> now
            }
        }
    }

    /** 404 (not 403) for a foreign session, so conversation ids cannot be probed. */
    private suspend fun readableConversation(call: ApplicationCall, chat: ChatService, auth: ChatAuth): Pair<ConversationRow, Boolean> {
        val id = call.pathInt("id")
        val row = chat.requireConversation(id)
        val admin = auth.adminOrNull(call)
        if (admin == null && call.sessionIdOrNull() != row.sessionId) notFound("Conversation not found")
        return row to (admin != null)
    }

    private suspend fun getConversation(call: ApplicationCall, chat: ChatService, auth: ChatAuth): JsonObject {
        val (row, isAdmin) = readableConversation(call, chat, auth)
        return chat.conversationJson(row, includeRecommendations = isAdmin)
    }

    private suspend fun getMessages(call: ApplicationCall, chat: ChatService, auth: ChatAuth): JsonObject {
        val (row, isAdmin) = readableConversation(call, chat, auth)
        val after = call.queryIntOrNull("lastMessageId")
        return chat.messagesJson(row, after, includeRecommendations = isAdmin)
    }
}
