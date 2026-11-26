package id.tunarasa.backend.chat.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Owner: BE-2. Mirrors `backend/app/db/models.py` and `frontend/src/lib/db/schema.ts`.
 *
 * Foreign keys are NOT declared here; they live in `db/migration/V20x__*.sql`. This keeps the
 * module testable on H2 without BE-1's `users` / `institutions` tables.
 * Every timestamp is written explicitly by the services (no reliance on DB defaults).
 */
internal object SessionsTable : Table("sessions") {
    val sessionId = integer("session_id").autoIncrement()
    val anonymousSessionId = varchar("anonymous_session_id", 255).nullable()
    val userId = integer("user_id").nullable()
    val sessionType = varchar("session_type", 20)
    val sessionStart = timestamp("session_start")
    val sessionEnd = timestamp("session_end").nullable()
    val isActive = bool("is_active")
    val userAgent = text("user_agent").nullable()
    val ipAddress = varchar("ip_address", 45).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(sessionId)
}

internal object ConversationsTable : Table("conversations") {
    val conversationId = integer("conversation_id").autoIncrement()
    val isActive = bool("is_active")
    val sessionId = varchar("session_id", 255)
    val userAgent = text("user_agent").nullable()
    val ipAddress = varchar("ip_address", 45).nullable()
    val serviceMode = varchar("service_mode", 30)
    val assignedAdminId = integer("assigned_admin_id").nullable()
    val institutionId = integer("institution_id").nullable()
    val status = varchar("status", 20)
    val priority = varchar("priority", 10)
    val lastMessageAt = timestamp("last_message_at").nullable()
    val resolvedAt = timestamp("resolved_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(conversationId)
}

internal object MessagesTable : Table("messages") {
    val messageId = integer("message_id").autoIncrement()
    val conversationId = integer("conversation_id")
    val messageContent = text("message_content")
    val messageType = varchar("message_type", 20)
    val adminId = integer("admin_id").nullable()
    val parentMessageId = integer("parent_message_id").nullable()
    val confidence = integer("confidence").nullable()
    val isRead = bool("is_read")
    val inputMethod = varchar("input_method", 20).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(messageId)
}

internal object AdminQueueTable : Table("admin_queue") {
    val queueId = integer("queue_id").autoIncrement()
    val conversationId = integer("conversation_id")
    val assignedAdminId = integer("assigned_admin_id").nullable()
    val priority = varchar("priority", 10)
    val status = varchar("status", 20)
    val queuedAt = timestamp("queued_at")
    val assignedAt = timestamp("assigned_at").nullable()
    val resolvedAt = timestamp("resolved_at").nullable()
    override val primaryKey = PrimaryKey(queueId)
}

internal object QaLogsTable : Table("qa_logs") {
    val qaId = integer("qa_id").autoIncrement()
    val conversationId = integer("conversation_id")
    val institutionId = integer("institution_id")
    val question = text("question")
    val answer = text("answer")
    val confidence = integer("confidence").nullable()
    val responseTime = integer("response_time").nullable()
    val gestureInput = text("gesture_input").nullable()
    val contextUsed = text("context_used").nullable()
    val evaluationScore = integer("evaluation_score").nullable()
    val serviceMode = varchar("service_mode", 30)
    val respondedBy = varchar("responded_by", 20)
    val adminId = integer("admin_id").nullable()
    val llmRecommendationUsed = bool("llm_recommendation_used").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(qaId)
}

internal object NotesTable : Table("notes") {
    val noteId = integer("note_id").autoIncrement()
    val conversationId = integer("conversation_id")
    val noteContent = text("note_content")
    val title = varchar("title", 255).nullable()
    val urlAccess = varchar("url_access", 255).nullable()
    val expiresAt = timestamp("expires_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(noteId)
}

/** All BE-2 tables, in dependency order. Used by tests (`SchemaUtils.create(*ChatTables.all)`). */
internal object ChatTables {
    val all: Array<Table> = arrayOf(
        SessionsTable, ConversationsTable, MessagesTable, AdminQueueTable, QaLogsTable, NotesTable,
    )
}

/** Runs [block] in a blocking JDBC transaction off the caller's dispatcher. */
internal suspend fun <T> Database.tx(block: () -> T): T =
    withContext(Dispatchers.IO) { transaction(this@tx) { block() } }
