package id.tunarasa.ai.gesture.pipeline

/** A per-frame classification. `letter` is null when nothing matched. */
data class FrameVote(val letter: Char?, val confidence: Float)

/** Majority vote over the last [window] frames. Not thread-safe. */
class GestureSmoother(private val window: Int = 5, private val minSamples: Int = 4) {
    private val buffer = ArrayDeque<FrameVote>()

    /** Returns null until [minSamples] frames were seen. */
    fun add(vote: FrameVote): FrameVote? {
        buffer.addLast(vote)
        if (buffer.size > window) buffer.removeFirst()
        if (buffer.size < minSamples) return null

        // groupBy keeps first-seen order and maxBy returns the first maximum, so ties match the web app.
        val (letter, votes) = buffer.groupBy { it.letter }.maxBy { it.value.size }
        return FrameVote(letter, votes.map { it.confidence }.average().toFloat())
    }

    fun reset() = buffer.clear()
}
