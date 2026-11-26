package id.tunarasa.backend.chat.summary

/**
 * PORT. The reference summarised with `langchain_service.generate_summary` / `generate_title_of_summary`.
 * `:contracts` `RagLlmService` has no such methods, so this is a BE-2 port until AI-2 adds them (see README "Open items").
 */
interface SummaryTextGenerator {
    /** Indonesian summary of the conversation transcript (`user:` / `bot:` / `admin:` lines). */
    suspend fun summarize(transcript: String): String

    /** Short title for the summary. May return garbage (the reference LLM did); callers sanitise it. */
    suspend fun title(summary: String): String
}

/** No-LLM fallback so the QR/PDF feature works before AI-2 ships summarisation. */
class ExtractiveSummaryTextGenerator(private val maxChars: Int = 1200) : SummaryTextGenerator {
    override suspend fun summarize(transcript: String): String {
        val body = transcript.trim()
        if (body.length <= maxChars) return body
        // cut on a sentence/word boundary
        val cut = body.take(maxChars)
        val end = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf('\n'), cut.lastIndexOf(' '))
        return (if (end > maxChars / 2) cut.take(end) else cut).trimEnd() + " …"
    }

    override suspend fun title(summary: String): String {
        val first = summary.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
        val stripped = first.removePrefix("user:").removePrefix("bot:").removePrefix("admin:").trim()
        return stripped.take(60)
    }
}
