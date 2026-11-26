package id.tunarasa.app.data

import android.net.Uri
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class PublicChatApi(private val client: ApiClient) {
    suspend fun ask(question: String, sessionId: String, slug: String, inputMethod: String): ApiResult<ChatAnswer> =
        client.safeRequest {
            post("/api/v1/rag/ask") {
                contentType(ContentType.Application.Json)
                setBody(AskRequest(question, sessionId, slug, inputMethod))
            }
        }

    suspend fun submitForValidation(
        question: String,
        sessionId: String,
        institutionId: Int,
        slug: String,
        inputMethod: String,
    ): ApiResult<SavedConversation> = client.safeRequest {
        post("/api/v1/chat/send-message") {
            contentType(ContentType.Application.Json)
            setBody(ValidationRequest(question, sessionId, institutionId, slug, inputMethod))
        }
    }

    suspend fun getMessages(conversationId: Int, sessionId: String): ApiResult<ConversationMessages> =
        client.safeRequest {
            get("/api/v1/chat/conversation/$conversationId/messages") { header("X-Session-Id", sessionId) }
        }

    suspend fun saveConversation(sessionId: String, institutionId: Int, messages: List<ChatEntry>): ApiResult<SavedConversation> =
        client.safeRequest {
            post("/api/v1/chat/save-conversation") {
                contentType(ContentType.Application.Json)
                setBody(SaveChatRequest(sessionId, institutionId, messages))
            }
        }

    suspend fun generateSummary(conversationId: Int, sessionId: String): ApiResult<GeneratedSummary> =
        client.safeRequest {
            post("/api/v1/summary/generate") {
                header("X-Session-Id", sessionId)
                contentType(ContentType.Application.Json)
                setBody(SummaryRequest(conversationId))
            }
        }

    suspend fun getSummary(token: String): ApiResult<NoteResponse> =
        client.safeRequest { get("/api/v1/summary/note/${Uri.encode(token)}") }
}

@Serializable
private data class AskRequest(
    val question: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("institution_slug") val institutionSlug: String,
    @SerialName("input_source") val inputSource: String,
)

@Serializable
private data class ValidationRequest(
    val message: String,
    val sessionId: String,
    val institutionId: Int,
    val institutionSlug: String,
    val inputMethod: String,
    val serviceMode: String = "bot_with_admin_validation",
)

@Serializable
data class ConversationMessages(val status: String, val messages: List<ConversationMessage>)

@Serializable
data class ConversationMessage(
    val id: String,
    val type: String,
    val content: String,
    val inputMethod: String? = null,
    val adminName: String? = null,
    val timestamp: String,
    val confidence: Int? = null,
)

@Serializable
data class ChatAnswer(
    val answer: String,
    val sources: List<ChatSource> = emptyList(),
    @SerialName("follow_up_suggestions") val followUpSuggestions: List<String> = emptyList(),
)

@Serializable
data class ChatSource(@SerialName("document_id") val documentId: String)

@Serializable
data class ChatEntry(val content: String, val type: String, val inputMethod: String)

@Serializable
private data class SaveChatRequest(
    val sessionId: String,
    val institutionId: Int,
    val messages: List<ChatEntry>,
)

@Serializable
data class SavedConversation(val conversationId: Int)

@Serializable
private data class SummaryRequest(val conversationId: Int)

@Serializable
data class GeneratedSummary(@SerialName("qr_code") val qrCode: SummaryQr)

@Serializable
data class SummaryQr(@SerialName("access_token") val accessToken: String)

@Serializable
data class NoteResponse(
    val note: NoteData,
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class NoteData(
    @SerialName("note_id") val noteId: Int,
    @SerialName("conversation_id") val conversationId: Int,
    val content: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("qr_generated_at") val qrGeneratedAt: String = "",
)
