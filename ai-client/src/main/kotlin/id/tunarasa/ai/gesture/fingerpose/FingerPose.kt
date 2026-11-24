package id.tunarasa.ai.gesture.fingerpose

import id.tunarasa.ai.gesture.HandLandmark

// Kotlin port of fingerpose 0.1.0 (MIT, (c) 2020 Andreas Schallwig). See THIRD_PARTY_NOTICES.md.

/** [points] are landmark indexes from palm to tip. */
enum class Finger(val points: List<Int>) {
    THUMB(listOf(0, 1, 2, 3, 4)),
    INDEX(listOf(0, 5, 6, 7, 8)),
    MIDDLE(listOf(0, 9, 10, 11, 12)),
    RING(listOf(0, 13, 14, 15, 16)),
    PINKY(listOf(0, 17, 18, 19, 20)),
}

enum class FingerCurl { NO_CURL, HALF_CURL, FULL_CURL }

enum class FingerDirection {
    VERTICAL_UP,
    VERTICAL_DOWN,
    HORIZONTAL_LEFT,
    HORIZONTAL_RIGHT,
    DIAGONAL_UP_RIGHT,
    DIAGONAL_UP_LEFT,
    DIAGONAL_DOWN_RIGHT,
    DIAGONAL_DOWN_LEFT,
}

/** Curl and direction of every finger. */
data class HandPose(
    val curls: Map<Finger, FingerCurl>,
    val directions: Map<Finger, FingerDirection>,
)

fun interface HandPoseEstimator {
    fun estimate(landmarks: List<HandLandmark>): HandPose
}
