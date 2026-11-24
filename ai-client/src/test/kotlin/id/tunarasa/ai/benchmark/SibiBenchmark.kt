package id.tunarasa.ai.benchmark

import id.tunarasa.ai.gesture.HandLandmark
import id.tunarasa.ai.gesture.SibiConfig

/** One labelled hand. CSV: `letter,x0,y0,z0,...,x20,y20,z20` in mirrored pixel space. */
data class LabelledHand(val letter: Char, val landmarks: List<HandLandmark>) {
    companion object {
        fun parse(line: String): LabelledHand {
            val cells = line.split(',').map(String::trim)
            require(cells.size == 64) { "Expected 64 cells, got ${cells.size}" }
            val letter = cells[0].uppercase().singleOrNull()
            require(letter in SibiConfig.ALPHABET) { "Bad label '${cells[0]}'" }
            val landmarks = cells.drop(1).map(String::toDouble).chunked(3).map { (x, y, z) -> HandLandmark(x, y, z) }
            return LabelledHand(checkNotNull(letter), landmarks)
        }
    }
}

class BenchmarkReport(private val outcomes: List<Pair<Char, Char?>>) {
    val total = outcomes.size
    val accuracy: Double = outcomes.count { it.first == it.second }.toDouble() / total
    val recall: Map<Char, Double> = outcomes.groupBy { it.first }
        .mapValues { (letter, group) -> group.count { it.second == letter }.toDouble() / group.size }
    val missingLetters: List<Char> = SibiConfig.ALPHABET - recall.keys

    /** Mean F1 over the letters that have samples. */
    val macroF1: Double = recall.keys.map { letter ->
        val truePositives = outcomes.count { it.first == letter && it.second == letter }
        val predicted = outcomes.count { it.second == letter }
        val actual = outcomes.count { it.first == letter }
        2.0 * truePositives / (predicted + actual)
    }.average()

    fun toMarkdown(title: String): String = buildString {
        appendLine("## $title")
        appendLine()
        appendLine("Samples: $total | accuracy: %.1f%% | macro F1: %.3f".format(accuracy * 100, macroF1))
        if (missingLetters.isNotEmpty()) appendLine("\nNo samples for: ${missingLetters.joinToString()}")
        appendLine()
        appendLine("| Letter | Samples | Recall | Confused with |")
        appendLine("|---|---:|---:|---|")
        for ((letter, group) in outcomes.groupBy { it.first }.toSortedMap()) {
            val mistakes = group.filter { it.second != letter }
                .groupingBy { it.second?.toString() ?: "none" }.eachCount()
                .entries.sortedByDescending { it.value }.joinToString { "${it.key} (${it.value})" }
            appendLine("| $letter | ${group.size} | %.0f%% | $mistakes |".format(recall.getValue(letter) * 100))
        }
        appendLine()
    }

    companion object {
        fun run(hands: List<LabelledHand>, classify: (List<HandLandmark>) -> Char?): BenchmarkReport {
            require(hands.isNotEmpty()) { "No samples" }
            return BenchmarkReport(hands.map { it.letter to classify(it.landmarks) })
        }
    }
}
