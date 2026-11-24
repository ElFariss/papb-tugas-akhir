package id.tunarasa.ai.gesture

/**
 * Tuning knobs for SIBI recognition.
 *
 * Defaults are ported from `frontend/src/lib/ai/config/sibi-config.ts`.
 */
data class SibiConfig(
    // Hand detection
    val maxNumHands: Int = 1,
    val minDetectionConfidence: Float = 0.7f,
    /** Mirror landmarks so they match the selfie view the sign definitions were authored in. */
    val mirrorHorizontally: Boolean = true,

    // Classification
    /** Minimum raw Fingerpose score (0..10 scale) for a letter to be considered at all. */
    val scoreThreshold: Double = 5.0,
    val maxAlternatives: Int = 3,

    // Smoothing
    val smoothingWindow: Int = 5,
    val minSmoothingSamples: Int = 4,
    val debounceMillis: Long = 150,

    // Temporal validation (committing a letter)
    val confidenceThreshold: Float = 0.75f,
    val minStableFrames: Int = 6,
    val confidenceAveragingWindow: Int = 8,
    val maxConfidenceVariation: Float = 0.12f,
    val temporalValidationWindowMillis: Long = 1_500,

    // Word formation
    val maxWordLength: Int = 50,
) {
    init {
        require(maxNumHands >= 1)
        require(smoothingWindow >= minSmoothingSamples && minSmoothingSamples >= 1)
        require(minStableFrames >= 1 && confidenceAveragingWindow >= minStableFrames)
        require(debounceMillis >= 0)
        // Otherwise the window always expires before enough frames arrive and nothing is ever committed.
        require(debounceMillis * (minStableFrames - 1) < temporalValidationWindowMillis) {
            "minStableFrames cannot fit inside temporalValidationWindowMillis at this debounce"
        }
    }

    companion object {
        val ALPHABET: List<Char> = ('A'..'Z').toList()
    }
}
