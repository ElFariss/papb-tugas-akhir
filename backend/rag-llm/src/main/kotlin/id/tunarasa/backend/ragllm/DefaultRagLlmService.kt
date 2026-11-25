package id.tunarasa.backend.ragllm

import id.tunarasa.backend.ragllm.ingestion.DocumentParser
import id.tunarasa.backend.ragllm.ingestion.InvalidDocument
import id.tunarasa.backend.ragllm.ingestion.PineconeFailure
import id.tunarasa.backend.ragllm.ingestion.PineconeIndex
import id.tunarasa.backend.ragllm.ingestion.RagDocumentSource
import id.tunarasa.contracts.Answer
import id.tunarasa.contracts.Evaluation
import id.tunarasa.contracts.FaqCluster
import id.tunarasa.contracts.IngestionResult
import id.tunarasa.contracts.RagLlmService
import id.tunarasa.contracts.RagProcessingStatus
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Server entry point for the AI-2 module. */
class DefaultRagLlmService internal constructor(
    private val documentSource: RagDocumentSource,
    private val index: PineconeIndex,
) : RagLlmService {
    constructor(documentSource: RagDocumentSource, pineconeApiKey: String, pineconeIndexHost: String) :
        this(documentSource, PineconeIndex.configured(pineconeApiKey, pineconeIndexHost))

    override suspend fun ingestDocument(ragFileId: Int, institutionSlug: String): IngestionResult {
        if (ragFileId <= 0 || !institutionSlug.matches(institutionSlugPattern)) {
            return failed(ragFileId, "Invalid document ID or institution slug")
        }

        return try {
            val document = documentSource.find(ragFileId, institutionSlug)
                ?: return failed(ragFileId, "Document not found for this institution")
            val chunks = runInterruptible(Dispatchers.IO) { DocumentParser.parse(document) }
            index.upsert(ragFileId, institutionSlug, document.fileName, chunks)
            IngestionResult(ragFileId, RagProcessingStatus.COMPLETED, chunks.size)
        } catch (error: InvalidDocument) {
            failed(ragFileId, error.message ?: "Invalid document")
        } catch (error: PineconeFailure) {
            failed(ragFileId, error.message ?: "Pinecone indexing failed")
        } catch (_: IOException) {
            failed(ragFileId, "Document could not be read or parsed")
        }
    }

    override suspend fun askRag(question: String, sessionId: String, institutionSlug: String?): Answer = TODO("AI-2")

    override suspend fun askGesture(
        gestureText: String,
        sessionId: String?,
        language: String,
        gestureConfidence: Float?,
    ): Answer = TODO("AI-2")

    override suspend fun clusterFaqs(institutionId: Int): List<FaqCluster> = TODO("AI-2")

    override suspend fun evaluateResponse(qaId: Int): Evaluation = TODO("AI-2")

    private fun failed(ragFileId: Int, message: String) =
        IngestionResult(ragFileId, RagProcessingStatus.FAILED, chunkCount = 0, error = message)

    private companion object {
        val institutionSlugPattern = Regex("[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}")
    }
}
