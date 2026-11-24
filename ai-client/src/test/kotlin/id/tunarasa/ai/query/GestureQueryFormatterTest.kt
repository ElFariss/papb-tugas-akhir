package id.tunarasa.ai.query

import id.tunarasa.ai.gesture.CommittedLetter
import id.tunarasa.ai.gesture.WordState
import id.tunarasa.ai.query.GestureQueryResult.Reason
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureQueryFormatterTest {

    private fun reason(
        text: String = "KTP",
        confidence: Float? = null,
        sessionId: String? = null,
        language: String = "id",
    ) = (GestureQueryFormatter.format(text, confidence, sessionId, language) as GestureQueryResult.Invalid).reason

    @Test
    fun `json uses the backend field names`() {
        val word = WordState("KTP".map { CommittedLetter(it, 0.75f, 0) })
        val valid = GestureQueryFormatter.format(word, sessionId = "anonymous_session_123") as GestureQueryResult.Valid
        assertEquals(
            """{"text":"KTP","session_id":"anonymous_session_123","language":"id","gesture_confidence":0.75}""",
            GestureQueryFormatter.toJson(valid.request),
        )
    }

    @Test
    fun `optional fields are omitted and whitespace collapsed`() {
        val valid = GestureQueryFormatter.format("  cara   buat KTP ") as GestureQueryResult.Valid
        assertEquals("""{"text":"cara buat KTP","language":"id"}""", GestureQueryFormatter.toJson(valid.request))
    }

    @Test
    fun `invalid input is rejected like the backend does`() {
        assertEquals(Reason.EMPTY_TEXT, reason(text = "   "))
        assertEquals(Reason.TEXT_TOO_LONG, reason(text = "a".repeat(501)))
        assertEquals(Reason.INVALID_SESSION_ID, reason(sessionId = "has space"))
        assertEquals(Reason.INVALID_SESSION_ID, reason(sessionId = "a--b"))
        assertEquals(Reason.INVALID_SESSION_ID, reason(sessionId = "x-DROP-y"))
        assertEquals(Reason.INVALID_SESSION_ID, reason(sessionId = "a".repeat(256)))
        assertEquals(Reason.UNSUPPORTED_LANGUAGE, reason(language = "fr"))
        assertEquals(Reason.CONFIDENCE_OUT_OF_RANGE, reason(confidence = 1.01f))
    }
}
