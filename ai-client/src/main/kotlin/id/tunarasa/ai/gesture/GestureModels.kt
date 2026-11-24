package id.tunarasa.ai.gesture

/** One of the 21 hand landmarks, in pixels. `z` is depth relative to the wrist on the same scale as `x`. */
data class HandLandmark(val x: Double, val y: Double, val z: Double)

/** A single detected hand, ready for classification. */
data class HandFrame(
    val landmarks: List<HandLandmark>,
    /** Upright image size the landmarks are expressed in. */
    val imageWidth: Int,
    val imageHeight: Int,
    /** True when `x` was flipped relative to the camera image. */
    val mirrored: Boolean,
    val detectionConfidence: Float,
    val timestampMillis: Long,
) {
    init {
        require(landmarks.size == LANDMARK_COUNT) { "Expected $LANDMARK_COUNT landmarks, got ${landmarks.size}" }
    }

    companion object {
        const val LANDMARK_COUNT = 21

        /** Joint chains for drawing an overlay. */
        val CONNECTIONS: List<List<Int>> = listOf(
            listOf(0, 1, 2, 3, 4),
            listOf(0, 5, 6, 7, 8),
            listOf(0, 9, 10, 11, 12),
            listOf(0, 13, 14, 15, 16),
            listOf(0, 17, 18, 19, 20),
        )
    }
}

data class LetterScore(
    val letter: Char,
    /** 0..1: share of the sign's maximum possible score that was matched. */
    val confidence: Float,
    /** Unnormalised Fingerpose score (0..10). */
    val rawScore: Double,
)

/** What the recogniser currently sees. */
data class GestureResult(
    /** Smoothed letter, or null when there is no hand, no match, or not enough frames yet. */
    val letter: Char? = null,
    val confidence: Float = 0f,
    val alternatives: List<LetterScore> = emptyList(),
    /** True only on the frame where [letter] passed temporal validation and was appended to the word. */
    val committed: Boolean = false,
    /** Landmarks for an overlay; null when no hand is visible. */
    val hand: HandFrame? = null,
    val timestampMillis: Long = 0,
    /** Camera frame to result latency. */
    val processingMillis: Long = 0,
) {
    val handVisible: Boolean get() = hand != null
}

data class CommittedLetter(val letter: Char, val confidence: Float, val timestampMillis: Long)

/** The word being spelled. */
data class WordState(val letters: List<CommittedLetter> = emptyList()) {
    val text: String get() = letters.map { it.letter }.joinToString("")
    val isEmpty: Boolean get() = letters.isEmpty()

    /** Mean confidence of the signed letters; null if nothing was signed. */
    val averageConfidence: Float?
        get() = letters.filter { it.letter != SPACE }
            .takeIf { it.isNotEmpty() }
            ?.map { it.confidence }?.average()?.toFloat()

    companion object {
        const val SPACE = ' '
    }
}

sealed interface RecognizerStatus {
    data object Uninitialized : RecognizerStatus
    data object Initializing : RecognizerStatus
    data object Ready : RecognizerStatus
    data object Running : RecognizerStatus
    data object Closed : RecognizerStatus
    data class Failed(val error: GestureError, val cause: Throwable? = null) : RecognizerStatus
}

enum class GestureError { MODEL_LOAD_FAILED, CAMERA_PERMISSION_DENIED, CAMERA_UNAVAILABLE, DETECTION_FAILED }
