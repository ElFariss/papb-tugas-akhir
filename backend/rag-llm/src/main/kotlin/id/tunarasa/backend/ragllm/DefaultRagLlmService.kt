package id.tunarasa.backend.ragllm

import id.tunarasa.contracts.Answer
import id.tunarasa.contracts.Evaluation
import id.tunarasa.contracts.FaqCluster
import id.tunarasa.contracts.IngestionResult
import id.tunarasa.contracts.RagLlmService

/** PLACEHOLDER. Owner: AI-2. The only class BE-1 and BE-2 need from this module. */
class DefaultRagLlmService : RagLlmService {
    override suspend fun ingestDocument(ragFileId: Int, institutionSlug: String): IngestionResult = TODO("AI-2")

    override suspend fun askRag(question: String, sessionId: String, institutionSlug: String?): Answer = TODO("AI-2")

    override suspend fun askGesture(
        gestureText: String,
        sessionId: String?,
        language: String,
        gestureConfidence: Float?,
    ): Answer = TODO("AI-2")

    override suspend fun clusterFaqs(institutionId: Int): List<FaqCluster> = TODO("AI-2")

    override suspend fun evaluateResponse(qaId: Int): Evaluation = TODO("AI-2")
}
