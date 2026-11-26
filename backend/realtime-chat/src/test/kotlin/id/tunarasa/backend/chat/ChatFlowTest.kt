package id.tunarasa.backend.chat

import id.tunarasa.backend.chat.db.ChatTables
import id.tunarasa.contracts.Answer
import id.tunarasa.contracts.Evaluation
import id.tunarasa.contracts.FaqCluster
import id.tunarasa.contracts.IngestionResult
import id.tunarasa.contracts.RagLlmService
import id.tunarasa.contracts.RagProcessingStatus
import id.tunarasa.contracts.Source
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** Stand-in for AI-2. `askGesture` throws like the real stub (`TODO()`). */
private class FakeRag : RagLlmService {
    override suspend fun ingestDocument(ragFileId: Int, institutionSlug: String) =
        IngestionResult(ragFileId, RagProcessingStatus.COMPLETED, 0)

    override suspend fun askRag(question: String, sessionId: String, institutionSlug: String?) =
        Answer("Jawaban uji untuk: $question", 0.8, listOf(Source("doc1", "kutipan", 0.9)))

    override suspend fun askGesture(gestureText: String, sessionId: String?, language: String, gestureConfidence: Float?): Answer = TODO()
    override suspend fun clusterFaqs(institutionId: Int) = listOf(FaqCluster("Cara buat KTP", listOf("a", "b"), 0.5))
    override suspend fun evaluateResponse(qaId: Int) = Evaluation(qaId, 88, "bagus")
}

private val admins = mapOf(
    "admin1" to AdminPrincipal(7, "a1@x.id", "Admin Satu", "admin"),
    "admin2" to AdminPrincipal(8, "a2@x.id", "Admin Dua", "admin"),
    "plain" to AdminPrincipal(9, "u@x.id", "User", "user"),
)

private val json = Json { ignoreUnknownKeys = true }
private suspend fun HttpResponse.obj(): JsonObject = json.parseToJsonElement(bodyAsText()).jsonObject

class ChatFlowTest {
    private fun deps(): ChatDependencies {
        val db = Database.connect("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
        transaction(db) { SchemaUtils.create(*ChatTables.all) }
        return ChatDependencies(db = db, ragLlm = FakeRag(), adminAuth = { t -> admins[t] })
    }

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { chatModule(deps()) }
        block()
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, token: String? = null, session: String? = null) =
        client.post(path) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
            if (session != null) header("X-Session-Id", session)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.getWith(path: String, token: String? = null, session: String? = null) =
        client.get(path) {
            if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
            if (session != null) header("X-Session-Id", session)
        }

    private suspend fun ApplicationTestBuilder.startAdminConversation(session: String = "sess_1"): Int {
        val r = postJson(
            "/api/v1/chat/send-message",
            """{"message":"Bagaimana cara membuat KTP?","sessionId":"$session","serviceMode":"bot_with_admin_validation"}""",
        )
        assertEquals(HttpStatusCode.OK, r.status)
        return r.obj()["conversationId"]!!.jsonPrimitive.int
    }

