package id.tunarasa.ai.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import id.tunarasa.ai.speech.SpeechSession.Failure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [SpeechToTextEngine] on top of Android's [SpeechRecognizer]. */
@MainThread
class AndroidSpeechToTextEngine(
    context: Context,
    private val language: String = "id-ID",
) : SpeechToTextEngine {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val session = SpeechSession()
    private val _state = MutableStateFlow(session.state)
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var closed = false

    private val listenAgain = Runnable { listen() }
    private val stopTimeout = Runnable {
        recognizer?.cancel()
        update { forceIdle() }
    }

    override fun start() {
        check(!closed) { "Engine is closed" }
        if (session.state.isActive) return
        handler.removeCallbacks(stopTimeout)
        recognizer?.cancel()

        update { start() }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            update { fail(SpeechError.NOT_AVAILABLE) }
            return
        }
        val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            update { fail(SpeechError.PERMISSION_DENIED) }
            return
        }
        listen()
    }

    override fun stop() {
        if (!session.state.isActive) return
        handler.removeCallbacks(listenAgain)
        update { requestStop() }
        recognizer?.stopListening()
        // Between two utterances there is nothing to stop, so no callback would ever arrive.
        handler.postDelayed(stopTimeout, STOP_TIMEOUT_MILLIS)
    }

    override fun clearTranscript() = update { clearTranscript() }

    override fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacks(listenAgain)
        handler.removeCallbacks(stopTimeout)
        recognizer?.destroy()
        recognizer = null
        update { forceIdle() }
    }

    private fun listen() {
        if (closed || !session.state.isActive) return
        val active = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        active.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName),
        )
    }

    private fun perform(action: SpeechSession.Action) {
        _state.value = session.state
        if (!session.state.isActive) handler.removeCallbacks(stopTimeout)
        if (action is SpeechSession.Action.Listen) {
            handler.removeCallbacks(listenAgain)
            handler.postDelayed(listenAgain, action.delayMillis)
        }
    }

    private fun update(change: SpeechSession.() -> Unit) {
        session.change()
        _state.value = session.state
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = update { onReady() }
        override fun onRmsChanged(rmsdB: Float) = update { onRms(rmsdB) }
        override fun onPartialResults(partialResults: Bundle?) = update { onPartial(partialResults.bestText()) }
        override fun onResults(results: Bundle?) = perform(session.onFinal(results.bestText()))
        override fun onError(error: Int) = perform(session.onFailure(failureOf(error)))
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    internal companion object {
        const val STOP_TIMEOUT_MILLIS = 2_000L

        fun Bundle?.bestText(): String =
            this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

        fun failureOf(code: Int): Failure = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Failure.SILENCE
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
            -> Failure.BUSY
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Failure.NETWORK
            SpeechRecognizer.ERROR_AUDIO -> Failure.AUDIO
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Failure.PERMISSION
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            -> Failure.LANGUAGE
            else -> Failure.SERVICE
        }
    }
}
