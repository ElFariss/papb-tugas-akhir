package id.tunarasa.backend.chat.gateway

import id.tunarasa.backend.chat.ChatConfig
import id.tunarasa.backend.chat.http.ApiException
import id.tunarasa.backend.chat.http.ChatAuth
import id.tunarasa.backend.chat.http.SESSION_ID_REGEX
import id.tunarasa.backend.chat.http.clientIp
import id.tunarasa.backend.chat.http.double
import id.tunarasa.backend.chat.http.enumOf
import id.tunarasa.backend.chat.http.envelopeOk
import id.tunarasa.backend.chat.http.handle
import id.tunarasa.backend.chat.http.int
import id.tunarasa.backend.chat.http.pathInt
import id.tunarasa.backend.chat.http.readJsonObject
import id.tunarasa.backend.chat.http.respondJson
import id.tunarasa.backend.chat.http.string
import id.tunarasa.backend.chat.qa.QaLogService
import id.tunarasa.contracts.Answer
import id.tunarasa.contracts.FaqCluster
import id.tunarasa.contracts.RagLlmService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Gateway to AI-2 (`RagLlmService`). BE-2 owns the HTTP surface; AI-2 owns the logic.
 *
 * | Method | Path                              | Auth  | AI-2 call |
 * |--------|-----------------------------------|-------|-----------|
 * | POST   | /api/v1/rag/ask                   | none (rate limited) | askRag |
 * | POST   | /api/v1/gesture/ask               | none (rate limited) | askGesture |
 * | GET    | /api/v1/faq/recommendations/{id}  | none (rate limited) | clusterFaqs (cached 10 min) |
 * | POST   | /api/v1/faq/refresh/{id}          | admin | clusterFaqs (cache bypass) |
 * | GET    | /api/v1/faq/metrics/{id}          | none  | from the cached clustering |
 * | GET    | /api/v1/faq/health, /api/v1/gesture/health | none | - |
 * LLM evaluation reports: `POST /api/v1/qa/{qaId}/evaluate` in `QaLogRoutes`.
 *
 * AI-2 error contract: `IllegalArgumentException` = invalid input (422), `IOException` = failure (502),
 * `NotImplementedError` = AI-2 stub not delivered yet (503).
 */
internal object AiGatewayRoutes {
    private const val FAQ_TTL_MILLIS = 10 * 60 * 1000L

    private class CachedFaq(val at: Long, val clusters: List<FaqCluster>, val tookSeconds: Double)

