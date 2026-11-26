package id.tunarasa.backend.chat.chat

import id.tunarasa.backend.chat.AdminDirectory
import id.tunarasa.backend.chat.AdminPrincipal
import id.tunarasa.backend.chat.ChatConfig
import id.tunarasa.backend.chat.db.AdminQueueTable
import id.tunarasa.backend.chat.db.ConversationsTable
import id.tunarasa.backend.chat.db.MessagesTable
import id.tunarasa.backend.chat.db.tx
import id.tunarasa.backend.chat.http.ApiException
import id.tunarasa.backend.chat.http.badRequest
import id.tunarasa.backend.chat.http.notFound
import id.tunarasa.backend.chat.http.putInstant
import id.tunarasa.backend.chat.qa.QaEntry
import id.tunarasa.backend.chat.qa.QaLogService
import id.tunarasa.contracts.RagLlmService
import id.tunarasa.contracts.ServiceMode
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Clock
import java.time.Instant

internal object MessageType {
    const val USER = "user"
    const val ADMIN = "admin"
    const val LLM_BOT = "llm_bot"
    const val LLM_RECOMMENDATION = "llm_recommendation"
    const val SYSTEM = "system"
}

internal object ConvStatus {
    const val ACTIVE = "active"
    const val WAITING = "waiting"
    const val IN_PROGRESS = "in_progress"
    const val RESOLVED = "resolved"
}

internal val INPUT_METHODS = setOf("text", "speech", "gesture")
internal val PRIORITIES = setOf("low", "normal", "high", "urgent")
internal val SERVICE_MODES = ServiceMode.entries.map { it.wire }.toSet()

internal data class ConversationRow(
    val id: Int,
    val sessionId: String,
    val serviceMode: String,
    val status: String,
    val priority: String,
    val isActive: Boolean,
    val institutionId: Int?,
    val assignedAdminId: Int?,
    val createdAt: Instant,
    val lastMessageAt: Instant?,
)

internal fun ResultRow.toConversation() = ConversationRow(
    id = this[ConversationsTable.conversationId],
    sessionId = this[ConversationsTable.sessionId],
    serviceMode = this[ConversationsTable.serviceMode],
    status = this[ConversationsTable.status],
    priority = this[ConversationsTable.priority],
    isActive = this[ConversationsTable.isActive],
    institutionId = this[ConversationsTable.institutionId],
    assignedAdminId = this[ConversationsTable.assignedAdminId],
    createdAt = this[ConversationsTable.createdAt],
    lastMessageAt = this[ConversationsTable.lastMessageAt],
)

internal data class SendMessageCommand(
    val message: String,
    val sessionId: String,
    val inputMethod: String,
    val institutionId: Int?,
    val institutionSlug: String?,
    val userAgent: String?,
    val ip: String?,
)

internal data class SaveMessage(
    val content: String,
    val type: String,
    val inputMethod: String,
    val confidencePct: Int?,
    val createdAt: Instant,
)

/** Result of the LLM call; never throws. */
private class LlmOutcome(val text: String, val confidencePct: Int?, val contextJson: String?, val ok: Boolean)

/**
 * Dual-mode chat.
 * - `full_llm_bot`: the client calls `POST /rag/ask` and then persists with `save-conversation` + `qa-log`.
 * - `bot_with_admin_validation`: [sendMessage] stores the user message, asks the LLM, stores the answer as a
 *   pending `llm_recommendation` (admin-only) and queues the conversation for an admin ([AdminQueueService]).
 */
