package id.tunarasa.backend.chat.qa

import id.tunarasa.backend.chat.chat.MessageType
import id.tunarasa.backend.chat.db.ConversationsTable
import id.tunarasa.backend.chat.db.MessagesTable
import id.tunarasa.backend.chat.db.QaLogsTable
import id.tunarasa.backend.chat.db.tx
import id.tunarasa.backend.chat.http.putInstant
import id.tunarasa.contracts.ServiceMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Instant

internal data class QaEntry(
    val conversationId: Int,
    val institutionId: Int,
    val question: String,
    val answer: String,
    val confidence: Int? = null,
    val responseTime: Int? = null,
    val gestureInput: String? = null,
    /** JSON array of the RAG sources that grounded the answer (AI-2 contract: persist question and sources). */
    val contextUsed: String? = null,
    val evaluationScore: Int? = null,
    val serviceMode: String = ServiceMode.FULL_LLM_BOT.wire,
    val respondedBy: String = "llm",
    val adminId: Int? = null,
    val llmRecommendationUsed: Boolean = false,
)

internal data class QaFilters(
    val page: Int,
    val limit: Int,
    val serviceMode: String?,
    val respondedBy: String?,
    val search: String?,
    val dateFrom: Instant?,
    val dateTo: Instant?,
    val minConfidence: Int?,
    val maxConfidence: Int?,
)

internal data class QaRow(val qaId: Int, val conversationId: Int, val institutionId: Int, val question: String, val answer: String)

/** Q&A interaction logging. Table `qa_logs` is also read directly by AI-2 for FAQ clustering (`institution_id`, `question`). */
internal class QaLogService(private val db: Database, private val clock: Clock) {

    suspend fun log(e: QaEntry): Int = db.tx {
        QaLogsTable.insert {
            it[QaLogsTable.conversationId] = e.conversationId
            it[QaLogsTable.institutionId] = e.institutionId
            it[QaLogsTable.question] = e.question
            it[QaLogsTable.answer] = e.answer
            it[QaLogsTable.confidence] = e.confidence?.coerceIn(0, 100)
            it[QaLogsTable.responseTime] = e.responseTime
            it[QaLogsTable.gestureInput] = e.gestureInput
            it[QaLogsTable.contextUsed] = e.contextUsed
            it[QaLogsTable.evaluationScore] = e.evaluationScore?.coerceIn(0, 100)
            it[QaLogsTable.serviceMode] = e.serviceMode
            it[QaLogsTable.respondedBy] = e.respondedBy
            it[QaLogsTable.adminId] = e.adminId
            it[QaLogsTable.llmRecommendationUsed] = e.llmRecommendationUsed
            it[QaLogsTable.createdAt] = clock.instant()
        }[QaLogsTable.qaId]
    }

    /**
     * Admin validation finished: flip the pending `llm` row to `admin` (reference comment: "will be updated when
     * admin approves", but it never was). If there is no pending row, record a new one from the last user question.
     */
    suspend fun markAdminResponse(convId: Int, adminId: Int, answer: String, usedRecommendation: Boolean) {
        db.tx {
            val pending = QaLogsTable.selectAll().where {
                (QaLogsTable.conversationId eq convId) and
                    (QaLogsTable.serviceMode eq ServiceMode.BOT_WITH_ADMIN_VALIDATION.wire) and
                    (QaLogsTable.respondedBy eq "llm")
            }.orderBy(QaLogsTable.createdAt, SortOrder.DESC).limit(1).firstOrNull()

            if (pending != null) {
                QaLogsTable.update({ QaLogsTable.qaId eq pending[QaLogsTable.qaId] }) {
                    it[QaLogsTable.respondedBy] = "admin"
                    it[QaLogsTable.adminId] = adminId
                    it[QaLogsTable.answer] = answer
                    it[QaLogsTable.llmRecommendationUsed] = usedRecommendation
                }
                return@tx
            }
            val conv = ConversationsTable.selectAll().where { ConversationsTable.conversationId eq convId }.limit(1).firstOrNull() ?: return@tx
            val question = MessagesTable.selectAll().where {
                (MessagesTable.conversationId eq convId) and (MessagesTable.messageType eq MessageType.USER)
            }.orderBy(MessagesTable.createdAt, SortOrder.DESC).limit(1).firstOrNull()?.get(MessagesTable.messageContent) ?: return@tx
            QaLogsTable.insert {
                it[QaLogsTable.conversationId] = convId
                it[QaLogsTable.institutionId] = conv[ConversationsTable.institutionId] ?: 1
                it[QaLogsTable.question] = question
                it[QaLogsTable.answer] = answer
                it[QaLogsTable.serviceMode] = conv[ConversationsTable.serviceMode]
                it[QaLogsTable.respondedBy] = "admin"
                it[QaLogsTable.adminId] = adminId
                it[QaLogsTable.llmRecommendationUsed] = usedRecommendation
                it[QaLogsTable.createdAt] = clock.instant()
            }
        }
    }

