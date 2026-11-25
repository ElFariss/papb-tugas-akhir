package id.tunarasa.app.ui.publicflow.stt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import id.tunarasa.ai.speech.AndroidSpeechToTextEngine
import id.tunarasa.ai.speech.SpeechToTextEngine

class SpeechToTextViewModel(app: Application) : AndroidViewModel(app) {
    val engine: SpeechToTextEngine = AndroidSpeechToTextEngine(app)

    fun toggleListening() {
        if (engine.state.value.isActive) {
            engine.stop()
        } else {
            engine.start()
        }
    }

    fun stopListening() {
        if (engine.state.value.isActive) {
            engine.stop()
        }
    }

    fun clearTranscript() {
        engine.clearTranscript()
    }

    override fun onCleared() {
        engine.close()
        super.onCleared()
    }
}