internal class ChatService(
    private val db: Database,
    private val rag: RagLlmService,
    private val hub: LiveUpdateHub,
    private val qaLog: QaLogService,
    private val config: ChatConfig,
    private val clock: Clock,
    private val admins: AdminDirectory,
) {
    private val log = LoggerFactory.getLogger(ChatService::class.java)

    suspend fun conversation(id: Int): ConversationRow? = db.tx {
        ConversationsTable.selectAll().where { ConversationsTable.conversationId eq id }.limit(1)
            .firstOrNull()?.toConversation()
    }

    /** Admin sees everything; an anonymous caller only the conversation of the session id it presents. */
    suspend fun canAccess(convId: Int, isAdmin: Boolean, sessionId: String?): Boolean {
        if (isAdmin) return conversation(convId) != null
        if (sessionId == null) return false
        return conversation(convId)?.sessionId == sessionId
    }

    // ------------------------------------------------------------------ send-message (admin validation mode)

    suspend fun sendMessage(cmd: SendMessageCommand): JsonObject {
        val now = clock.instant()
        // 1) conversation + queue row + user message, atomically.
        val (convId, userMessageId) = db.tx {
            val existing = ConversationsTable.selectAll()
                .where { (ConversationsTable.sessionId eq cmd.sessionId) and (ConversationsTable.isActive eq true) }
                .orderBy(ConversationsTable.createdAt, SortOrder.DESC)
                .limit(1).firstOrNull()?.toConversation()

            val cid: Int
            if (existing == null) {
                cid = ConversationsTable.insert {
                    it[ConversationsTable.isActive] = true
                    it[ConversationsTable.sessionId] = cmd.sessionId
                    it[ConversationsTable.userAgent] = cmd.userAgent
                    it[ConversationsTable.ipAddress] = cmd.ip
                    it[ConversationsTable.serviceMode] = ServiceMode.BOT_WITH_ADMIN_VALIDATION.wire
                    it[ConversationsTable.institutionId] = cmd.institutionId
                    it[ConversationsTable.status] = ConvStatus.WAITING
                    it[ConversationsTable.priority] = "normal"
                    it[ConversationsTable.lastMessageAt] = now
                    it[ConversationsTable.createdAt] = now
                    it[ConversationsTable.updatedAt] = now
                }[ConversationsTable.conversationId]
                AdminQueueTable.insert {
                    it[AdminQueueTable.conversationId] = cid
                    it[AdminQueueTable.priority] = "normal"
                    it[AdminQueueTable.status] = "waiting"
                    it[AdminQueueTable.queuedAt] = now
                }
            } else {
                cid = existing.id
                // A new user message re-opens a resolved conversation (reference behaviour).
                ConversationsTable.update({ ConversationsTable.conversationId eq cid }) {
                    it[ConversationsTable.status] = ConvStatus.WAITING
                    it[ConversationsTable.resolvedAt] = null
                    it[ConversationsTable.lastMessageAt] = now
                    it[ConversationsTable.updatedAt] = now
                }
                AdminQueueTable.update({ AdminQueueTable.conversationId eq cid }) {
                    it[AdminQueueTable.status] = "waiting"
                    it[AdminQueueTable.resolvedAt] = null
                }
            }
            val mid = MessagesTable.insert {
                it[MessagesTable.conversationId] = cid
                it[MessagesTable.messageContent] = cmd.message
                it[MessagesTable.messageType] = MessageType.USER
                it[MessagesTable.isRead] = false
                it[MessagesTable.inputMethod] = cmd.inputMethod
                it[MessagesTable.createdAt] = now
            }[MessagesTable.messageId]
            cid to mid
        }

        hub.publish(
            ChatEvent(
                "message.created", convId, adminOnly = false,
                buildJsonObject {
                    put("id", userMessageId.toString()); put("type", MessageType.USER); put("content", cmd.message)
                    put("inputMethod", cmd.inputMethod); putInstant("timestamp", now)
                },
            ),
        )
        hub.publish(ChatEvent("queue.updated", convId, adminOnly = true, buildJsonObject { put("status", ConvStatus.WAITING) }))

        // 2) LLM recommendation, outside any DB transaction (up to ragTimeoutMillis).
        val started = clock.millis()
        val llm = askLlm(cmd.message, convId, cmd.institutionSlug)
        val elapsed = (clock.millis() - started).toInt()
        val answeredAt = clock.instant()

        val recId = db.tx {
            val id = MessagesTable.insert {
                it[MessagesTable.conversationId] = convId
                it[MessagesTable.messageContent] = llm.text
                it[MessagesTable.messageType] = MessageType.LLM_RECOMMENDATION
                it[MessagesTable.parentMessageId] = userMessageId
                it[MessagesTable.confidence] = llm.confidencePct
                it[MessagesTable.isRead] = false
                it[MessagesTable.inputMethod] = "llm_generated"
                it[MessagesTable.createdAt] = answeredAt
            }[MessagesTable.messageId]
            ConversationsTable.update({ ConversationsTable.conversationId eq convId }) {
                it[ConversationsTable.lastMessageAt] = answeredAt
                it[ConversationsTable.updatedAt] = answeredAt
            }
            id
        }
        hub.publish(
            ChatEvent(
                "message.created", convId, adminOnly = true,
                buildJsonObject {
                    put("id", recId.toString()); put("type", MessageType.LLM_RECOMMENDATION); put("content", llm.text)
                    put("confidence", llm.confidencePct); putInstant("timestamp", answeredAt)
                },
            ),
        )

        // 3) QA log. Failure here must not fail the user's request (reference: "warn and continue").
        try {
            qaLog.log(
                QaEntry(
                    conversationId = convId,
                    institutionId = cmd.institutionId ?: config.defaultInstitutionId,
                    question = cmd.message,
                    answer = llm.text,
                    confidence = llm.confidencePct ?: 0,
                    responseTime = elapsed,
                    contextUsed = llm.contextJson,
                    serviceMode = ServiceMode.BOT_WITH_ADMIN_VALIDATION.wire,
                    respondedBy = "llm",
                    llmRecommendationUsed = false,
                    gestureInput = if (cmd.inputMethod == "gesture") cmd.message else null,
                ),
            )
        } catch (e: Exception) {
            log.warn("QA log failed for conversation {}: {}", convId, e.toString())
        }

        return buildJsonObject {
            put("success", true)
            put("conversationId", convId)
            put("status", ConvStatus.WAITING)
            put("message", "Message received, LLM response generated and waiting for admin approval")
        }
    }

    private suspend fun askLlm(question: String, convId: Int, slug: String?): LlmOutcome {
        fun failure(reason: String) = LlmOutcome("Error generating LLM response: $reason", 0, null, ok = false)
        return try {
            val answer = withTimeoutOrNull(config.ragTimeoutMillis) {
                rag.askRag(question, "admin-validation-$convId", slug)
            } ?: return failure("timeout")
            val sources = buildJsonArray {
                answer.sources.forEach { s ->
                    add(buildJsonObject {
                        put("documentId", s.documentId); put("excerpt", s.excerpt.take(500)); put("score", s.score)
                    })
                }
            }
            LlmOutcome(
                text = answer.text.ifBlank { "Unable to generate response" },
                confidencePct = (answer.confidence * 100).toInt().coerceIn(0, 100),
                contextJson = sources.toString(),
                ok = true,
            )
        } catch (e: IllegalArgumentException) { // AI-2: invalid input
            failure("invalid question")
        } catch (e: IOException) { // AI-2: failure
            log.warn("askRag failed for conversation {}: {}", convId, e.toString())
            failure("service unavailable")
        } catch (e: NotImplementedError) { // AI-2 stub not implemented yet
            failure("service not available")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("askRag unexpected error for conversation {}: {}", convId, e.toString())
            failure("unexpected error")
        }
    }

    // ------------------------------------------------------------------ save-conversation (full_llm_bot mode)

    suspend fun saveConversation(
        sessionId: String,
        serviceMode: String,
        institutionId: Int?,
        messages: List<SaveMessage>,
        userAgent: String?,
        ip: String?,
    ): JsonObject {
        val now = clock.instant()
        val last = messages.maxOf { it.createdAt }
        val (convId, createdAt) = db.tx {
            val cid = ConversationsTable.insert {
                it[ConversationsTable.isActive] = true
                it[ConversationsTable.sessionId] = sessionId
                it[ConversationsTable.userAgent] = userAgent
                it[ConversationsTable.ipAddress] = ip
                it[ConversationsTable.serviceMode] = serviceMode
                it[ConversationsTable.institutionId] = institutionId
                it[ConversationsTable.status] = ConvStatus.ACTIVE
                it[ConversationsTable.priority] = "normal"
                it[ConversationsTable.lastMessageAt] = last
                it[ConversationsTable.createdAt] = now
                it[ConversationsTable.updatedAt] = now
            }[ConversationsTable.conversationId]
            // One statement per row keeps this portable and the batch is small (<= 500, validated upstream).
            messages.forEach { m ->
                MessagesTable.insert {
                    it[MessagesTable.conversationId] = cid
                    it[MessagesTable.messageContent] = m.content
                    it[MessagesTable.messageType] = m.type
                    it[MessagesTable.inputMethod] = m.inputMethod
                    it[MessagesTable.confidence] = m.confidencePct
                    it[MessagesTable.isRead] = false
                    it[MessagesTable.createdAt] = m.createdAt
                }
            }
            cid to now
        }
        return buildJsonObject {
            put("success", true)
            put("message", "Conversation saved successfully")
            put("conversationId", convId)
            put("savedMessages", messages.size)
            put("failedMessages", 0)
            put(
                "data",
                buildJsonObject {
                    put("conversation_id", convId); put("session_id", sessionId)
                    put("messages_saved", messages.size); put("service_mode", serviceMode)
                    putInstant("created_at", createdAt)
                },
            )
        }
    }

    // ------------------------------------------------------------------ reads

    /** Reference backend shape: `GET /api/v1/conversation/{id}`. */
    suspend fun conversationJson(row: ConversationRow, includeRecommendations: Boolean): JsonObject {
        val msgs = messageRows(row.id, null, includeRecommendations)
        val names = admins.displayNames(msgs.mapNotNull { it[MessagesTable.adminId] }.toSet())
        return buildJsonObject {
            put("success", true)
            put(
                "data",
                buildJsonObject {
                    put("conversation_id", row.id); put("session_id", row.sessionId)
                    put("service_mode", row.serviceMode); put("status", row.status); put("priority", row.priority)
                    put("is_active", row.isActive); putInstant("created_at", row.createdAt)
                    put(
                        "messages",
                        JsonArray(
                            msgs.map { m ->
                                buildJsonObject {
                                    put("message_id", m[MessagesTable.messageId]); put("content", m[MessagesTable.messageContent])
                                    put("type", m[MessagesTable.messageType]); put("input_method", m[MessagesTable.inputMethod])
                                    put("confidence", m[MessagesTable.confidence]); put("admin_id", m[MessagesTable.adminId])
                                    put("admin_name", m[MessagesTable.adminId]?.let { names[it] })
                                    put("is_read", m[MessagesTable.isRead]); putInstant("created_at", m[MessagesTable.createdAt])
                                }
                            },
                        ),
                    )
                },
            )
        }
    }

    /**
     * Reference frontend shape: `GET /api/chat/conversation/{id}/messages[?lastMessageId=]`.
     * With `afterMessageId` the array is returned as `newMessages`, otherwise as `messages`.
     */
    suspend fun messagesJson(row: ConversationRow, afterMessageId: Int?, includeRecommendations: Boolean): JsonObject {
        val msgs = messageRows(row.id, afterMessageId, includeRecommendations)
        val names = admins.displayNames(msgs.mapNotNull { it[MessagesTable.adminId] }.toSet())
        val formatted = JsonArray(
            msgs.map { m ->
                buildJsonObject {
                    put("id", m[MessagesTable.messageId].toString())
                    put("type", m[MessagesTable.messageType])
                    put("content", m[MessagesTable.messageContent])
                    putInstant("timestamp", m[MessagesTable.createdAt])
                    put("confidence", m[MessagesTable.confidence])
                    put("adminName", m[MessagesTable.adminId]?.let { names[it] ?: "Admin" })
                    put("inputMethod", m[MessagesTable.inputMethod])
                }
            },
        )
        return buildJsonObject {
            put("success", true)
            put("conversationId", row.id)
            put("status", row.status)
            if (afterMessageId != null) put("newMessages", formatted) else put("messages", formatted)
        }
    }

    private suspend fun messageRows(convId: Int, afterId: Int?, includeRecommendations: Boolean): List<ResultRow> = db.tx {
        var where = MessagesTable.conversationId eq convId
        if (afterId != null) where = where and (MessagesTable.messageId greater afterId)
        // Pending LLM recommendations are for admins only. The reference returned them to anyone who knew the id.
        if (!includeRecommendations) where = where and (MessagesTable.messageType neq MessageType.LLM_RECOMMENDATION)
        MessagesTable.selectAll().where { where }
            .orderBy(MessagesTable.createdAt, SortOrder.ASC)
            .limit(2000)
            .toList()
    }

    /** Used by routes that need a 404 instead of null. */
    suspend fun requireConversation(id: Int): ConversationRow = conversation(id) ?: notFound("Conversation not found")

    companion object {
        /** Maps client message types onto the DB vocabulary; enforces who may write which type. */
        fun mapSaveType(raw: String, admin: AdminPrincipal?): String = when (raw) {
            "user" -> MessageType.USER
            "assistant" -> MessageType.LLM_BOT
            "admin", "system" -> if (admin != null) raw else throw ApiException(
                io.ktor.http.HttpStatusCode.Forbidden, "message_type '$raw' requires admin authentication",
            )
            else -> badRequest("message_type must be one of user|assistant|admin|system")
        }
    }
}
