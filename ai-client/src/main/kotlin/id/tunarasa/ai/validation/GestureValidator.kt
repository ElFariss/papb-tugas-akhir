package id.tunarasa.ai.validation

/** Outcome of comparing recognised text with the ground truth. */
data class GestureValidation(
    val isCorrect: Boolean,
    val similarity: Double,
    val accuracyScore: Double,
    val match: GestureGroundTruth?,
)

/**
 * Scores recognised text against known phrases. Ported from `gesture_validation_service.py`;
 * `GestureValidatorParityTest` checks it against the Python original.
 */
class GestureValidator(private val groundTruth: List<GestureGroundTruth> = GroundTruthDataset.ALL) {

    fun validate(predictedText: String, gestureConfidence: Double): GestureValidation {
        if (predictedText.isEmpty()) return GestureValidation(false, 0.0, 0.0, null)

        var best: GestureGroundTruth? = null
        var bestSimilarity = 0.0
        for (candidate in groundTruth) {
            val similarity = (candidate.variations + candidate.expectedText)
                .maxOf { similarity(predictedText, it) }
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                best = candidate
            }
        }

        val confident = gestureConfidence >= (best?.confidenceThreshold ?: DEFAULT_CONFIDENCE_THRESHOLD)
        return GestureValidation(
            isCorrect = bestSimilarity >= SIMILARITY_THRESHOLD && confident,
            similarity = bestSimilarity,
            accuracyScore = bestSimilarity * 0.8 + gestureConfidence * 0.2,
            match = best,
        )
    }

    companion object {
        const val SIMILARITY_THRESHOLD = 0.6
        private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.7
        private val WHITESPACE = Regex("""\s+""")

        /** 70% word overlap (Jaccard) + 30% share of characters of [predicted] that occur in [expected]. */
        fun similarity(predicted: String, expected: String): Double {
            val a = predicted.lowercase().trim()
            val b = expected.lowercase().trim()
            if (predicted.isEmpty() || expected.isEmpty()) return 0.0
            if (a == b) return 1.0

            val wordsA = a.split(WHITESPACE).filter { it.isNotEmpty() }.toSet()
            val wordsB = b.split(WHITESPACE).filter { it.isNotEmpty() }.toSet()
            if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0

            val jaccard = (wordsA intersect wordsB).size.toDouble() / (wordsA union wordsB).size
            val characters = a.count { it in b }.toDouble() / maxOf(a.length, b.length)
            return (jaccard * 0.7 + characters * 0.3).coerceAtMost(1.0)
        }
    }
}
