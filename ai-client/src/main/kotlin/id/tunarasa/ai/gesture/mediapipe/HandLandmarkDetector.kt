package id.tunarasa.ai.gesture.mediapipe

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import id.tunarasa.ai.gesture.HandFrame
import id.tunarasa.ai.gesture.SibiConfig
import id.tunarasa.ai.gesture.pipeline.LandmarkMapper

/**
 * MediaPipe Hand Landmarker in live-stream mode. Create and call it off the main thread.
 *
 * @param onFrame called on MediaPipe's thread; the hand is null when none was found.
 */
internal class HandLandmarkDetector(
    context: Context,
    private val config: SibiConfig,
    private val onFrame: (hand: HandFrame?, timestampMillis: Long) -> Unit,
    private val onError: (Throwable) -> Unit,
) : AutoCloseable {

    private val landmarker: HandLandmarker = HandLandmarker.createFromOptions(
        context.applicationContext,
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(config.maxNumHands)
            .setMinHandDetectionConfidence(config.minDetectionConfidence)
            .setResultListener { result, image ->
                onFrame(result.toHandFrame(image.width, image.height), result.timestampMs())
            }
            .setErrorListener { onError(it) }
            .build(),
    )

    /** [uprightBitmap] must already be rotated. Timestamps must strictly increase. */
    fun detect(uprightBitmap: Bitmap, timestampMillis: Long) {
        landmarker.detectAsync(BitmapImageBuilder(uprightBitmap).build(), timestampMillis)
    }

    override fun close() = landmarker.close()

    private fun HandLandmarkerResult.toHandFrame(width: Int, height: Int): HandFrame? {
        val scores = landmarks().indices.map { i ->
            handedness().getOrNull(i)?.firstOrNull()?.score() ?: DEFAULT_CONFIDENCE
        }
        val best = scores.indices.maxByOrNull { scores[it] } ?: return null
        return HandFrame(
            landmarks = landmarks()[best].map {
                LandmarkMapper.toPixels(it.x(), it.y(), it.z(), width, height, config.mirrorHorizontally)
            },
            imageWidth = width,
            imageHeight = height,
            mirrored = config.mirrorHorizontally,
            detectionConfidence = scores[best],
            timestampMillis = timestampMs(),
        )
    }

    companion object {
        const val MODEL_ASSET = "hand_landmarker.task"
        private const val DEFAULT_CONFIDENCE = 0.8f
    }
}
