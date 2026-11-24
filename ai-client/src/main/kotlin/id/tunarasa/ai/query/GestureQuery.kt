package id.tunarasa.ai.query

import id.tunarasa.ai.gesture.WordState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Body of `POST /api/v1/gesture/ask`. */
@Serializable
data class GestureAskRequest(
    val text: String,
    @SerialName("session_id") val sessionId: String? = null,
    val language: String = "id",
    @SerialName("gesture_confidence") val gestureConfidence: Float? = null,
)

sealed interface GestureQueryResult {
    data class Valid(val request: GestureAskRequest) : GestureQueryResult
    data class Invalid(val reason: Reason) : GestureQueryResult

    enum class Reason { EMPTY_TEXT, TEXT_TOO_LONG, INVALID_SESSION_ID, UNSUPPORTED_LANGUAGE, CONFIDENCE_OUT_OF_RANGE }
}

/** Builds the request and applies the backend's validation rules, so bad input never reaches the network. */
object GestureQueryFormatter {
    const val MAX_TEXT_LENGTH = 500
    const val MAX_SESSION_ID_LENGTH = 255
    val LANGUAGES = setOf("id", "en")

    private val SESSION_ID = Regex("^[a-zA-Z0-9_-]+$")
    private val FORBIDDEN_IN_SESSION_ID =
        Regex("""\b(SELECT|INSERT|UPDATE|DELETE|DROP|UNION)\b|--""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("""\s+""")
    private val json = Json { explicitNulls = false; encodeDefaults = true }

    fun format(word: WordState, sessionId: String? = null, language: String = "id"): GestureQueryResult =
        format(word.text, word.averageConfidence, sessionId, language)

    fun format(
        text: String,
        confidence: Float? = null,
        sessionId: String? = null,
        language: String = "id",
    ): GestureQueryResult {
        val clean = text.trim().replace(WHITESPACE, " ")
        val problem = when {
            clean.isEmpty() -> GestureQueryResult.Reason.EMPTY_TEXT
            clean.length > MAX_TEXT_LENGTH -> GestureQueryResult.Reason.TEXT_TOO_LONG
            sessionId != null && !isValidSessionId(sessionId) -> GestureQueryResult.Reason.INVALID_SESSION_ID
            language !in LANGUAGES -> GestureQueryResult.Reason.UNSUPPORTED_LANGUAGE
            confidence != null && confidence !in 0f..1f -> GestureQueryResult.Reason.CONFIDENCE_OUT_OF_RANGE
            else -> null
        }
        return if (problem != null) {
            GestureQueryResult.Invalid(problem)
        } else {
            GestureQueryResult.Valid(GestureAskRequest(clean, sessionId, language, confidence))
        }
    }

    fun toJson(request: GestureAskRequest): String = json.encodeToString(GestureAskRequest.serializer(), request)

    private fun isValidSessionId(id: String): Boolean =
        id.length <= MAX_SESSION_ID_LENGTH && SESSION_ID.matches(id) && !FORBIDDEN_IN_SESSION_ID.containsMatchIn(id)
}
