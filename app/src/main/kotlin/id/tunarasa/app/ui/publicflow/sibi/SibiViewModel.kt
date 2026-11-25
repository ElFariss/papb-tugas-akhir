package id.tunarasa.app.ui.publicflow.sibi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.tunarasa.ai.gesture.GestureRecognizer
import id.tunarasa.ai.gesture.mediapipe.MediaPipeGestureRecognizer
import kotlinx.coroutines.launch
// Tekan Alt+Enter di Android Studio untuk auto-import class berikut dari modul :ai-client:
// GestureRecognizer, MediaPipeGestureRecognizer, RecognizerStatus

class SibiViewModel(app: Application) : AndroidViewModel(app) {
    val recognizer: GestureRecognizer = MediaPipeGestureRecognizer(app)

    init {
        viewModelScope.launch {
            recognizer.initialize()
        }
    }

    fun appendSpace() {
        recognizer.appendSpace()
    }

    fun removeLastLetter() {
        recognizer.removeLastLetter()
    }

    fun resetWord() {
        recognizer.clearWord()
    }

    override fun onCleared() {
        recognizer.close()
        super.onCleared()
    }
}
