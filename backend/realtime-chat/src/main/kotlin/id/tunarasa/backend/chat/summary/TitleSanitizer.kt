package id.tunarasa.backend.chat.summary

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

internal const val DEFAULT_TITLE = "Ringkasan Percakapan Tunarasa"

/**
 * Port of `_clean_title_from_database`, `_add_proper_spacing`, `_has_concatenated_words`,
 * `_apply_basic_spacing_patterns` (`summary.py`). Repairs titles that an LLM returned as
 * `["R","i","n",...]`, `JUDUL: ...`, quoted, or glued together (`BuatKTPdanSIM`).
 */
internal object TitleSanitizer {
    private val CONCATENATED = Regex("[a-z][A-Z]|[A-Z]{2,}[a-z]")

    fun clean(raw: String?): String {
        if (raw == null) return DEFAULT_TITLE
        var s = raw.trim()
        if (s.uppercase().startsWith("JUDUL:")) s = s.substring(6).trim()

        if (s.startsWith("[") && s.contains(",") && s.endsWith("]")) {
            s = joinCharArray(s) ?: s.trim('[', ']').replace("'", "").replace("\"", "")
                .split(",").joinToString("") { it.trim() }
        }
        s = s.trim('"', '\'').replace("{", "").replace("}", "").replace(":", ": ")
        s = addProperSpacing(s)
        return if (s.length < 5) DEFAULT_TITLE else s
    }

    private fun joinCharArray(s: String): String? {
        for (candidate in listOf(s, s.replace("'", "\""))) {
            val arr = try {
                Json.parseToJsonElement(candidate) as? JsonArray
            } catch (_: Exception) {
                null
            } ?: continue
            return arr.joinToString("") { (it as? JsonPrimitive)?.content ?: it.toString() }
        }
        return null
    }

    internal fun addProperSpacing(text: String): String {
        if (text.contains(' ') && !CONCATENATED.containsMatchIn(text)) return text
        var r = text
        r = r.replace(Regex("([a-z])dan([A-Z])"), "$1 dan $2")
        r = r.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        r = r.replace(Regex("(?<!\\w)dan([A-Z])"), "dan $1")
        r = r.replace(Regex("([a-z])dan(?!\\w)"), "$1 dan")
        r = r.replace(Regex("SIM ([A-Z])"), "SIM$1")
        r = r.replace(Regex("([A-Z]{2,}) ([A-Z][a-z])"), "$1$2")
        r = r.replace(Regex("([a-z])\\("), "$1 (")
        r = r.replace(Regex("\\s+"), " ")
        return r.trim()
    }
}
