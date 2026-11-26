package id.tunarasa.backend.chat.qa

import id.tunarasa.backend.chat.ChatConfig
import id.tunarasa.backend.chat.chat.ChatService
import id.tunarasa.backend.chat.chat.SERVICE_MODES
import id.tunarasa.backend.chat.http.ApiException
import id.tunarasa.backend.chat.http.ChatAuth
import id.tunarasa.backend.chat.http.bool
import id.tunarasa.backend.chat.http.enumOf
import id.tunarasa.backend.chat.http.handle
import id.tunarasa.backend.chat.http.int
import id.tunarasa.backend.chat.http.notFound
import id.tunarasa.backend.chat.http.pathInt
import id.tunarasa.backend.chat.http.queryInstantOrNull
import id.tunarasa.backend.chat.http.queryInt
import id.tunarasa.backend.chat.http.queryIntOrNull
import id.tunarasa.backend.chat.http.readJsonObject
import id.tunarasa.backend.chat.http.respondJson
import id.tunarasa.backend.chat.http.sessionIdOrNull
import id.tunarasa.backend.chat.http.string
import id.tunarasa.contracts.RagLlmService
import id.tunarasa.contracts.ServiceMode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant

/**
 * Q&A logging. Mounted under BOTH `/api/v1/qa-log/...` (reference path) and `/api/v1/qa/...` (BE-2 interface name).
 *
 * | Method | Path                          | Caller |
 * |--------|-------------------------------|--------|
 * | POST   | /log                          | owner session or admin (sets responded_by/admin_id only for admins) |
 * | POST   | /admin-validation             | owner session or admin |
 * | POST   | /gesture                      | owner session or admin |
 * | GET    | /institution/{institutionId}  | admin (reference: public) |
 * | GET    | /admin                        | admin (reference: Next route `/api/admin/qa-logs`) |
 * | POST   | /{qaId}/evaluate              | admin; calls AI-2 `evaluateResponse` and stores `evaluation_score` |
 * | GET    | /health                       | public |
 *
 * Writes need `X-Session-Id` equal to the conversation's session: the reference let anyone write QA rows into any conversation.
 */
internal object QaLogRoutes {

    fun install(parent: Route, qa: QaLogService, chat: ChatService, rag: RagLlmService, auth: ChatAuth, config: ChatConfig) {
        for (prefix in listOf("qa-log", "qa")) {
            parent.route("/$prefix") {
                post("/log") { call.handle { call.respondJson(write(call, qa, chat, auth, config, Kind.GENERIC)) } }
                post("/admin-validation") { call.handle { call.respondJson(write(call, qa, chat, auth, config, Kind.ADMIN_VALIDATION)) } }
                post("/gesture") { call.handle { call.respondJson(write(call, qa, chat, auth, config, Kind.GESTURE)) } }

                get("/institution/{institutionId}") {
                    call.handle {
                        auth.requireAdmin(call)
                        val institutionId = call.pathInt("institutionId")
                        val limit = call.queryInt("limit", 100, 1, 1000)
                        val offset = call.queryInt("offset", 0, 0, 1_000_000)
                        val minConf = call.queryIntOrNull("min_confidence")
                        val logs = qa.listForInstitution(institutionId, limit, offset, minConf)
                        call.respondJson(
                            buildJsonObject {
                                put("success", true)
                                put("institution_id", institutionId)
                                put("total_logs", logs.size)
                                put("qa_logs", logs)
                                put(
                                    "filters",
                                    buildJsonObject { put("limit", limit); put("offset", offset); put("min_confidence", minConf) },
                                )
                            },
                        )
                    }
                }

                get("/admin") {
                    call.handle {
                        auth.requireAdmin(call)
                        val q = call.request.queryParameters
                        val mode = q["serviceMode"]?.takeIf { it.isNotBlank() }
                        val by = q["respondedBy"]?.takeIf { it.isNotBlank() }
                        val filters = QaFilters(
                            page = call.queryInt("page", 1, 1, 1_000_000),
                            limit = call.queryInt("limit", 50, 1, 100),
                            serviceMode = mode?.also { enumOf("serviceMode", it, SERVICE_MODES, it) },
                            respondedBy = by?.also { enumOf("respondedBy", it, setOf("llm", "admin"), it) },
                            search = q["search"]?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
                            dateFrom = call.queryInstantOrNull("dateFrom"),
                            dateTo = call.queryInstantOrNull("dateTo"),
                            minConfidence = call.queryIntOrNull("minConfidence")?.takeIf { it in 0..100 },
                            maxConfidence = call.queryIntOrNull("maxConfidence")?.takeIf { it in 0..100 },
                        )
                        call.respondJson(qa.adminPage(filters))
                    }
                }

                post("/{qaId}/evaluate") {
                    call.handle {
                        auth.requireAdmin(call)
                        val qaId = call.pathInt("qaId")
                        qa.get(qaId) ?: notFound("QA log not found")
                        val eval = try {
                            rag.evaluateResponse(qaId)
                        } catch (e: IllegalArgumentException) {
                            throw ApiException(HttpStatusCode.BadRequest, "QA log cannot be evaluated")
                        } catch (e: IOException) {
                            throw ApiException(HttpStatusCode.BadGateway, "Evaluation service failed")
                        } catch (e: NotImplementedError) {
                            throw ApiException(HttpStatusCode.ServiceUnavailable, "Evaluation service is not available yet")
                        }
                        qa.setEvaluation(qaId, eval.score)
                        call.respondJson(
                            buildJsonObject {
                                put("success", true)
                                put(
                                    "data",
                                    buildJsonObject {
                                        put("qaId", qaId); put("score", eval.score.coerceIn(0, 100)); put("reasoning", eval.reasoning)
                                    },
                                )
                            },
                        )
                    }
                }

                get("/health") {
                    call.handle {
                        call.respondJson(
                            buildJsonObject {
                                put("success", true); put("service", "qa-logging"); put("status", "healthy")
                                put("timestamp", Instant.now().toString())
                            },
                        )
                    }
                }
            }
        }
    }

