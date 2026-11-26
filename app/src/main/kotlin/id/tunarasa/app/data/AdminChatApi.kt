package id.tunarasa.app.data

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class AdminChatApi(private val client: ApiClient) {
    suspend fun getQueue(): ApiResult<AdminQueueResponse> = client.safeRequest {
        get("/api/v1/chat/admin/conversations") {
            with(client) { attachBearerToken() }
            parameter("serviceMode", "bot_with_admin_validation")
        }
    }

    suspend fun getMessages(conversationId: String): ApiResult<ConversationMessages> = client.safeRequest {
        get("/api/v1/chat/conversation/$conversationId/messages") { with(client) { attachBearerToken() } }
    }

    suspend fun getQaLogs(
        dateFrom: String,
        search: String,
        respondedBy: String?,
        serviceMode: String?,
        page: Int,
    ): ApiResult<AdminQaResponse> = client.safeRequest {
        get("/api/v1/qa/admin") {
            with(client) { attachBearerToken() }
            parameter("dateFrom", dateFrom)
            parameter("search", search)
            parameter("respondedBy", respondedBy)
            parameter("serviceMode", serviceMode)
            parameter("page", page)
        }
    }

    suspend fun assign(conversationId: String): ApiResult<ChatActionResponse> =
        postAction("/api/v1/chat/admin/conversations/$conversationId/assign")

    suspend fun resolve(conversationId: String): ApiResult<ChatActionResponse> =
        postAction("/api/v1/chat/conversation/$conversationId/resolve")

    suspend fun respond(conversationId: String, response: String, approved: Boolean): ApiResult<ChatActionResponse> =
        client.safeRequest {
            post("/api/v1/chat/admin/respond") {
                with(client) { attachBearerToken() }
                contentType(ContentType.Application.Json)
                setBody(AdminReply(conversationId, response, if (approved) "approved_recommendation" else "custom"))
            }
        }

    private suspend fun postAction(path: String): ApiResult<ChatActionResponse> = client.safeRequest {
        post(path) { with(client) { attachBearerToken() } }
    }
}

@Serializable
data class ChatActionResponse(val success: Boolean)

@Serializable
private data class AdminReply(val conversationId: String, val response: String, val responseType: String)

@Serializable
data class AdminQueueResponse(val conversations: List<AdminQueueConversation>)

@Serializable
data class AdminQueueConversation(
    val id: String,
    val sessionId: String,
    val userName: String,
    val institutionName: String? = null,
    val status: String,
    val priority: String,
    val lastMessage: String,
    val waitTime: Int,
)

@Serializable
data class AdminQaResponse(val data: AdminQaPage)

@Serializable
data class AdminQaPage(val qaLogs: List<AdminQaLog>, val pagination: AdminQaPagination)

@Serializable
data class AdminQaPagination(val hasNextPage: Boolean)

@Serializable
data class AdminQaLog(
    val id: String,
    val question: String,
    val answer: String,
    val confidence: Int? = null,
    val responseTime: Int? = null,
    val serviceMode: String,
    val respondedBy: String,
    val createdAt: String,
    val evaluationScore: Int? = null,
    val gestureInput: String? = null,
    val institutionName: String? = null,
    val inputMethod: String? = null,
    val conversation: AdminQaConversation,
)

@Serializable
data class AdminQaConversation(val sessionId: String? = null, val status: String? = null)
