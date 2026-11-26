package id.tunarasa.backend.chat.queue

import id.tunarasa.backend.chat.AdminPrincipal
import id.tunarasa.backend.chat.ChatConfig
import id.tunarasa.backend.chat.chat.ChatEvent
import id.tunarasa.backend.chat.chat.ConvStatus
import id.tunarasa.backend.chat.chat.LiveUpdateHub
import id.tunarasa.backend.chat.chat.MessageType
import id.tunarasa.backend.chat.chat.toConversation
import id.tunarasa.backend.chat.db.AdminQueueTable
import id.tunarasa.backend.chat.db.ConversationsTable
import id.tunarasa.backend.chat.db.MessagesTable
import id.tunarasa.backend.chat.db.tx
import id.tunarasa.backend.chat.http.badRequest
import id.tunarasa.backend.chat.http.conflict
import id.tunarasa.backend.chat.http.notFound
import id.tunarasa.backend.chat.http.putInstant
import id.tunarasa.backend.chat.qa.QaLogService
import id.tunarasa.contracts.ServiceMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Admin queue: list, claim (assign), approve/answer, resolve.
 * Queue status: `waiting -> assigned -> resolved` (a new user message sets it back to `waiting`).
 * Conversation status: `waiting -> in_progress -> resolved`.
 */
