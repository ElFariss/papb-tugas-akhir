package id.tunarasa.backend.chat.summary

import id.tunarasa.backend.chat.ChatConfig
import id.tunarasa.backend.chat.chat.MessageType
import id.tunarasa.backend.chat.db.ConversationsTable
import id.tunarasa.backend.chat.db.MessagesTable
import id.tunarasa.backend.chat.db.NotesTable
import id.tunarasa.backend.chat.db.tx
import id.tunarasa.backend.chat.http.badRequest
import id.tunarasa.backend.chat.http.notFound
import id.tunarasa.backend.chat.http.putInstant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

internal data class NoteView(
    val noteId: Int,
    val conversationId: Int,
    val title: String?,
    val content: String,
    val accessToken: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
)

/**
 * Conversation summary + QR code + PDF note. Table `notes` (one note per conversation; regenerating issues a new token,
 * so the previous QR stops working, as in the reference).
 */
internal class SummaryService(
    private val db: Database,
    private val text: SummaryTextGenerator,
    private val qr: QrCodeGenerator,
    private val pdf: PdfSummaryGenerator,
    private val config: ChatConfig,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(SummaryService::class.java)
    private val fallback = ExtractiveSummaryTextGenerator()
    private val pdfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)

    private data class Line(val id: Int, val type: String, val content: String, val at: Instant)

    suspend fun generate(convId: Int, format: String, includeQr: Boolean): JsonObject {
        val (conv, lines) = db.tx {
            val c = ConversationsTable.selectAll().where { ConversationsTable.conversationId eq convId }.limit(1).firstOrNull()
                ?: notFound("Conversation not found")
            // Pending (undelivered) LLM recommendations are not part of what the user saw: excluded (reference included them).
            val ms = MessagesTable.selectAll()
                .where { (MessagesTable.conversationId eq convId) and (MessagesTable.messageType neq MessageType.LLM_RECOMMENDATION) }
                .orderBy(MessagesTable.createdAt, SortOrder.ASC)
                .limit(2000)
                .map { Line(it[MessagesTable.messageId], it[MessagesTable.messageType], it[MessagesTable.messageContent], it[MessagesTable.createdAt]) }
            c[ConversationsTable.createdAt] to ms
        }
        if (lines.isEmpty()) badRequest("Conversation has no messages to summarise")

        val transcript = lines.joinToString("\n") { "${roleOf(it.type)}: ${it.content}" }
        val summary = safeSummary(transcript)
        val title = safeTitle(summary)

        val now = clock.instant()
        val expires = now.plus(config.noteTtlDays, ChronoUnit.DAYS)
        val token = UUID.randomUUID().toString()
        db.tx {
            val existing = NotesTable.selectAll().where { NotesTable.conversationId eq convId }
                .orderBy(NotesTable.createdAt, SortOrder.ASC).limit(1).firstOrNull()
            if (existing != null) {
                NotesTable.update({ NotesTable.noteId eq existing[NotesTable.noteId] }) {
                    it[NotesTable.title] = title.take(255)
                    it[NotesTable.noteContent] = summary
                    it[NotesTable.urlAccess] = token
                    it[NotesTable.expiresAt] = expires
                }
            } else {
                NotesTable.insert {
                    it[NotesTable.conversationId] = convId
                    it[NotesTable.noteContent] = summary
                    it[NotesTable.title] = title.take(255)
                    it[NotesTable.urlAccess] = token
                    it[NotesTable.expiresAt] = expires
                    it[NotesTable.createdAt] = now
                }
            }
        }

        val document = when (format) {
            "json" -> jsonDocument(convId, title, summary, conv, lines)
            "html" -> htmlDocument(convId, title, summary, conv, lines.size)
            else -> textDocument(title, summary, conv, lines.size)
        }
        val downloadUrl = "${config.publicBaseUrl.trimEnd('/')}/summary/$token"

        return buildJsonObject {
            put("conversation_id", convId)
            put("summary_content", document)
            put("format_type", format)
            if (includeQr) {
                put(
                    "qr_code",
                    buildJsonObject {
                        put("qr_code_base64", qr.base64Png(downloadUrl))
                        put("access_token", token)
                        put("download_url", downloadUrl)
                        putInstant("expires_at", expires)
                        put("summary_preview", buildJsonObject { put("title", title); put("message_count", lines.size) })
                    },
                )
                put("download_url", downloadUrl)
            } else {
                put("qr_code", kotlinx.serialization.json.JsonNull)
                put("download_url", kotlinx.serialization.json.JsonNull)
            }
        }
    }

    /** Valid, unexpired note for [token], or null. Expiry is enforced here (reference: advertised, never checked). */
    suspend fun noteByToken(token: String): NoteView? {
        if (!TOKEN.matches(token)) return null
        val now = clock.instant()
        val row = db.tx {
            NotesTable.selectAll().where { NotesTable.urlAccess eq token }.limit(1).firstOrNull()
        } ?: return null
        val expires = row[NotesTable.expiresAt]
        if (expires != null && expires.isBefore(now)) return null
        return NoteView(
            row[NotesTable.noteId], row[NotesTable.conversationId], row[NotesTable.title], row[NotesTable.noteContent],
            token, row[NotesTable.createdAt], expires,
        )
    }

    fun noteJson(n: NoteView): JsonObject = buildJsonObject {
        put(
            "note",
            buildJsonObject {
                put("note_id", n.noteId); put("conversation_id", n.conversationId)
                put("title", TitleSanitizer.clean(n.title)); put("content", n.content)
                putInstant("created_at", n.createdAt)
            },
        )
        put("access_token", n.accessToken)
        putInstant("expires_at", n.expiresAt)
    }

    fun pdfFor(n: NoteView): ByteArray =
        pdf.noteToPdf(TitleSanitizer.clean(n.title), n.content, n.accessToken, pdfDate.format(n.createdAt))


    // ------------------------------------------------------------------ text generation with fallbacks

    private suspend fun safeSummary(transcript: String): String = try {
        text.summarize(transcript).trim().ifEmpty { fallback.summarize(transcript) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        log.warn("SummaryTextGenerator.summarize failed, using extractive fallback: {}", e.toString())
        fallback.summarize(transcript)
    } catch (e: NotImplementedError) {
        fallback.summarize(transcript)
    }

    private suspend fun safeTitle(summary: String): String = try {
        TitleSanitizer.clean(text.title(summary))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        log.warn("SummaryTextGenerator.title failed: {}", e.toString())
        DEFAULT_TITLE
    } catch (e: NotImplementedError) {
        DEFAULT_TITLE
    }

    private fun roleOf(type: String) = when (type) {
        MessageType.USER -> "user"
        MessageType.ADMIN -> "admin"
        else -> "bot"
    }

    // ------------------------------------------------------------------ documents (reference: create_summary_document)

    private fun textDocument(title: String, summary: String, created: Instant, count: Int): String = """
RINGKASAN PERCAKAPAN TUNARASA
=============================

Judul: $title
Tanggal: $created
Total Pesan: $count

$summary

---
Dibuat oleh Tunarasa - Platform Bahasa Isyarat Indonesia
https://tunarasa.my.id
""".trimStart('\n')

    private fun jsonDocument(convId: Int, title: String, summary: String, created: Instant, lines: List<Line>): String =
        buildJsonObject {
            put("conversation_id", convId)
            put("title", title)
            putInstant("created_at", created)
            put("summary", summary)
            put("message_count", lines.size)
            put(
                "messages",
                JsonArray(
                    lines.map {
                        buildJsonObject {
                            put("message_id", it.id)
                            put("sender_type", if (it.type == MessageType.USER) "user" else "ai")
                            put("content", it.content)
                            putInstant("created_at", it.at)
                        }
                    },
                ),
            )
            put("platform", buildJsonObject { put("name", "Tunarasa"); put("url", "https://tunarasa.my.id") })
        }.toString()

    /** All interpolated values are HTML-escaped (the reference interpolated raw text). */
    private fun htmlDocument(convId: Int, title: String, summary: String, created: Instant, count: Int): String = """
<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="utf-8">
<title>${esc(title)}</title>
<style>body{font-family:Arial,sans-serif;max-width:720px;margin:2rem auto;line-height:1.6}h1{font-size:1.4rem}.meta{color:#666;font-size:.9rem}</style>
</head>
<body>
<h1>Ringkasan Percakapan Tunarasa</h1>
<h2>${esc(title)}</h2>
<p class="meta">Percakapan #$convId &middot; ${esc(created.toString())} &middot; $count pesan</p>
<div>${esc(summary).replace("\n", "<br>")}</div>
<hr>
<p class="meta">Dibuat oleh Tunarasa - Platform Bahasa Isyarat Indonesia &middot; <a href="https://tunarasa.my.id">https://tunarasa.my.id</a></p>
</body>
</html>
""".trimStart('\n')

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    companion object {
        private val TOKEN = Regex("^[0-9a-fA-F-]{36}$")
    }
}
