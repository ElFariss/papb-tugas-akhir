package id.tunarasa.ai.gesture.fingerpose

/** Expected curls and directions of one sign, with a weight for each accepted value. */
class GestureDescription private constructor(
    val letter: Char,
    val curls: Map<Finger, List<Pair<FingerCurl, Double>>>,
    val directions: Map<Finger, List<Pair<FingerDirection, Double>>>,
) {
    private val parameterCount = curls.size + directions.size

    /** Score of a hand that matches every described finger with its best-weighted value. */
    val maxScore: Double =
        (curls.values.sumOf { it.bestWeight() } + directions.values.sumOf { it.bestWeight() }) / parameterCount * 10

    /**
     * Fingerpose score on a 0..10 scale.
     *
     * Fingerpose intends to subtract a penalty for mismatches, but its penalty is always zero
     * (it is only assigned on a match). That behaviour is kept for parity.
     */
    fun score(pose: HandPose): Double {
        var score = 0.0
        for ((finger, expected) in curls) {
            score += expected.firstOrNull { it.first == pose.curls[finger] }?.second ?: 0.0
        }
        for ((finger, expected) in directions) {
            score += expected.firstOrNull { it.first == pose.directions[finger] }?.second ?: 0.0
        }
        return score / parameterCount * 10
    }

    class Builder internal constructor(private val letter: Char) {
        private val curls = mutableMapOf<Finger, MutableList<Pair<FingerCurl, Double>>>()
        private val directions = mutableMapOf<Finger, MutableList<Pair<FingerDirection, Double>>>()

        fun curl(finger: Finger, curl: FingerCurl, weight: Double = 1.0) {
            curls.getOrPut(finger) { mutableListOf() } += curl to weight
        }

        fun direction(finger: Finger, direction: FingerDirection, weight: Double = 0.7) {
            directions.getOrPut(finger) { mutableListOf() } += direction to weight
        }

        internal fun build() = GestureDescription(letter, curls, directions)
    }

    companion object {
        fun of(letter: Char, block: Builder.() -> Unit): GestureDescription =
            Builder(letter).apply(block).build()

        private fun List<Pair<*, Double>>.bestWeight() = maxOf { it.second }
    }
}
