package id.tunarasa.ai.gesture.pipeline

import id.tunarasa.ai.gesture.CommittedLetter
import id.tunarasa.ai.gesture.GestureResult
import id.tunarasa.ai.gesture.HandFrame
import id.tunarasa.ai.gesture.SibiConfig
import id.tunarasa.ai.gesture.fingerpose.GestureClassifier

/**
 * Landmarks -> letter: classify, smooth, validate. Free of Android and MediaPipe so it can be unit tested.
 *
 * Thread-safe: frames arrive on MediaPipe's thread while [reset] is called from the main thread.
 */
class GesturePipeline(
    private val config: SibiConfig = SibiConfig(),
    private val classifier: GestureClassifier = GestureClassifier(scoreThreshold = config.scoreThreshold),
) {
    private val lock = Any()
    private val smoother = GestureSmoother(config.smoothingWindow, config.minSmoothingSamples)
    private val validator = TemporalValidator(config)

    data class Output(val result: GestureResult, val committed: CommittedLetter?)

    fun onHand(frame: HandFrame, nowMillis: Long = frame.timestampMillis): Output {
        val scores = classifier.classify(frame.landmarks)
        val best = scores.firstOrNull()

        val (smoothed, committed) = synchronized(lock) {
            val vote = smoother.add(FrameVote(best?.letter, best?.confidence ?: 0f))
            val letter = vote?.letter
            val accepted = if (letter != null) {
                validator.add(letter, vote.confidence, frame.timestampMillis)
            } else {
                // Unrecognised shape: the letter was not held continuously.
                validator.reset()
                null
            }
            vote to accepted?.let { CommittedLetter(checkNotNull(letter), it, frame.timestampMillis) }
        }

        return Output(
            GestureResult(
                letter = smoothed?.letter,
                confidence = committed?.confidence ?: smoothed?.confidence ?: 0f,
                alternatives = scores.filter { it.letter != smoothed?.letter }.take(config.maxAlternatives),
                committed = committed != null,
                hand = frame,
                timestampMillis = frame.timestampMillis,
                processingMillis = nowMillis - frame.timestampMillis,
            ),
            committed,
        )
    }

    /** The hand left the frame: forget it, so the next sign is not blended with the previous one. */
    fun onNoHand(timestampMillis: Long): GestureResult {
        reset()
        return GestureResult(timestampMillis = timestampMillis)
    }

    fun reset() = synchronized(lock) {
        smoother.reset()
        validator.reset()
    }
}
