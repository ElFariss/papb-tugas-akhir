package id.tunarasa.backend

import id.tunarasa.backend.platform.db.DatabaseFactory
import id.tunarasa.backend.ragllm.DefaultRagLlmService
import id.tunarasa.backend.ragllm.ingestion.CompletedRagDocuments
import id.tunarasa.backend.ragllm.ingestion.RagDocument
import id.tunarasa.backend.ragllm.ingestion.RagDocumentSource
import java.nio.file.Path

internal fun createRagLlmService(): DefaultRagLlmService {
    val source = RagDocumentSource { fileId, slug ->
        DatabaseFactory.ragDocument(fileId, slug)
            ?.let { RagDocument(Path.of(it.filePath), it.fileName) }
    }
    val completed = CompletedRagDocuments(DatabaseFactory::completedRagDocumentIds)

    val pineconeIndexUrl = System.getenv("PINECONE_INDEX_URL")
        ?: System.getenv("PINECONE_INDEX_HOST")?.let { "https://$it" }
        ?: "http://127.0.0.1:65535"
    return DefaultRagLlmService(
        documentSource = source,
        completedDocuments = completed,
        pineconeApiKey = System.getenv("PINECONE_API_KEY") ?: "local-test-key",
        pineconeIndexUrl = pineconeIndexUrl,
        pineconeEmbedUrl = System.getenv("PINECONE_EMBED_URL"),
        chatBaseUrl = System.getenv("RAG_CHAT_BASE_URL") ?: "http://127.0.0.1:65535/v1",
        chatApiKey = System.getenv("RAG_CHAT_API_KEY") ?: "local-test-key",
        chatModel = System.getenv("RAG_CHAT_MODEL") ?: "local-test-model",
    )
}
