package id.tunarasa.ai.gesture

import id.tunarasa.ai.Fixtures
import id.tunarasa.ai.gesture.fingerpose.Finger
import id.tunarasa.ai.gesture.fingerpose.FingerCurl
import id.tunarasa.ai.gesture.fingerpose.FingerDirection
import id.tunarasa.ai.gesture.fingerpose.FingerPoseEstimator
import id.tunarasa.ai.gesture.fingerpose.GestureClassifier
import id.tunarasa.ai.gesture.fingerpose.SibiSigns
import org.junit.Assert.assertEquals
import org.junit.Test

/** The port must agree with the real fingerpose 0.1.0 + tunarasa sign files (see tools/generate-fixtures.mjs). */
class FingerposeParityTest {

    @Test
    fun `curls, directions and all 26 scores match the web implementation`() {
        val rows = Fixtures.lines("fingerpose_parity.csv")
        assertEquals(200, rows.size)
        rows.forEachIndexed { index, line ->
            val values = line.split(',').map(String::toDouble)
            val pose = FingerPoseEstimator().estimate(Fixtures.landmarks(values.take(63)))
            Finger.entries.forEachIndexed { f, finger ->
                assertEquals("row $index curl $finger", FingerCurl.entries[values[63 + f].toInt()], pose.curls[finger])
                assertEquals(
                    "row $index direction $finger",
                    FingerDirection.entries[values[68 + f].toInt()],
                    pose.directions[finger],
                )
            }
            SibiSigns.ALL.forEachIndexed { s, sign ->
                assertEquals("row $index score ${sign.letter}", values[73 + s], sign.score(pose), 1e-9)
            }
        }
    }

    @Test
    fun `selected letter matches the web implementation`() {
        val classifier = GestureClassifier()
        for (sample in Fixtures.syntheticAlphabet) {
            assertEquals(sample.webLetter, classifier.classify(sample.landmarks).firstOrNull()?.letter)
        }
    }

    @Test
    fun `signs cover A to Z in order`() {
        assertEquals(SibiConfig.ALPHABET, SibiSigns.ALL.map { it.letter })
    }

    @Test
    fun `degenerate hands do not crash`() {
        val collapsed = List(21) { HandLandmark(1.0, 1.0, 1.0) }
        FingerPoseEstimator().estimate(collapsed)
    }
}