    suspend fun get(qaId: Int): QaRow? = db.tx {
        QaLogsTable.selectAll().where { QaLogsTable.qaId eq qaId }.limit(1).firstOrNull()?.let {
            QaRow(
                it[QaLogsTable.qaId], it[QaLogsTable.conversationId], it[QaLogsTable.institutionId],
                it[QaLogsTable.question], it[QaLogsTable.answer],
            )
        }
    }

    /** Latest logged answer for [question] (FAQ cards show a representative answer). */
    suspend fun latestAnswer(institutionId: Int, question: String): String? = db.tx {
        QaLogsTable.selectAll()
            .where { (QaLogsTable.institutionId eq institutionId) and (QaLogsTable.question eq question) }
            .orderBy(QaLogsTable.createdAt, SortOrder.DESC).limit(1).firstOrNull()?.get(QaLogsTable.answer)
    }

    suspend fun setEvaluation(qaId: Int, score: Int) {
        db.tx {
            QaLogsTable.update({ QaLogsTable.qaId eq qaId }) { it[QaLogsTable.evaluationScore] = score.coerceIn(0, 100) }
        }
    }

    /** Reference `GET /qa-log/institution/{id}` (consumed by FAQ clustering). */
    suspend fun listForInstitution(institutionId: Int, limit: Int, offset: Int, minConfidence: Int?): JsonArray {
        val rows = db.tx {
            var w: Op<Boolean> = QaLogsTable.institutionId eq institutionId
            if (minConfidence != null) w = w and (QaLogsTable.confidence greaterEq minConfidence)
            QaLogsTable.selectAll().where { w }
                .orderBy(QaLogsTable.createdAt, SortOrder.DESC)
                .limit(limit).offset(offset.toLong())
                .toList()
        }
        return JsonArray(
            rows.map {
                buildJsonObject {
                    put("qa_id", it[QaLogsTable.qaId]); put("question", it[QaLogsTable.question])
                    put("answer", it[QaLogsTable.answer]); put("confidence", it[QaLogsTable.confidence])
                    put("response_time", it[QaLogsTable.responseTime]); put("service_mode", it[QaLogsTable.serviceMode])
                    put("responded_by", it[QaLogsTable.respondedBy]); putInstant("created_at", it[QaLogsTable.createdAt])
                    put("evaluation_score", it[QaLogsTable.evaluationScore])
                }
            },
        )
    }

