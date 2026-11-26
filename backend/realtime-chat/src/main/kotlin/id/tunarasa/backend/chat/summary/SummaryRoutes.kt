package id.tunarasa.backend.chat.summary

import id.tunarasa.backend.chat.chat.ChatService
import id.tunarasa.backend.chat.http.ChatAuth
import id.tunarasa.backend.chat.http.bool
import id.tunarasa.backend.chat.http.enumOf
import id.tunarasa.backend.chat.http.handle
import id.tunarasa.backend.chat.http.int
import id.tunarasa.backend.chat.http.notFound
import id.tunarasa.backend.chat.http.readJsonObject
import id.tunarasa.backend.chat.http.respondJson
import id.tunarasa.backend.chat.http.sessionIdOrNull
import id.tunarasa.backend.chat.http.string
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * `/api/v1/summary/...` (migrated from `summary.py`).
 *
 * - `POST /generate`        `{conversation_id, format_type?: text|json|html, include_qr?: true}` (owner session or admin)
 * - `GET  /{access_token}`  the note as a PDF (the token is the credential; expires after `ChatConfig.noteTtlDays`)
 * - `GET  /note/{access_token}` the note as JSON (reference returned hard-coded mock data)
 */
internal object SummaryRoutes {
    fun install(parent: Route, summary: SummaryService, chat: ChatService, auth: ChatAuth) {
        parent.route("/summary") {
            post("/generate") {
                call.handle {
                    val admin = auth.adminOrNull(call)
                    val body = call.readJsonObject()
                    val convId = body.int("conversation_id", "conversationId", required = true, min = 1)!!
                    // `user_id` from the reference request is accepted and ignored: identity comes from the session / token.
                    body.int("user_id", "userId")
                    val format = enumOf("format_type", body.string("format_type", "formatType"), setOf("text", "json", "html"), "text")
                    val includeQr = body.bool("include_qr", "includeQr") ?: true
                    if (!chat.canAccess(convId, admin != null, call.sessionIdOrNull())) notFound("Conversation not found")
                    call.respondJson(summary.generate(convId, format, includeQr))
                }
            }
            get("/note/{access_token}") {
                call.handle {
                    val note = summary.noteByToken(call.parameters["access_token"].orEmpty())
                        ?: notFound("Note not found or access token expired")
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respondJson(summary.noteJson(note))
                }
            }
            get("/{access_token}") {
                call.handle {
                    val note = summary.noteByToken(call.parameters["access_token"].orEmpty())
                        ?: notFound("Summary not found or access token expired")
                    val bytes = summary.pdfFor(note)
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"note_${note.noteId}.pdf\"")
                    call.respondBytes(bytes, ContentType.Application.Pdf)
                }
            }
        }
    }
}
