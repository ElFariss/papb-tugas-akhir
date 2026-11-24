package id.tunarasa.ai.gesture

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * On-device SIBI A-Z recogniser. **Public contract consumed by FE.**
 *
 * Lifecycle: [initialize] -> [start] <-> [stop] -> [close]. All methods are main-thread safe.
 * The caller must hold `android.permission.CAMERA` before [start].
 */
interface GestureRecognizer {
    val config: SibiConfig
    val status: StateFlow<RecognizerStatus>

    /** Conflated: renders the latest frame. Use [committedLetters] to react to every accepted letter. */
    val result: StateFlow<GestureResult>
    val word: StateFlow<WordState>
    val committedLetters: SharedFlow<CommittedLetter>

    /** Loads the model off the main thread. Safe to call again after a failure. */
    suspend fun initialize()

    /**
     * Opens the camera and starts recognising.
     *
     * @param surfaceProvider where to draw the preview (`PreviewView.surfaceProvider`), or null for no preview.
     */
    suspend fun start(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider? = null,
        lens: CameraLens = CameraLens.FRONT,
    )

    /** Releases the camera and clears the temporal state. The word is kept. */
    fun stop()

    fun appendSpace()
    fun removeLastLetter()
    fun clearWord()

    /** Releases everything. The instance cannot be reused. */
    fun close()
}

enum class CameraLens { FRONT, BACK }