    private enum class Kind { GENERIC, ADMIN_VALIDATION, GESTURE }

    private suspend fun write(
        call: ApplicationCall,
        qa: QaLogService,
        chat: ChatService,
        auth: ChatAuth,
        config: ChatConfig,
        kind: Kind,
    ): JsonObject {
        val admin = auth.adminOrNull(call)
        val b = call.readJsonObject()
        val convId = b.int("conversation_id", "conversationId", required = true, min = 1)!!
        // Same 404 for "missing" and "not yours": ids must not be probeable.
        if (!chat.canAccess(convId, admin != null, call.sessionIdOrNull())) notFound("Conversation not found")
        val conv = chat.requireConversation(convId)

        val question = b.string("question", required = true, min = 1, max = 10_000)!!
        val answer = b.string("answer", required = true, min = 1, max = 50_000)!!
        val institutionId = b.int("institution_id", "institutionId", min = 1) ?: conv.institutionId ?: config.defaultInstitutionId
        val confidence = b.int("confidence", min = 0, max = 100)
        val entry = when (kind) {
            Kind.GENERIC -> QaEntry(
                conversationId = convId, institutionId = institutionId, question = question, answer = answer,
                confidence = confidence,
                responseTime = b.int("response_time", "responseTime", min = 0),
                gestureInput = b.string("gesture_input", "gestureInput", max = 10_000),
                contextUsed = b.string("context_used", "contextUsed", max = 100_000),
                // Only an admin may attest an evaluation, an admin identity or an admin-authored answer.
                evaluationScore = if (admin != null) b.int("evaluation_score", "evaluationScore", min = 0, max = 100) else null,
                serviceMode = enumOf("service_mode", b.string("service_mode", "serviceMode"), SERVICE_MODES, ServiceMode.FULL_LLM_BOT.wire),
                respondedBy = if (admin != null) enumOf("responded_by", b.string("responded_by", "respondedBy"), setOf("llm", "admin"), "llm") else "llm",
                adminId = admin?.userId,
                llmRecommendationUsed = b.bool("llm_recommendation_used", "llmRecommendationUsed") ?: false,
            )
            Kind.ADMIN_VALIDATION -> QaEntry(
                conversationId = convId, institutionId = institutionId, question = question, answer = answer,
                confidence = confidence ?: 75, // reference default for a pending approval
                serviceMode = ServiceMode.BOT_WITH_ADMIN_VALIDATION.wire,
                respondedBy = "llm",
                llmRecommendationUsed = b.bool("llm_recommendation_used", "llmRecommendationUsed") ?: false,
            )
            Kind.GESTURE -> QaEntry(
                conversationId = convId, institutionId = institutionId, question = question, answer = answer,
                confidence = confidence,
                responseTime = b.int("response_time", "responseTime", min = 0),
                gestureInput = b.string("gesture_input", "gestureInput", required = true, min = 1, max = 10_000),
            )
        }
        val id = qa.log(entry)
        return buildJsonObject {
            put("success", true)
            put("qa_log_id", id)
            put(
                "message",
                when (kind) {
                    Kind.GENERIC -> "QA interaction logged successfully"
                    Kind.ADMIN_VALIDATION -> "Admin validation QA logged successfully"
                    Kind.GESTURE -> "Gesture QA interaction logged successfully"
                },
            )
        }
    }
}
