package id.tunarasa.ai.benchmark

import id.tunarasa.ai.Fixtures
import id.tunarasa.ai.gesture.fingerpose.GestureClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * A-Z benchmark.
 *
 * The synthetic set only measures whether the sign definitions can be told apart: its hands are built from those
 * same definitions. **It is not recognition accuracy on people.** For that, put captures in
 * `ai-client/benchmark-data/` (see README).
 */
class SibiBenchmarkTest {
    private val classifier = GestureClassifier()
    private fun run(hands: List<LabelledHand>) =
        BenchmarkReport.run(hands) { classifier.classify(it).firstOrNull()?.letter }

    @Test
    fun `synthetic alphabet does not regress`() {
        val report = run(Fixtures.syntheticAlphabet.map { LabelledHand(it.intended, it.landmarks) })
        publish("synthetic", report.toMarkdown("Synthetic separability (NOT real accuracy)"))

        assertEquals(260, report.total)
        assertTrue(report.missingLetters.isEmpty())
        assertEquals("Same score as the web implementation", 233.0 / 260, report.accuracy, 1e-9)
        // S ties with N and loses on order: documented in README. Update this when the definitions are fixed.
        assertEquals(setOf('S'), report.recall.filterValues { it == 0.0 }.keys)
    }

    @Test
    fun `real hands`() {
        val files = File(System.getProperty("sibi.benchmark.dir").orEmpty())
            .listFiles { file -> file.extension == "csv" }.orEmpty()
        assumeTrue("No captures in ai-client/benchmark-data; real accuracy not measured", files.isNotEmpty())
        val hands = files.sorted().flatMap { it.readLines() }.filter { it.isNotBlank() }.map(LabelledHand::parse)
        val report = run(hands)
        publish("real", report.toMarkdown("Real hands"))
        val minimum = System.getProperty("sibi.benchmark.min")?.toDouble() ?: 0.0
        assertTrue("Accuracy ${report.accuracy} < $minimum", report.accuracy >= minimum)
    }

    @Test
    fun `report arithmetic`() {
        val hand = Fixtures.handOf('A')
        val answers = ArrayDeque(listOf<Char?>('A', 'B', 'B', null))
        val report = BenchmarkReport.run("AABB".map { LabelledHand(it, hand) }) { answers.removeFirst() }
        assertEquals(0.5, report.accuracy, 1e-9)
        assertEquals(mapOf('A' to 0.5, 'B' to 0.5), report.recall)
        assertEquals((2.0 / 3 + 0.5) / 2, report.macroF1, 1e-9)
    }

    @Test
    fun `bad rows are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { LabelledHand.parse("A,1,2") }
        assertThrows(IllegalArgumentException::class.java) { LabelledHand.parse("1," + "0,".repeat(62) + "0") }
    }

    private fun publish(name: String, markdown: String) {
        println(markdown)
        val report = System.getProperty("sibi.benchmark.report") ?: return
        File(report.replace(".md", "-$name.md")).apply { parentFile?.mkdirs() }.writeText(markdown)
    }
}