    @Test fun `admin validation flow end to end`() = app {
        val id = startAdminConversation()

        // anonymous caller without / with the wrong session cannot read it (404, not 403)
        assertEquals(HttpStatusCode.NotFound, getWith("/api/v1/chat/conversation/$id/messages").status)
        assertEquals(HttpStatusCode.NotFound, getWith("/api/v1/chat/conversation/$id/messages", session = "other").status)

        // owner sees only the user message; the pending LLM recommendation is hidden
        val own = getWith("/api/v1/chat/conversation/$id/messages", session = "sess_1").obj()
        val ownTypes = own["messages"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(listOf("user"), ownTypes)

        // admin sees both
        val adm = getWith("/api/v1/chat/conversation/$id/messages", token = "admin1").obj()
        val admTypes = adm["messages"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(listOf("user", "llm_recommendation"), admTypes)

        // admin endpoints: 401 without token, 403 for a non-admin
        assertEquals(HttpStatusCode.Unauthorized, getWith("/api/v1/chat/admin/conversations").status)
        assertEquals(HttpStatusCode.Forbidden, getWith("/api/v1/chat/admin/conversations", token = "plain").status)

        val list = getWith("/api/v1/chat/admin/conversations", token = "admin1").obj()
        assertEquals(1, list["conversations"]!!.jsonArray.size)
        assertEquals(1, list["stats"]!!.jsonObject["waitingInQueue"]!!.jsonPrimitive.int)

        // claim: admin1 wins, admin2 gets 409
        assertEquals(HttpStatusCode.OK, postJson("/api/v1/chat/admin/conversations/$id/assign", "{}", token = "admin1").status)
        assertEquals(HttpStatusCode.Conflict, postJson("/api/v1/chat/admin/conversations/$id/assign", "{}", token = "admin2").status)

        // approve
        val resp = postJson(
            "/api/v1/chat/admin/respond",
            """{"conversationId":"$id","response":"Silakan ke kelurahan.","responseType":"approved_recommendation"}""",
            token = "admin1",
        )
        assertEquals(HttpStatusCode.OK, resp.status)

        val after = getWith("/api/v1/chat/conversation/$id/messages", session = "sess_1").obj()
        assertEquals("resolved", after["status"]!!.jsonPrimitive.content)
        val last = after["messages"]!!.jsonArray.last().jsonObject
        assertEquals("admin", last["type"]!!.jsonPrimitive.content)
        assertEquals("Silakan ke kelurahan.", last["content"]!!.jsonPrimitive.content)

        // QA log was flipped to admin and is only readable by admins
        assertEquals(HttpStatusCode.Unauthorized, getWith("/api/v1/qa-log/admin").status)
        val qa = getWith("/api/v1/qa-log/admin", token = "admin1").obj()["data"]!!.jsonObject["qaLogs"]!!.jsonArray
        assertEquals(1, qa.size)
        assertEquals("admin", qa[0].jsonObject["respondedBy"]!!.jsonPrimitive.content)
        assertTrue(qa[0].jsonObject["llmRecommendationUsed"]!!.jsonPrimitive.content.toBoolean())

        // a new user message re-opens the conversation
        val again = startAdminConversation()
        assertEquals(id, again)
        val reopened = getWith("/api/v1/chat/conversation/$id/messages", session = "sess_1").obj()
        assertEquals("waiting", reopened["status"]!!.jsonPrimitive.content)
    }

    @Test fun `send-message validates input`() = app {
        assertEquals(
            HttpStatusCode.BadRequest,
            postJson("/api/v1/chat/send-message", """{"message":"x","sessionId":"s","serviceMode":"full_llm_bot"}""").status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            postJson("/api/v1/chat/send-message", """{"message":"","sessionId":"s","serviceMode":"bot_with_admin_validation"}""").status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            postJson("/api/v1/chat/send-message", """{"message":"x","sessionId":"bad id!","serviceMode":"bot_with_admin_validation"}""").status,
        )
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/v1/chat/send-message", "not json").status)
    }

    @Test fun `save-conversation maps types, rejects anonymous admin messages`() = app {
        val ok = postJson(
            "/api/v1/chat/save-conversation",
            """{"session_id":"sess_9","service_mode":"full_llm_bot","messages":[
                {"message_content":"halo","message_type":"user","created_at":"2025-01-01T00:00:00Z"},
                {"message_content":"hai","message_type":"assistant","confidence":0.5,"created_at":"bukan-tanggal"}]}""",
        )
        assertEquals(HttpStatusCode.OK, ok.status)
        val cid = ok.obj()["conversationId"]!!.jsonPrimitive.int
        val conv = getWith("/api/v1/conversation/$cid", session = "sess_9").obj()["data"]!!.jsonObject
        val types = conv["messages"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(listOf("user", "llm_bot"), types)
        assertEquals(50, conv["messages"]!!.jsonArray[1].jsonObject["confidence"]!!.jsonPrimitive.int)

        val spoof = postJson(
            "/api/v1/chat/save-conversation",
            """{"session_id":"sess_9","messages":[{"message_content":"x","message_type":"admin","admin_id":1,"created_at":"2025-01-01T00:00:00Z"}]}""",
        )
        assertEquals(HttpStatusCode.Forbidden, spoof.status)
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/v1/chat/save-conversation", """{"session_id":"s","messages":[]}""").status)
    }

    @Test fun `qa log writes need the owning session`() = app {
        val id = startAdminConversation("sess_q")
        val body = """{"conversation_id":$id,"question":"q","answer":"a","responded_by":"admin","admin_id":1,"evaluation_score":99}"""
        assertEquals(HttpStatusCode.NotFound, postJson("/api/v1/qa-log/log", body).status)
        assertEquals(HttpStatusCode.NotFound, postJson("/api/v1/qa/log", body, session = "intruder").status)
        val ok = postJson("/api/v1/qa-log/log", body, session = "sess_q")
        assertEquals(HttpStatusCode.OK, ok.status)
        // privileged fields from an anonymous caller are ignored
        val rows = getWith("/api/v1/qa/admin?respondedBy=admin", token = "admin1").obj()["data"]!!.jsonObject["qaLogs"]!!.jsonArray
        assertEquals(0, rows.size)

        val all = getWith("/api/v1/qa/admin", token = "admin1").obj()["data"]!!.jsonObject["qaLogs"]!!.jsonArray
        val mine = all.map { it.jsonObject }.first { it["question"]!!.jsonPrimitive.content == "q" }
        assertEquals("llm", mine["respondedBy"]!!.jsonPrimitive.content)
        assertTrue(mine["evaluationScore"] is kotlinx.serialization.json.JsonNull)

        // evaluation: admin only, delegated to AI-2 and persisted
        val qaId = mine["id"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.Unauthorized, postJson("/api/v1/qa/$qaId/evaluate", "{}").status)
        val ev = postJson("/api/v1/qa/$qaId/evaluate", "{}", token = "admin1")
        assertEquals(HttpStatusCode.OK, ev.status)
        assertEquals(88, ev.obj()["data"]!!.jsonObject["score"]!!.jsonPrimitive.int)
    }

    @Test fun `summary generate, download as pdf, regenerate invalidates the old token`() = app {
        val id = startAdminConversation("sess_s")
        assertEquals(HttpStatusCode.NotFound, postJson("/api/v1/summary/generate", """{"conversation_id":$id}""").status)

        val g1 = postJson("/api/v1/summary/generate", """{"conversation_id":$id,"format_type":"html"}""", session = "sess_s")
        assertEquals(HttpStatusCode.OK, g1.status)
        val o1 = g1.obj()
        assertTrue(o1["qr_code"]!!.jsonObject["qr_code_base64"]!!.jsonPrimitive.content.length > 100)
        assertFalse(o1["summary_content"]!!.jsonPrimitive.content.contains("<script"))
        val t1 = o1["qr_code"]!!.jsonObject["access_token"]!!.jsonPrimitive.content

        val pdf = client.get("/api/v1/summary/$t1")
        assertEquals(HttpStatusCode.OK, pdf.status)
        assertEquals("%PDF", String(pdf.bodyAsBytes(), 0, 4, Charsets.US_ASCII))
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/summary/note/$t1").status)

        val t2 = postJson("/api/v1/summary/generate", """{"conversation_id":$id,"include_qr":false}""", session = "sess_s")
            .obj().let { it["qr_code"] }
        assertTrue(t2 is kotlinx.serialization.json.JsonNull)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/summary/$t1").status) // token rotated
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/summary/not-a-token").status)
    }

    @Test fun `public sessions and AI gateway`() = app {
        assertEquals(HttpStatusCode.OK, postJson("/api/v1/public-session/start-session/guest_1", "").status)
        val n = postJson("/api/v1/public-session/track-sessions", """{"session_ids":["guest_2","guest_3"]}""").obj()
        assertEquals(3, n["active_sessions"]!!.jsonPrimitive.int) // track only adds, never evicts
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/v1/public-session/track-sessions", """{"session_ids":["bad id"]}""").status)
        assertEquals(HttpStatusCode.Unauthorized, getWith("/api/v1/session/metrics").status)
        assertEquals(HttpStatusCode.OK, getWith("/api/v1/session/metrics", token = "admin1").status)

        val rag = postJson("/api/v1/rag/ask", """{"question":"cara buat KTP","session_id":"sess_r"}""").obj()
        assertEquals(0.8, rag["confidence"]!!.jsonPrimitive.content.toDouble(), 0.0001)
        assertEquals(1, (rag["sources"] as JsonArray).size)

        val gesture = postJson("/api/v1/gesture/ask", """{"text":"halo"}""")
        assertEquals(HttpStatusCode.ServiceUnavailable, gesture.status) // AI-2 stub still TODO()
        assertEquals("NOT_AVAILABLE", gesture.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)

        val faq = getWith("/api/v1/faq/recommendations/1").obj()
        assertEquals(1, faq["cluster_count"]!!.jsonPrimitive.int)
        assertEquals(HttpStatusCode.Unauthorized, postJson("/api/v1/faq/refresh/1", "{}").status)
    }
}
