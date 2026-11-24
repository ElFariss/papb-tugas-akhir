package id.tunarasa.contracts

/** `conversations.service_mode`, `app_settings` key `service_mode`. */
enum class ServiceMode(val wire: String) {
    FULL_LLM_BOT("full_llm_bot"),
    BOT_WITH_ADMIN_VALIDATION("bot_with_admin_validation"),
}

/** `messages.input_method`. */
enum class InputMethod(val wire: String) { TEXT("text"), SPEECH("speech"), GESTURE("gesture") }

/** `conversations.status`. */
enum class ConversationStatus(val wire: String) {
    ACTIVE("active"), WAITING("waiting"), IN_PROGRESS("in_progress"), RESOLVED("resolved"),
}

/** `rag_files.processing_status`. */
enum class RagProcessingStatus(val wire: String) {
    PENDING("pending"), PROCESSING("processing"), COMPLETED("completed"), FAILED("failed"),
}

/** Pinecone namespace of an institution. */
fun institutionNamespace(slug: String): String = "institution_$slug"
