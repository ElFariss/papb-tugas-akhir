package id.tunarasa.contracts

/**
 * What AI-2 provides and BE-1 / BE-2 call. Implemented in `:backend:rag-llm`.
 *
 * DRAFT: method names and parameters come from the task table; return types are a proposal that
 * AI-2, BE-1 and BE-2 must confirm in the first pull request that uses them.
 */
interface RagLlmService {
    /** Called by BE-1 after an upload. Parses, chunks and indexes the file. */
    suspend fun ingestDocument(ragFileId: Int, institutionSlug: String): IngestionResult

    /** Called by BE-2. */
    suspend fun askRag(question: String, sessionId: String, institutionSlug: String?): Answer

    /** Called by BE-2 for `POST /api/v1/gesture/ask`; input is produced by AI-1's `GestureQueryFormatter`. */
    suspend fun askGesture(
        gestureText: String,
        sessionId: String?,
        language: String,
        gestureConfidence: Float?,
    ): Answer

    suspend fun clusterFaqs(institutionId: Int): List<FaqCluster>

    suspend fun evaluateResponse(qaId: Int): Evaluation
}

data class IngestionResult(val ragFileId: Int, val status: RagProcessingStatus, val chunkCount: Int, val error: String? = null)

data class Source(val documentId: String, val excerpt: String, val score: Double)

data class Answer(val text: String, val confidence: Double, val sources: List<Source>, val category: String? = null)

data class FaqCluster(val representativeQuestion: String, val questions: List<String>, val silhouetteScore: Double)

data class Evaluation(val qaId: Int, val score: Int, val reasoning: String)
