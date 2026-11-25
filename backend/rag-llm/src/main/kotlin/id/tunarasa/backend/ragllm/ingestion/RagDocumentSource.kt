package id.tunarasa.backend.ragllm.ingestion

import java.nio.file.Path

/** A local, readable file from BE-1 storage with the original basename used to identify its format. */
data class RagDocument(val path: Path, val fileName: String)

/** Implemented by BE-1 and injected by the server when constructing the RAG service. */
fun interface RagDocumentSource {
    /**
     * Resolves a committed upload belonging to this institution, or returns null.
     * The path must remain readable for this call to ingestion. An upload ID identifies immutable
     * content so a retry or duplicate job can safely index the same chunks.
     */
    suspend fun find(ragFileId: Int, institutionSlug: String): RagDocument?
}
