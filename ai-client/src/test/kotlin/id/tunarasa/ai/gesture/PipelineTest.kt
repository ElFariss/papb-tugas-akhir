package id.tunarasa.ai.gesture

import id.tunarasa.ai.Fixtures
import id.tunarasa.ai.gesture.pipeline.FrameVote
import id.tunarasa.ai.gesture.pipeline.GesturePipeline
import id.tunarasa.ai.gesture.pipeline.GestureSmoother
import id.tunarasa.ai.gesture.pipeline.LandmarkMapper
import id.tunarasa.ai.gesture.pipeline.TemporalValidator
import id.tunarasa.ai.gesture.pipeline.WordBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineTest {

    // --- smoother

    @Test
    fun `smoother waits for four samples then takes the majority`() {
        val smoother = GestureSmoother()
        assertNull(smoother.add(FrameVote('A', 0.9f)))
        assertNull(smoother.add(FrameVote('B', 0.5f)))
        assertNull(smoother.add(FrameVote('A', 0.7f)))
        val vote = checkNotNull(smoother.add(FrameVote('A', 0.8f)))
        assertEquals('A', vote.letter)
        assertEquals(0.8f, vote.confidence, 1e-6f)
    }

    @Test
    fun `smoother forgets frames outside the window`() {
        val smoother = GestureSmoother()
        repeat(5) { smoother.add(FrameVote('A', 1f)) }
        repeat(2) { smoother.add(FrameVote('B', 1f)) }
        assertEquals('B', smoother.add(FrameVote('B', 1f))?.letter)
    }

    // --- validator

    private fun TemporalValidator.feed(letters: String, confidence: Float = 0.9f, step: Long = 150, from: Long = 0) =
        letters.mapIndexed { i, letter -> add(letter, confidence, from + i * step) }

    @Test
    fun `validator commits on the sixth stable frame and starts over`() {
        val validator = TemporalValidator()
        val outcomes = validator.feed("AAAAAAAAAAAA")
        assertEquals(listOf(5, 11), outcomes.indices.filter { outcomes[it] != null })
        assertEquals(0.9f, outcomes[5]!!, 1e-6f)
    }

    @Test
    fun `validator rejects a changing letter`() {
        assertTrue(TemporalValidator().feed("AABAAB").all { it == null })
    }

    @Test
    fun `validator rejects low confidence`() {
        assertTrue(TemporalValidator().feed("AAAAAA", confidence = 0.74f).all { it == null })
    }

    @Test
    fun `validator rejects unstable confidence`() {
        val validator = TemporalValidator()
        val outcomes = (0 until 6).map { validator.add('A', if (it == 3) 0.8f else 0.95f, it * 150L) }
        assertTrue(outcomes.all { it == null })
    }

    @Test
    fun `validator rejects frames spread over more than the window`() {
        assertTrue(TemporalValidator().feed("AAAAAA", step = 400).all { it == null })
    }

    @Test
    fun `config that can never commit is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SibiConfig(debounceMillis = 300) }
    }

    // --- whole pipeline

    private class Harness(config: SibiConfig = SibiConfig()) {
        val pipeline = GesturePipeline(config)
        var now = 0L

        fun hold(letter: Char, frames: Int): List<GesturePipeline.Output> =
            List(frames) { pipeline.onHand(Fixtures.frame(Fixtures.handOf(letter), now)).also { now += 150 } }

        fun noHand() = pipeline.onNoHand(now).also { now += 150 }
    }

    @Test
    fun `holding a sign commits it once after nine frames`() {
        val outputs = Harness().hold('B', 9)
        assertEquals(listOf(8), outputs.indices.filter { outputs[it].committed != null })
        assertEquals('B', outputs[8].committed?.letter)
        assertTrue(outputs[8].result.committed)
        assertEquals(listOf(null, null, null, 'B'), outputs.take(4).map { it.result.letter })
    }

    @Test
    fun `removing the hand discards progress`() {
        val harness = Harness()
        harness.hold('B', 8)
        val empty = harness.noHand()
        assertNull(empty.letter)
        assertFalse(empty.handVisible)
        assertTrue(harness.hold('B', 8).all { it.committed == null })
        assertNotNull(harness.hold('B', 1).single().committed)
    }

    @Test
    fun `switching signs commits both`() {
        val harness = Harness()
        val letters = (harness.hold('B', 10) + harness.hold('L', 10)).mapNotNull { it.committed?.letter }
        assertEquals("BL", letters.joinToString(""))
    }

    @Test
    fun `an unrecognised shape is never committed`() {
        val harness = Harness(SibiConfig(scoreThreshold = 10.1))
        val outputs = harness.hold('B', 20)
        assertTrue(outputs.all { it.committed == null && it.result.letter == null && it.result.handVisible })
    }

    @Test
    fun `alternatives exclude the shown letter and respect the limit`() {
        val result = Harness().hold('A', 5).last().result
        assertTrue(result.alternatives.size <= 3)
        assertTrue(result.alternatives.none { it.letter == result.letter })
    }

    // --- landmarks

    @Test
    fun `landmarks are scaled to pixels and mirrored`() {
        assertEquals(HandLandmark(160.0, 240.0, -64.0), LandmarkMapper.toPixels(0.25f, 0.5f, -0.1f, 640, 480, false).rounded())
        assertEquals(HandLandmark(480.0, 240.0, -64.0), LandmarkMapper.toPixels(0.25f, 0.5f, -0.1f, 640, 480, true).rounded())
    }

    private fun HandLandmark.rounded() =
        HandLandmark(Math.round(x).toDouble(), Math.round(y).toDouble(), Math.round(z).toDouble())

    // --- word

    private fun letter(c: Char, confidence: Float = 0.9f) = CommittedLetter(c, confidence, 0)

    @Test
    fun `word supports append, space, backspace and clear`() {
        val word = WordBuilder()
        word.appendSpace(0)
        assertTrue("no leading space", word.state.value.isEmpty)
        word.append(letter('K', 0.8f))
        word.append(letter('T', 1f))
        word.appendSpace(0)
        word.appendSpace(0)
        word.append(letter('P', 0.9f))
        assertEquals("KT P", word.state.value.text)
        assertEquals("spaces do not count", 0.9f, word.state.value.averageConfidence!!, 1e-6f)
        word.removeLast()
        word.removeLast()
        assertEquals("KT", word.state.value.text)
        word.clear()
        word.removeLast()
        assertNull(word.state.value.averageConfidence)
    }

    @Test
    fun `word stops at its maximum length`() {
        val word = WordBuilder(maxLength = 2)
        assertTrue(word.append(letter('A')))
        assertTrue(word.append(letter('B')))
        assertFalse(word.append(letter('C')))
        assertEquals("AB", word.state.value.text)
    }
}
