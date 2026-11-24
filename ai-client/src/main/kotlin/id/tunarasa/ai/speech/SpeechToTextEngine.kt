package id.tunarasa.ai.speech

import kotlinx.coroutines.flow.StateFlow

/**
 * Continuous Indonesian dictation. **Public contract consumed by FE.**
 *
 * Call every method on the main thread. The caller must hold `android.permission.RECORD_AUDIO` before [start].
 */
interface SpeechToTextEngine {
    val state: StateFlow<SpeechState>

    /** Clears the transcript and listens until [stop]. */
    fun start()

    /** Stops listening; the last words may still arrive before the status becomes [SpeechStatus.IDLE]. */
    fun stop()

    fun clearTranscript()

    /** Releases the recogniser. The instance cannot be reused. */
    fun close()
}

data class SpeechState(
    val status: SpeechStatus = SpeechStatus.IDLE,
    /** Text the recogniser has settled on. */
    val finalText: String = "",
    /** Current guess for the words being spoken; may still change. */
    val partialText: String = "",
    /** Microphone level in dB, for a level meter. */
    val rmsDb: Float = 0f,
    val error: SpeechError? = null,
) {
    val transcript: String
        get() = listOf(finalText, partialText).filter { it.isNotBlank() }.joinToString(" ")

    /** True from the tap on the mic until the user stops or an error ends the session. */
    val isActive: Boolean
        get() = status == SpeechStatus.STARTING || status == SpeechStatus.LISTENING
}

enum class SpeechStatus { IDLE, STARTING, LISTENING, STOPPING, ERROR }

enum class SpeechError {
    NOT_AVAILABLE,
    PERMISSION_DENIED,
    LANGUAGE_UNAVAILABLE,
    NETWORK,
    AUDIO,
    SERVICE,
}