internal class AdminQueueService(
    private val db: Database,
    private val hub: LiveUpdateHub,
    private val qaLog: QaLogService,
    private val config: ChatConfig,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(AdminQueueService::class.java)

    // ------------------------------------------------------------------ list (+ stats)

    suspend fun list(statusFilter: String?, modeFilter: String?): JsonObject {
        val now = clock.instant()
        val since = now.minus(config.adminListWindowHours, ChronoUnit.HOURS)
        val startOfDay = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC)

        data class Snapshot(
            val convs: List<id.tunarasa.backend.chat.chat.ConversationRow>,
            val queue: Map<Int, org.jetbrains.exposed.v1.core.ResultRow>,
            val counts: Map<Int, Int>,
            val lastUser: Map<Int, Pair<String, String?>>,
            val todayResolved: Long,
            val windowQueue: List<org.jetbrains.exposed.v1.core.ResultRow>,
        )

        val snap = db.tx {
            var w: Op<Boolean> = (ConversationsTable.isActive eq true) and (ConversationsTable.createdAt greaterEq since)
            if (statusFilter != null) w = w and (ConversationsTable.status eq statusFilter)
            // Reference: both service modes, so the admin sees full_llm_bot traffic too.
            w = if (modeFilter != null) w and (ConversationsTable.serviceMode eq modeFilter)
            else w and (ConversationsTable.serviceMode inList ServiceMode.entries.map { it.wire })

            val convs = ConversationsTable.selectAll().where { w }
                .orderBy(ConversationsTable.lastMessageAt, SortOrder.DESC)
                .limit(config.adminListLimit)
                .map { it.toConversation() }
            val ids = convs.map { it.id }

            val queue = if (ids.isEmpty()) emptyMap() else
                AdminQueueTable.selectAll().where { AdminQueueTable.conversationId inList ids }
                    .associateBy { it[AdminQueueTable.conversationId] }

            val cnt = MessagesTable.messageId.count()
            val counts = if (ids.isEmpty()) emptyMap() else
                MessagesTable.select(MessagesTable.conversationId, cnt)
                    .where { MessagesTable.conversationId inList ids }
                    .groupBy(MessagesTable.conversationId)
                    .associate { it[MessagesTable.conversationId] to it[cnt].toInt() }

            // newest user message per conversation: input method + preview
            val lastUser = LinkedHashMap<Int, Pair<String, String?>>()
            if (ids.isNotEmpty()) {
                MessagesTable.selectAll()
                    .where { (MessagesTable.conversationId inList ids) and (MessagesTable.messageType eq MessageType.USER) }
                    .orderBy(MessagesTable.createdAt, SortOrder.DESC)
                    .limit(500)
                    .forEach { r -> lastUser.getOrPut(r[MessagesTable.conversationId]) { r[MessagesTable.messageContent] to r[MessagesTable.inputMethod] } }
            }

            val today = ConversationsTable.selectAll().where {
                (ConversationsTable.serviceMode eq ServiceMode.BOT_WITH_ADMIN_VALIDATION.wire) and
                    (ConversationsTable.status eq ConvStatus.RESOLVED) and
                    (ConversationsTable.resolvedAt greaterEq startOfDay)
            }.count()

            val windowQueue = AdminQueueTable.selectAll().where { AdminQueueTable.queuedAt greaterEq since }.limit(500).toList()
            Snapshot(convs, queue, counts, lastUser, today, windowQueue)
        }

        val items = snap.convs.map { c ->
            val q = snap.queue[c.id]
            val queuedAt = q?.get(AdminQueueTable.queuedAt) ?: c.createdAt
            val last = snap.lastUser[c.id]
            val shortId = c.sessionId.takeLast(8)
            buildJsonObject {
                put("id", c.id.toString())
                put("sessionId", c.sessionId)
                put("userName", "User-$shortId")
                put("status", c.status)
                put("priority", c.priority)
                put("lastMessage", last?.first?.take(120) ?: "")
                putInstant("lastMessageAt", c.lastMessageAt ?: c.createdAt)
                putInstant("queuedAt", queuedAt)
                put("messageCount", snap.counts[c.id] ?: 0)
                put("waitTime", Duration.between(queuedAt, now).toMinutes())
                put("assignedAdminId", q?.get(AdminQueueTable.assignedAdminId) ?: c.assignedAdminId)
                put("inputMethod", last?.second ?: "text")
                put("serviceMode", c.serviceMode)
                put("queueStatus", q?.get(AdminQueueTable.status))
            }
        }

        val waiting = snap.convs.count { it.status == ConvStatus.WAITING || snap.queue[it.id]?.get(AdminQueueTable.status) == "waiting" }
        val active = snap.convs.count { it.status == ConvStatus.IN_PROGRESS || snap.queue[it.id]?.get(AdminQueueTable.status) == "in_progress" }
        // Reference returned random numbers here. These are measured: minutes from queued to first claim / to resolution.
        val avgResponse = avgMinutes(snap.windowQueue.mapNotNull { r -> r[AdminQueueTable.assignedAt]?.let { Duration.between(r[AdminQueueTable.queuedAt], it) } })
        val avgResolution = avgMinutes(snap.windowQueue.mapNotNull { r -> r[AdminQueueTable.resolvedAt]?.let { Duration.between(r[AdminQueueTable.queuedAt], it) } })

        return buildJsonObject {
            put("success", true)
            put("conversations", JsonArray(items))
            put(
                "stats",
                buildJsonObject {
                    put("totalConversations", snap.convs.size)
                    put("activeConversations", active)
                    put("waitingInQueue", waiting)
                    put("averageResponseTime", avgResponse)
                    put("averageResolutionTime", avgResolution)
                    put("todayResolved", snap.todayResolved)
                },
            )
        }
    }

    private fun avgMinutes(ds: List<Duration>): Long =
        if (ds.isEmpty()) 0 else Math.round(ds.map { it.toMillis() }.average() / 60_000.0)

    // ------------------------------------------------------------------ claim

    /** Atomic claim: succeeds only while the item is unassigned (or already mine). */
    suspend fun assign(convId: Int, admin: AdminPrincipal): JsonObject {
        val now = clock.instant()
        db.tx {
            ConversationsTable.selectAll().where { ConversationsTable.conversationId eq convId }.limit(1).firstOrNull()
                ?: notFound("Conversation not found")
            val q = AdminQueueTable.selectAll().where { AdminQueueTable.conversationId eq convId }.limit(1).firstOrNull()
            if (q == null) {
                AdminQueueTable.insert {
                    it[AdminQueueTable.conversationId] = convId
                    it[AdminQueueTable.priority] = "normal"
                    it[AdminQueueTable.status] = "assigned"
                    it[AdminQueueTable.assignedAdminId] = admin.userId
                    it[AdminQueueTable.queuedAt] = now
                    it[AdminQueueTable.assignedAt] = now
                }
            } else {
                val claimed = AdminQueueTable.update({
                    (AdminQueueTable.conversationId eq convId) and
                        (AdminQueueTable.status inList listOf("waiting", "assigned", "in_progress")) and
                        (AdminQueueTable.assignedAdminId.isNull() or (AdminQueueTable.assignedAdminId eq admin.userId))
                }) {
                    it[AdminQueueTable.status] = "assigned"
                    it[AdminQueueTable.assignedAdminId] = admin.userId
                    it[AdminQueueTable.assignedAt] = q[AdminQueueTable.assignedAt] ?: now
                }
                if (claimed == 0) conflict("Conversation is already assigned to another admin or resolved")
            }
            ConversationsTable.update({ ConversationsTable.conversationId eq convId }) {
                it[ConversationsTable.status] = ConvStatus.IN_PROGRESS
                it[ConversationsTable.assignedAdminId] = admin.userId
                it[ConversationsTable.updatedAt] = now
            }
        }
        publishStatus(convId, ConvStatus.IN_PROGRESS)
        return buildJsonObject {
            put("success", true)
            put("conversationId", convId)
            put("status", ConvStatus.IN_PROGRESS)
            put("assignedAdminId", admin.userId)
        }
    }

    // ------------------------------------------------------------------ answer / approve

    /** Reference `POST /api/chat/admin/respond`. `approved_recommendation` => confidence 100, else 95. */
    suspend fun respond(convId: Int, admin: AdminPrincipal, response: String, responseType: String): JsonObject {
        val approved = responseType == "approved_recommendation"
        val (messageId, _) = writeAdminAnswer(
            convId, admin, response, confidence = if (approved) 100 else 95,
            parentMessageId = null, deleteRecommendations = false,
        )
        qaMark(convId, admin, response, approved)
        return buildJsonObject {
            put("success", true)
            put(
                "data",
                buildJsonObject {
                    put("messageId", messageId); put("conversationId", convId)
                    put("status", ConvStatus.RESOLVED); put("responseType", responseType)
                },
            )
        }
    }

    /** Reference `POST /api/chat/conversation/{id}/messages` (admin side). */
    suspend fun postMessage(
        convId: Int,
        admin: AdminPrincipal,
        message: String,
        isRecommendation: Boolean,
        parentMessageId: Int?,
        approvingRecommendation: Boolean,
    ): JsonObject {
        val (messageId, at) = if (isRecommendation) {
            writeRecommendation(convId, admin, message, parentMessageId)
        } else {
            writeAdminAnswer(
                convId, admin, message, confidence = if (approvingRecommendation) 100 else null,
                parentMessageId = parentMessageId, deleteRecommendations = approvingRecommendation,
            ).also { qaMark(convId, admin, message, approvingRecommendation) }
        }
        return buildJsonObject {
            put("success", true)
            put("message", "Admin response sent successfully")
            put("messageId", messageId)
            put("adminName", admin.fullName ?: admin.email ?: "Admin")
            putInstant("timestamp", at)
        }
    }

    private suspend fun writeAdminAnswer(
        convId: Int,
        admin: AdminPrincipal,
        text: String,
        confidence: Int?,
        parentMessageId: Int?,
        deleteRecommendations: Boolean,
    ): Pair<Int, Instant> {
        val now = clock.instant()
        val messageId = db.tx {
            requireConversationRow(convId)
            requireParentInConversation(parentMessageId, convId)
            val id = MessagesTable.insert {
                it[MessagesTable.conversationId] = convId
                it[MessagesTable.messageContent] = text
                it[MessagesTable.messageType] = MessageType.ADMIN
                it[MessagesTable.adminId] = admin.userId
                it[MessagesTable.parentMessageId] = parentMessageId
                it[MessagesTable.confidence] = confidence
                it[MessagesTable.isRead] = false
                it[MessagesTable.inputMethod] = "text"
                it[MessagesTable.createdAt] = now
            }[MessagesTable.messageId]
            if (deleteRecommendations) {
                MessagesTable.deleteWhere {
                    (MessagesTable.conversationId eq convId) and (MessagesTable.messageType eq MessageType.LLM_RECOMMENDATION)
                }
            }
            ConversationsTable.update({ ConversationsTable.conversationId eq convId }) {
                it[ConversationsTable.status] = ConvStatus.RESOLVED
                it[ConversationsTable.assignedAdminId] = admin.userId
                it[ConversationsTable.lastMessageAt] = now
                it[ConversationsTable.resolvedAt] = now
                it[ConversationsTable.updatedAt] = now
            }
            resolveQueueRow(convId, admin.userId, now)
            id
        }
        hub.publish(
            ChatEvent(
                "message.created", convId, adminOnly = false,
                buildJsonObject {
                    put("id", messageId.toString()); put("type", MessageType.ADMIN); put("content", text)
                    put("adminName", admin.fullName ?: admin.email ?: "Admin"); put("confidence", confidence)
                    putInstant("timestamp", now)
                },
            ),
        )
        publishStatus(convId, ConvStatus.RESOLVED)
        return messageId to now
    }

    /** An admin-authored LLM recommendation (stays pending: conversation status unchanged, admin-only event). */
    private suspend fun writeRecommendation(convId: Int, admin: AdminPrincipal, text: String, parentMessageId: Int?): Pair<Int, Instant> {
        val now = clock.instant()
        val id = db.tx {
            requireConversationRow(convId)
            requireParentInConversation(parentMessageId, convId)
            val mid = MessagesTable.insert {
                it[MessagesTable.conversationId] = convId
                it[MessagesTable.messageContent] = text
                it[MessagesTable.messageType] = MessageType.LLM_RECOMMENDATION
                it[MessagesTable.adminId] = admin.userId
                it[MessagesTable.parentMessageId] = parentMessageId
                it[MessagesTable.isRead] = false
                it[MessagesTable.inputMethod] = "text"
                it[MessagesTable.createdAt] = now
            }[MessagesTable.messageId]
            ConversationsTable.update({ ConversationsTable.conversationId eq convId }) {
                it[ConversationsTable.assignedAdminId] = admin.userId
                it[ConversationsTable.lastMessageAt] = now
                it[ConversationsTable.updatedAt] = now
            }
            mid
        }
        hub.publish(
            ChatEvent(
                "message.created", convId, adminOnly = true,
                buildJsonObject {
                    put("id", id.toString()); put("type", MessageType.LLM_RECOMMENDATION); put("content", text)
                    putInstant("timestamp", now)
                },
            ),
        )
        return id to now
    }

    // ------------------------------------------------------------------ resolve

    suspend fun resolve(convId: Int, admin: AdminPrincipal): JsonObject {
        val now = clock.instant()
        db.tx {
            requireConversationRow(convId)
            ConversationsTable.update({ ConversationsTable.conversationId eq convId }) {
                it[ConversationsTable.status] = ConvStatus.RESOLVED
                it[ConversationsTable.resolvedAt] = now
                it[ConversationsTable.updatedAt] = now
            }
            resolveQueueRow(convId, admin.userId, now)
        }
        publishStatus(convId, ConvStatus.RESOLVED)
        return buildJsonObject {
            put("success", true)
            put("message", "Conversation resolved successfully")
            put("conversationId", convId)
            put("resolvedBy", admin.email ?: admin.fullName ?: admin.userId.toString())
            putInstant("resolvedAt", now)
        }
    }

    // ------------------------------------------------------------------ helpers (call inside db.tx)

    private fun requireConversationRow(convId: Int) {
        ConversationsTable.selectAll().where { ConversationsTable.conversationId eq convId }.limit(1).firstOrNull()
            ?: notFound("Conversation not found")
    }

    private fun requireParentInConversation(parentId: Int?, convId: Int) {
        if (parentId == null) return
        val ok = MessagesTable.selectAll()
            .where { (MessagesTable.messageId eq parentId) and (MessagesTable.conversationId eq convId) }
            .limit(1).count() > 0
        if (!ok) badRequest("parentMessageId does not belong to this conversation")
    }

    /** Marks the queue row resolved if there is one (full_llm_bot conversations have none). */
    private fun resolveQueueRow(convId: Int, adminId: Int, now: Instant) {
        val q = AdminQueueTable.selectAll().where { AdminQueueTable.conversationId eq convId }.limit(1).firstOrNull() ?: return
        AdminQueueTable.update({ AdminQueueTable.conversationId eq convId }) {
            it[AdminQueueTable.status] = "resolved"
            it[AdminQueueTable.assignedAdminId] = q[AdminQueueTable.assignedAdminId] ?: adminId
            it[AdminQueueTable.assignedAt] = q[AdminQueueTable.assignedAt] ?: now
            it[AdminQueueTable.resolvedAt] = now
        }
    }

    private fun publishStatus(convId: Int, status: String) {
        hub.publish(ChatEvent("conversation.updated", convId, adminOnly = false, buildJsonObject { put("status", status) }))
        hub.publish(ChatEvent("queue.updated", convId, adminOnly = true, buildJsonObject { put("status", status) }))
    }

    /** QA log must reflect who actually answered (the reference left it at `llm`). Never fails the request. */
    private suspend fun qaMark(convId: Int, admin: AdminPrincipal, answer: String, usedRecommendation: Boolean) {
        try {
            qaLog.markAdminResponse(convId, admin.userId, answer, usedRecommendation)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("QA log update failed for conversation {}: {}", convId, e.toString())
        }
    }
}