    fun install(parent: Route, rag: RagLlmService, qa: QaLogService, config: ChatConfig, auth: ChatAuth) {
        val limiter = FixedWindowLimiter(config.publicAiRequestsPerMinute)
        val faqCache = ConcurrentHashMap<Int, CachedFaq>()

        parent.route("/rag") {
            post("/ask") {
                call.handle {
                    enforceRate(call, auth, limiter)
                    val b = call.readJsonObject()
                    val question = b.string("question", required = true, min = 1, max = 500)!!
                    val sessionId = b.string("session_id", "sessionId", required = true, pattern = SESSION_ID_REGEX)!!
                    enumOf("language", b.string("language"), setOf("id", "en"), "id")
                    enumOf("input_source", b.string("input_source", "inputSource"), setOf("text", "gesture", "speech"), "text")
                    // Accepted for client compatibility; the AI-2 contract has no such knobs.
                    b.int("max_sources", min = 1, max = 10); b.double("similarity_threshold", min = 0.0, max = 1.0)
                    b.int("institution_id", "institutionId", min = 1)
                    val slug = b.string("institution_slug", "institutionSlug", max = 100)

                    val t0 = System.nanoTime()
                    val a = ai { rag.askRag(question, sessionId, slug) }
                    val took = (System.nanoTime() - t0) / 1e9
                    call.respondJson(
                        buildJsonObject {
                            put("success", true)
                            put("question", question)
                            put("answer", a.text)
                            put("confidence", a.confidence)
                            put("sources", sourcesJson(a))
                            put("session_id", sessionId)
                            put("processing_time", took)
                            put("follow_up_suggestions", JsonArray(emptyList()))
                            put("message", if (a.sources.isEmpty()) "No supporting documents found" else "Question answered successfully")
                            put("timestamp", Instant.now().toString())
                            put("category", a.category)
                            put("original_question", question)
                            put("typo_corrected", false)
                        },
                    )
                }
            }
        }

        parent.route("/gesture") {
            post("/ask") {
                call.handle(envelope = true) {
                    enforceRate(call, auth, limiter)
                    val b = call.readJsonObject()
                    val text = b.string("text", required = true, min = 1, max = 500)!!
                    val sessionId = b.string("session_id", "sessionId", max = 255, pattern = SESSION_ID_REGEX)
                        ?: "gesture_session_${Instant.now().epochSecond}"
                    val language = enumOf("language", b.string("language"), setOf("id", "en"), "id")
                    val gestureConfidence = b.double("gesture_confidence", "gestureConfidence", min = 0.0, max = 1.0)

                    val t0 = System.nanoTime()
                    val a = ai { rag.askGesture(text, sessionId, language, gestureConfidence?.toFloat()) }
                    val took = (System.nanoTime() - t0) / 1e9
                    call.respondJson(
                        envelopeOk(
                            buildJsonObject {
                                put("question", text)
                                put("answer", a.text)
                                put("confidence", a.confidence)
                                put("sources", sourcesJson(a))
                                put("processing_time", took)
                                put("session_id", sessionId)
                                put("gesture_confidence", gestureConfidence)
                            },
                        ),
                    )
                }
            }
            get("/health") {
                call.handle(envelope = true) {
                    call.respondJson(envelopeOk(buildJsonObject { put("status", "healthy"); put("service", "gesture") }))
                }
            }
        }

        parent.route("/faq") {
            get("/recommendations/{institution_id}") {
                call.handle {
                    enforceRate(call, auth, limiter)
                    val id = call.pathInt("institution_id")
                    val force = call.request.queryParameters["force_refresh"]?.lowercase() == "true"
                    call.respondJson(faq(id, force, rag, qa, faqCache))
                }
            }
            post("/refresh/{institution_id}") {
                call.handle {
                    auth.requireAdmin(call)
                    val id = call.pathInt("institution_id")
                    val data = faq(id, true, rag, qa, faqCache)
                    call.respondJson(
                        buildJsonObject {
                            put("success", true)
                            put("message", "Successfully refreshed recommendations for institution $id")
                            put("data", data)
                        },
                    )
                }
            }
            get("/metrics/{institution_id}") {
                call.handle {
                    val id = call.pathInt("institution_id")
                    val c = faqCache[id]
                    call.respondJson(
                        buildJsonObject {
                            put("success", true)
                            put("institution_id", id)
                            put(
                                "metrics",
                                if (c == null) JsonNull else buildJsonObject {
                                    put("cluster_count", c.clusters.size)
                                    put("avg_questions_per_cluster", avgSize(c.clusters))
                                    put("silhouette_score", avgSilhouette(c.clusters))
                                    put("data_source", "database")
                                    put("last_updated", c.at / 1000.0)
                                },
                            )
                            put("description", "Clustering quality metrics for FAQ recommendations")
                        },
                    )
                }
            }
            get("/health") {
                call.handle {
                    call.respondJson(buildJsonObject { put("success", true); put("service", "FAQ Recommendation API"); put("status", "healthy") })
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun faq(
        institutionId: Int,
        force: Boolean,
        rag: RagLlmService,
        qa: QaLogService,
        cache: ConcurrentHashMap<Int, CachedFaq>,
    ): JsonObject {
        val now = System.currentTimeMillis()
        var entry = cache[institutionId]
        if (force || entry == null || now - entry.at > FAQ_TTL_MILLIS) {
            val t0 = System.nanoTime()
            val clusters = ai { rag.clusterFaqs(institutionId) }
            entry = CachedFaq(now, clusters, (System.nanoTime() - t0) / 1e9)
            cache[institutionId] = entry
        }
        val clusters = entry.clusters
        val recs = clusters.sortedByDescending { it.questions.size }.mapIndexed { i, c ->
            buildJsonObject {
                put("cluster_id", i)
                put("cluster_title", c.representativeQuestion.take(80))
                put("representative_question", c.representativeQuestion)
                put("representative_answer", qa.latestAnswer(institutionId, c.representativeQuestion) ?: "")
                put("question_count", c.questions.size)
                put("silhouette_score", c.silhouetteScore)
            }
        }
        return buildJsonObject {
            put("success", true)
            put("institution_id", institutionId)
            put("data_source", "database")
            put("total_questions", clusters.sumOf { it.questions.size })
            put("cluster_count", clusters.size)
            put("avg_questions_per_cluster", avgSize(clusters))
            put("silhouette_score", avgSilhouette(clusters))
            put("processing_time_seconds", entry.tookSeconds)
            put("recommendations", JsonArray(recs))
            put("generated_at", entry.at / 1000.0)
        }
    }

    private fun avgSize(c: List<FaqCluster>) = if (c.isEmpty()) 0.0 else c.sumOf { it.questions.size }.toDouble() / c.size
    private fun avgSilhouette(c: List<FaqCluster>) = if (c.isEmpty()) 0.0 else c.map { it.silhouetteScore }.average()

    private fun sourcesJson(a: Answer): JsonArray = JsonArray(
        a.sources.map { buildJsonObject { put("document_id", it.documentId); put("excerpt", it.excerpt); put("score", it.score) } },
    )

    /** Maps the AI-2 error contract onto HTTP. Cancellation is never caught. */
    private inline fun <T> ai(block: () -> T): T = try {
        block()
    } catch (e: IllegalArgumentException) {
        throw ApiException(HttpStatusCode.UnprocessableEntity, "Invalid request", "VALIDATION_ERROR")
    } catch (e: IOException) {
        throw ApiException(HttpStatusCode.BadGateway, "AI service failed", "AI_SERVICE_ERROR")
    } catch (e: NotImplementedError) {
        throw ApiException(HttpStatusCode.ServiceUnavailable, "This AI feature is not available yet", "NOT_AVAILABLE")
    }

    private suspend fun enforceRate(call: ApplicationCall, auth: ChatAuth, limiter: FixedWindowLimiter) {
        if (auth.adminOrNull(call) != null) return
        if (!limiter.tryAcquire(call.clientIp() ?: "unknown")) {
            throw ApiException(HttpStatusCode.TooManyRequests, "Too many requests, slow down", "RATE_LIMITED")
        }
    }
}

/** Fixed one-minute window per key. In-memory (per instance); good enough to cap LLM spend from a single client. */
internal class FixedWindowLimiter(
    private val perMinute: Int,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private class Window(var start: Long, var count: Int)

    private val windows = ConcurrentHashMap<String, Window>()

    fun tryAcquire(key: String): Boolean {
        val now = nowMillis()
        if (windows.size > 10_000) windows.entries.removeIf { now - it.value.start >= 60_000 }
        val w = windows.computeIfAbsent(key) { Window(now, 0) }
        synchronized(w) {
            if (now - w.start >= 60_000) {
                w.start = now
                w.count = 0
            }
            w.count++
            return w.count <= perMinute
        }
    }
}
