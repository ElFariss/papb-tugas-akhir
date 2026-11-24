package id.tunarasa.ai.speech

import id.tunarasa.ai.speech.SpeechSession.Action
import id.tunarasa.ai.speech.SpeechSession.Failure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSessionTest {
    private val session = SpeechSession().apply { start(); onReady() }

    @Test
    fun `utterances accumulate and listening continues`() {
        session.onPartial("cara")
        assertEquals("cara", session.state.transcript)
        assertTrue(session.onFinal("cara membuat") is Action.Listen)
        session.onPartial("ktp")
        assertEquals("cara membuat ktp", session.state.transcript)
        session.onFinal("KTP baru")
        assertEquals("cara membuat KTP baru", session.state.finalText)
        assertEquals("", session.state.partialText)
        assertEquals(SpeechStatus.LISTENING, session.state.status)
    }

    @Test
    fun `silence restarts forever`() {
        repeat(20) { assertTrue(session.onFailure(Failure.SILENCE) is Action.Listen) }
        assertEquals(null, session.state.error)
    }

    @Test
    fun `an error keeps the words already heard`() {
        session.onPartial("tolong saya")
        session.onFailure(Failure.SILENCE)
        assertEquals("tolong saya", session.state.finalText)
    }

    @Test
    fun `an empty final result keeps the partial`() {
        session.onPartial("terima kasih")
        session.onFinal("")
        assertEquals("terima kasih", session.state.finalText)
    }

    @Test
    fun `network errors give up after three retries`() {
        repeat(3) { assertTrue(session.onFailure(Failure.NETWORK) is Action.Listen) }
        assertEquals(Action.None, session.onFailure(Failure.NETWORK))
        assertEquals(SpeechStatus.ERROR, session.state.status)
        assertEquals(SpeechError.NETWORK, session.state.error)
    }

    @Test
    fun `speech resets the retry budget`() {
        repeat(3) { session.onFailure(Failure.NETWORK) }
        session.onPartial("halo")
        assertTrue(session.onFailure(Failure.NETWORK) is Action.Listen)
    }

    @Test
    fun `a missing permission is fatal at once`() {
        assertEquals(Action.None, session.onFailure(Failure.PERMISSION))
        assertEquals(SpeechError.PERMISSION_DENIED, session.state.error)
    }

    @Test
    fun `stopping waits for the last words then goes idle`() {
        session.onPartial("permi")
        session.requestStop()
        assertEquals(SpeechStatus.STOPPING, session.state.status)
        assertEquals(Action.None, session.onFinal("permisi"))
        assertEquals(SpeechStatus.IDLE, session.state.status)
        assertEquals("permisi", session.state.transcript)
    }

    @Test
    fun `an error while stopping is not reported`() {
        session.requestStop()
        assertEquals(Action.None, session.onFailure(Failure.NETWORK))
        assertEquals(SpeechStatus.IDLE, session.state.status)
        assertEquals(null, session.state.error)
    }

    @Test
    fun `starting again clears the transcript`() {
        session.onFinal("lama")
        session.requestStop()
        session.forceIdle()
        session.start()
        assertEquals("", session.state.transcript)
        assertEquals(SpeechStatus.STARTING, session.state.status)
    }
}
