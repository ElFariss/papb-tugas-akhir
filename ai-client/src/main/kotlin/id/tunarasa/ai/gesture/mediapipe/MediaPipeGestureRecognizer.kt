package id.tunarasa.ai.gesture.mediapipe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.annotation.MainThread
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import id.tunarasa.ai.gesture.CameraLens
import id.tunarasa.ai.gesture.CommittedLetter
import id.tunarasa.ai.gesture.GestureError
import id.tunarasa.ai.gesture.GestureRecognizer
import id.tunarasa.ai.gesture.GestureResult
import id.tunarasa.ai.gesture.HandFrame
import id.tunarasa.ai.gesture.RecognizerStatus
import id.tunarasa.ai.gesture.SibiConfig
import id.tunarasa.ai.gesture.WordState
import id.tunarasa.ai.gesture.pipeline.GesturePipeline
import id.tunarasa.ai.gesture.pipeline.WordBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** CameraX + MediaPipe implementation of [GestureRecognizer]. */
class MediaPipeGestureRecognizer(
    context: Context,
    override val config: SibiConfig = SibiConfig(),
) : GestureRecognizer {

    private val appContext = context.applicationContext
    private val pipeline = GesturePipeline(config)
    private val wordBuilder = WordBuilder(config.maxWordLength)

    /** Owns [detector]: it is created, used and closed only on this thread. */
    private val analysisExecutor = Executors.newSingleThreadExecutor { Thread(it, "sibi-analysis") }
    private val analysisDispatcher = analysisExecutor.asCoroutineDispatcher()
    private var detector: HandLandmarkDetector? = null
    private var lastFrameMillis = 0L

    // Main thread only.
    private var cameraProvider: ProcessCameraProvider? = null
    private var boundUseCases: List<UseCase> = emptyList()
    private var startRequest = 0

    @Volatile
    private var running = false

    @Volatile
    private var closed = false

    private val _status = MutableStateFlow<RecognizerStatus>(RecognizerStatus.Uninitialized)
    override val status: StateFlow<RecognizerStatus> = _status.asStateFlow()

    private val _result = MutableStateFlow(GestureResult())
    override val result: StateFlow<GestureResult> = _result.asStateFlow()

    override val word: StateFlow<WordState> = wordBuilder.state

    private val _committedLetters = MutableSharedFlow<CommittedLetter>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val committedLetters: SharedFlow<CommittedLetter> = _committedLetters.asSharedFlow()

    override suspend fun initialize() {
        check(!closed) { "Recognizer is closed" }
        withContext(analysisDispatcher) {
            if (detector != null) return@withContext
            _status.value = RecognizerStatus.Initializing
            try {
                detector = HandLandmarkDetector(appContext, config, ::onFrame, ::onDetectionError)
                _status.value = RecognizerStatus.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Throwable: a missing native library surfaces as UnsatisfiedLinkError.
                fail(GestureError.MODEL_LOAD_FAILED, e)
            }
        }
    }

    override suspend fun start(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider?,
        lens: CameraLens,
    ) = withContext(Dispatchers.Main.immediate) {
        check(!closed) { "Recognizer is closed" }
        check(withContext(analysisDispatcher) { detector != null }) { "Call initialize() first" }

        val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            fail(GestureError.CAMERA_PERMISSION_DENIED)
            return@withContext
        }

        val request = ++startRequest
        try {
            val provider = ProcessCameraProvider.awaitInstance(appContext)
            // stop(), close() or a newer start() happened while waiting for the camera service.
            if (request != startRequest || closed) return@withContext
            unbind()

            val resolution = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .apply { setAnalyzer(analysisExecutor, ::analyze) }
            val preview = surfaceProvider?.let { target ->
                Preview.Builder().setResolutionSelector(resolution).build().apply { this.surfaceProvider = target }
            }
            val selector = when (lens) {
                CameraLens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                CameraLens.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            }

            val useCases = listOfNotNull(preview, analysis)
            pipeline.reset()
            provider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
            cameraProvider = provider
            boundUseCases = useCases
            running = true
            _status.value = RecognizerStatus.Running
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unbind()
            fail(GestureError.CAMERA_UNAVAILABLE, e)
        }
    }

    @MainThread
    override fun stop() {
        startRequest++
        unbind()
        pipeline.reset()
        _result.value = GestureResult()
        if (_status.value == RecognizerStatus.Running) _status.value = RecognizerStatus.Ready
    }

    override fun appendSpace() = wordBuilder.appendSpace(SystemClock.uptimeMillis())

    override fun removeLastLetter() = wordBuilder.removeLast()

    override fun clearWord() = wordBuilder.clear()

    @MainThread
    override fun close() {
        if (closed) return
        stop()
        closed = true
        _status.value = RecognizerStatus.Closed
        analysisExecutor.execute {
            detector?.close()
            detector = null
        }
        analysisExecutor.shutdown()
    }

    private fun unbind() {
        running = false
        if (boundUseCases.isNotEmpty()) cameraProvider?.unbind(*boundUseCases.toTypedArray())
        boundUseCases = emptyList()
    }

    /** Runs on [analysisExecutor]. */
    private fun analyze(image: ImageProxy) = image.use {
        val now = SystemClock.uptimeMillis()
        // The strict comparison also guarantees MediaPipe's increasing timestamps when debounce is 0.
        if (!running || now <= lastFrameMillis || now - lastFrameMillis < config.debounceMillis) return@use
        lastFrameMillis = now
        try {
            detector?.detect(image.toUprightBitmap(), now)
        } catch (e: RuntimeException) {
            onDetectionError(e)
        }
    }

    /** Runs on MediaPipe's thread. */
    private fun onFrame(hand: HandFrame?, timestampMillis: Long) {
        if (!running) return
        if (hand == null) {
            _result.value = pipeline.onNoHand(timestampMillis)
            return
        }
        val (result, committed) = pipeline.onHand(hand, nowMillis = SystemClock.uptimeMillis())
        if (committed != null && wordBuilder.append(committed)) {
            _committedLetters.tryEmit(committed)
            _result.value = result
        } else {
            // Word is full: do not tell the UI a letter was added.
            _result.value = result.copy(committed = false)
        }
    }

    /** One bad frame is not fatal; recognition continues with the next one. */
    private fun onDetectionError(error: Throwable) {
        Log.w(TAG, "Skipping frame", error)
    }

    private fun fail(error: GestureError, cause: Throwable? = null) {
        Log.e(TAG, "Gesture recognition failed: $error", cause)
        if (!closed) _status.value = RecognizerStatus.Failed(error, cause)
    }

    private fun ImageProxy.toUprightBitmap(): Bitmap {
        val bitmap = toBitmap()
        val degrees = imageInfo.rotationDegrees
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val TAG = "SibiRecognizer"
        val ANALYSIS_SIZE = Size(640, 480)
    }
}
