package id.tunarasa.ai.gesture.pipeline

import id.tunarasa.ai.gesture.SibiConfig

/**
 * Decides when a letter has been held steadily enough to commit it.
 *
 * Ported from `validateTemporalConsistency` in `gesture-recognition.tsx`. The web version reads React state from
 * the previous render, so it judges a frame by history that excludes that frame; here the current frame counts.
 * Not thread-safe.
 */
class TemporalValidator(private val config: SibiConfig = SibiConfig()) {
    private val letters = ArrayDeque<Char>()
    private val confidences = ArrayDeque<Float>()
    private var windowStart: Long? = null

    /** Returns the averaged confidence when [letter] should be committed, otherwise null. */
    fun add(letter: Char, confidence: Float, timestampMillis: Long): Float? {
        val start = windowStart
        if (start == null || timestampMillis - start > config.temporalValidationWindowMillis) {
            reset()
            windowStart = timestampMillis
        }
        letters.addLast(letter)
        if (letters.size > config.minStableFrames) letters.removeFirst()
        confidences.addLast(confidence)
        if (confidences.size > config.confidenceAveragingWindow) confidences.removeFirst()

        val recent = confidences.takeLast(config.minStableFrames)
        val average = confidences.average().toFloat()
        val accepted = letters.size >= config.minStableFrames &&
            letters.all { it == letter } &&
            recent.max() - recent.min() <= config.maxConfidenceVariation &&
            average >= config.confidenceThreshold

        if (!accepted) return null
        reset()
        return average
    }

    fun reset() {
        letters.clear()
        confidences.clear()
        windowStart = null
    }
}
