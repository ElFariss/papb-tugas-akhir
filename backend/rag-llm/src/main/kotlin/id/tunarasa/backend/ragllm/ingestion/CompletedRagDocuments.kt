package id.tunarasa.backend.ragllm.ingestion

/** Reads the authoritative catalog of searchable uploads from BE-1. */
fun interface CompletedRagDocuments {
    /** Returns current COMPLETED upload IDs for this institution. Excludes deleted or pending uploads. */
    suspend fun findIds(institutionSlug: String): Set<Int>
}
