package id.tunarasa.ai.gesture

import id.tunarasa.ai.Fixtures
import id.tunarasa.ai.gesture.fingerpose.GestureClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureClassifierTest {
    private val classifier = GestureClassifier()

    @Test
    fun `confidence is normalised, not clamped to one`() {
        val scores = Fixtures.syntheticAlphabet.flatMap { classifier.classify(it.landmarks) }
        assertTrue(scores.all { it.confidence in 0f..1f })
        assertTrue("partial matches must score below 1", scores.any { it.confidence < 0.9f })
    }

    @Test
    fun `results are ranked and above the threshold`() {
        val scores = classifier.classify(Fixtures.handOf('B'))
        assertEquals('B', scores.first().letter)
        assertEquals(scores.map { it.rawScore }.sortedDescending(), scores.map { it.rawScore })
        assertTrue(scores.all { it.rawScore >= 5.0 })
    }

    @Test
    fun `nothing matches above an impossible threshold`() {
        assertTrue(GestureClassifier(scoreThreshold = 10.1).classify(Fixtures.handOf('B')).isEmpty())
    }
}
