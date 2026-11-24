package id.tunarasa.ai.speech

/**
 * State machine that turns Android's one-utterance-at-a-time recogniser into continuous dictation.
 * Free of Android types so it can be unit tested.
 */
internal class SpeechSession(private val maxRetries: Int = 3) {
    var state = SpeechState()
        private set

    private var retries = 0

    /** What the engine must do after an event. */
    sealed interface Action {
        data object None : Action
        data class Listen(val delayMillis: Long) : Action
    }

    /** Why an utterance ended without a result. */
    enum class Failure(val error: SpeechError?, val retryDelayMillis: Long?) {
        /** Nobody spoke, or nothing was understood. Normal during continuous dictation. */
        SILENCE(null, RESTART_DELAY_MILLIS),
        BUSY(SpeechError.SERVICE, 400),
        SERVICE(SpeechError.SERVICE, 1_000),
        NETWORK(SpeechError.NETWORK, 1_000),
        AUDIO(SpeechError.AUDIO, null),
        PERMISSION(SpeechError.PERMISSION_DENIED, null),
        LANGUAGE(SpeechError.LANGUAGE_UNAVAILABLE, null),
    }

    fun start() {
        retries = 0
        state = SpeechState(status = SpeechStatus.STARTING)
    }

    fun requestStop() {
        if (state.isActive) state = state.copy(status = SpeechStatus.STOPPING, rmsDb = 0f)
    }

    /** The recogniser did not answer a stop request. */
    fun forceIdle() {
        commitPartial()
        state = state.copy(status = SpeechStatus.IDLE, rmsDb = 0f)
    }

    fun clearTranscript() {
        state = state.copy(finalText = "", partialText = "")
    }

    fun fail(error: SpeechError) {
        commitPartial()
        state = state.copy(status = SpeechStatus.ERROR, rmsDb = 0f, error = error)
    }

    fun onReady() {
        if (state.status == SpeechStatus.STARTING) state = state.copy(status = SpeechStatus.LISTENING)
    }

    fun onRms(rmsDb: Float) {
        if (state.status == SpeechStatus.LISTENING) state = state.copy(rmsDb = rmsDb)
    }

    fun onPartial(text: String) {
        if (text.isNotBlank()) {
            retries = 0
            state = state.copy(partialText = text.trim())
        }
    }

    fun onFinal(text: String): Action {
        retries = 0
        // Some recognisers return an empty final result after good partials; keep what the user saw.
        if (text.isNotBlank()) state = state.copy(partialText = text.trim())
        commitPartial()
        return continueOrFinish(RESTART_DELAY_MILLIS)
    }

    fun onFailure(failure: Failure): Action {
        commitPartial()
        if (!state.isActive) return continueOrFinish(0)

        val delay = failure.retryDelayMillis
        if (failure.error == null) {
            retries = 0
            return continueOrFinish(checkNotNull(delay))
        }
        if (delay == null || retries >= maxRetries) {
            fail(failure.error)
            return Action.None
        }
        retries++
        return continueOrFinish(delay)
    }

    private fun continueOrFinish(delayMillis: Long): Action = when {
        state.isActive -> Action.Listen(delayMillis)
        state.status == SpeechStatus.STOPPING -> {
            state = state.copy(status = SpeechStatus.IDLE, rmsDb = 0f)
            Action.None
        }
        else -> Action.None
    }

    private fun commitPartial() {
        state = state.copy(finalText = state.transcript, partialText = "")
    }

    companion object {
        const val RESTART_DELAY_MILLIS = 100L
    }
}