    /** Reference frontend `GET /api/admin/qa-logs`. Admin names are not resolved here (BE-1 owns `users`). */
    suspend fun adminPage(f: QaFilters): JsonObject {
        val (total, rows, convs) = db.tx {
            var w: Op<Boolean> = Op.TRUE
            if (f.serviceMode != null) w = w and (QaLogsTable.serviceMode eq f.serviceMode)
            if (f.respondedBy != null) w = w and (QaLogsTable.respondedBy eq f.respondedBy)
            if (f.search != null) {
                // Strip LIKE wildcards from user input; the value itself is bound as a parameter.
                val term = "%" + f.search.lowercase().replace(Regex("[%_\\\\]"), "") + "%"
                w = w and ((QaLogsTable.question.lowerCase() like term) or (QaLogsTable.answer.lowerCase() like term))
            }
            if (f.dateFrom != null) w = w and (QaLogsTable.createdAt greaterEq f.dateFrom)
            if (f.dateTo != null) w = w and (QaLogsTable.createdAt lessEq f.dateTo)
            if (f.minConfidence != null) w = w and (QaLogsTable.confidence greaterEq f.minConfidence)
            if (f.maxConfidence != null) w = w and (QaLogsTable.confidence lessEq f.maxConfidence)

            val count = QaLogsTable.selectAll().where { w }.count()
            val page = QaLogsTable.selectAll().where { w }
                .orderBy(QaLogsTable.createdAt, SortOrder.DESC)
                .limit(f.limit).offset(((f.page - 1).toLong()) * f.limit)
                .toList()
            val convIds = page.map { it[QaLogsTable.conversationId] }.distinct()
            val convMap: Map<Int, ResultRow> = if (convIds.isEmpty()) emptyMap() else
                ConversationsTable.selectAll().where { ConversationsTable.conversationId inList convIds }
                    .associateBy { it[ConversationsTable.conversationId] }
            Triple(count, page, convMap)
        }

        val items = rows.map { r ->
            val c = convs[r[QaLogsTable.conversationId]]
            buildJsonObject {
                put("id", r[QaLogsTable.qaId].toString())
                put("conversationId", r[QaLogsTable.conversationId])
                put("question", r[QaLogsTable.question]); put("answer", r[QaLogsTable.answer])
                put("confidence", r[QaLogsTable.confidence]); put("responseTime", r[QaLogsTable.responseTime])
                put("gestureInput", r[QaLogsTable.gestureInput]); put("contextUsed", r[QaLogsTable.contextUsed])
                put("evaluationScore", r[QaLogsTable.evaluationScore])
                put("serviceMode", r[QaLogsTable.serviceMode]); put("respondedBy", r[QaLogsTable.respondedBy])
                put("llmRecommendationUsed", r[QaLogsTable.llmRecommendationUsed])
                putInstant("createdAt", r[QaLogsTable.createdAt])
                put(
                    "conversation",
                    buildJsonObject {
                        put("sessionId", c?.get(ConversationsTable.sessionId))
                        put("status", c?.get(ConversationsTable.status))
                        put("serviceMode", c?.get(ConversationsTable.serviceMode))
                    },
                )
                put("admin", r[QaLogsTable.adminId]?.let { id -> buildJsonObject { put("id", id) } } ?: kotlinx.serialization.json.JsonNull)
            }
        }
        val conf = rows.mapNotNull { it[QaLogsTable.confidence] }
        val rt = rows.mapNotNull { it[QaLogsTable.responseTime] }
        val totalPages = ((total + f.limit - 1) / f.limit).toInt()
        return buildJsonObject {
            put("success", true)
            put(
                "data",
                buildJsonObject {
                    put("qaLogs", JsonArray(items))
                    put(
                        "pagination",
                        buildJsonObject {
                            put("page", f.page); put("limit", f.limit); put("total", total)
                            put("totalPages", totalPages); put("hasNextPage", f.page < totalPages); put("hasPrevPage", f.page > 1)
                        },
                    )
                    put(
                        "statistics",
                        buildJsonObject {
                            put("totalLogs", total)
                            put("averageConfidence", if (conf.isEmpty()) 0L else Math.round(conf.average()))
                            put("averageResponseTime", if (rt.isEmpty()) 0L else Math.round(rt.average()))
                            put("llmResponses", rows.count { it[QaLogsTable.respondedBy] == "llm" })
                            put("adminResponses", rows.count { it[QaLogsTable.respondedBy] == "admin" })
                        },
                    )
                },
            )
        }
    }
}
