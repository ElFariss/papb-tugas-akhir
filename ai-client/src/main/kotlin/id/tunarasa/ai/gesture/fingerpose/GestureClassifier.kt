package id.tunarasa.ai.gesture.fingerpose

import id.tunarasa.ai.gesture.HandLandmark
import id.tunarasa.ai.gesture.LetterScore

/**
 * Scores a hand against every sign.
 *
 * Differs from the web app in one way: the web app clamps the raw 0..10 score to 1.0, so every match reports
 * 100% confidence and the 0.75 acceptance gate never rejects anything. Here confidence is the raw score divided
 * by the sign's maximum. Ranking still uses the raw score, so the chosen letter is unchanged.
 */
class GestureClassifier(
    private val signs: List<GestureDescription> = SibiSigns.ALL,
    private val scoreThreshold: Double = 5.0,
    private val estimator: HandPoseEstimator = FingerPoseEstimator(),
) {
    /** Letters at or above the threshold, best first. Ties keep alphabetical order, like the web app. */
    fun classify(landmarks: List<HandLandmark>): List<LetterScore> = classify(estimator.estimate(landmarks))

    fun classify(pose: HandPose): List<LetterScore> =
        signs.map { sign ->
            val raw = sign.score(pose)
            LetterScore(sign.letter, (raw / sign.maxScore).coerceIn(0.0, 1.0).toFloat(), raw)
        }
            .filter { it.rawScore >= scoreThreshold }
            .sortedByDescending { it.rawScore }
}
