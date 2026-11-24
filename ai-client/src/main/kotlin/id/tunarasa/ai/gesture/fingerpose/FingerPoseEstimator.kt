package id.tunarasa.ai.gesture.fingerpose

import id.tunarasa.ai.gesture.HandLandmark
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Landmarks -> curl and direction per finger.
 *
 * Deliberately mirrors fingerpose 0.1.0 branch for branch (including its quirks) so that
 * results stay identical to the web app; `FingerposeParityTest` enforces it.
 */
class FingerPoseEstimator(
    private val halfCurlStartLimit: Double = 60.0,
    private val noCurlStartLimit: Double = 130.0,
    private val distanceVotePower: Double = 1.1,
    private val singleAngleVotePower: Double = 0.9,
    private val totalAngleVotePower: Double = 1.6,
) : HandPoseEstimator {

    override fun estimate(landmarks: List<HandLandmark>): HandPose {
        require(landmarks.size == 21) { "Expected 21 landmarks, got ${landmarks.size}" }
        val curls = mutableMapOf<Finger, FingerCurl>()
        val directions = mutableMapOf<Finger, FingerDirection>()

        for (finger in Finger.entries) {
            // Start from the palm, except for the thumb which starts at its own base.
            val first = if (finger == Finger.THUMB) 1 else 0
            val start = landmarks[finger.points[first]]
            val mid = landmarks[finger.points[first + 2]]
            val end = landmarks[finger.points[4]]
            val slopes = (first until 4).map { i ->
                val a = landmarks[finger.points[i]]
                val b = landmarks[finger.points[i + 1]]
                slope(a.x, a.y, b.x, b.y)
            }
            curls[finger] = estimateCurl(start, mid, end)
            directions[finger] = estimateDirection(start, mid, end, slopes)
        }
        return HandPose(curls, directions)
    }

    private fun estimateCurl(start: HandLandmark, mid: HandLandmark, end: HandLandmark): FingerCurl {
        val startMid = distance3d(start, mid)
        val startEnd = distance3d(start, end)
        val midEnd = distance3d(mid, end)
        val cos = ((midEnd * midEnd + startMid * startMid - startEnd * startEnd) / (2 * midEnd * startMid))
            .coerceIn(-1.0, 1.0)
        val angle = (57.2958 * acos(cos)) % 180
        return when {
            angle > noCurlStartLimit -> FingerCurl.NO_CURL
            angle > halfCurlStartLimit -> FingerCurl.HALF_CURL
            else -> FingerCurl.FULL_CURL
        }
    }

    private fun estimateDirection(
        start: HandLandmark,
        mid: HandLandmark,
        end: HandLandmark,
        slopes: List<Double>,
    ): FingerDirection {
        val dx = Deltas(startMid = start.x - mid.x, startEnd = start.x - end.x, midEnd = mid.x - end.x)
        val dy = Deltas(startMid = start.y - mid.y, startEnd = start.y - end.y, midEnd = mid.y - end.y)

        val votes = Votes()
        val ratio = dy.max / (dx.max + 0.00001)
        when {
            ratio > 1.5 -> votes.vertical += distanceVotePower
            ratio > 0.66 -> votes.diagonal += distanceVotePower
            else -> votes.horizontal += distanceVotePower
        }

        val startMid = sqrt(dx.startMid * dx.startMid + dy.startMid * dy.startMid)
        val startEnd = sqrt(dx.startEnd * dx.startEnd + dy.startEnd * dy.startEnd)
        val midEnd = sqrt(dx.midEnd * dx.midEnd + dy.midEnd * dy.midEnd)
        val longest = max(startMid, max(startEnd, midEnd))
        // Upstream only ever moves the start point, so start->end is used even when start->mid is longest.
        val from = if (longest != startMid && longest == midEnd) mid else start
        votes.addAngle(slope(from.x, from.y, end.x, end.y), totalAngleVotePower)
        slopes.forEach { votes.addAngle(it, singleAngleVotePower) }

        // On ties: vertical, then horizontal, then diagonal.
        return when {
            votes.vertical >= max(votes.diagonal, votes.horizontal) ->
                if (dy.isUp()) FingerDirection.VERTICAL_UP else FingerDirection.VERTICAL_DOWN

            votes.horizontal >= votes.diagonal ->
                if (dx.isLeft()) FingerDirection.HORIZONTAL_LEFT else FingerDirection.HORIZONTAL_RIGHT

            dy.isUp() ->
                if (dx.isLeft()) FingerDirection.DIAGONAL_UP_LEFT else FingerDirection.DIAGONAL_UP_RIGHT

            else ->
                if (dx.isLeft()) FingerDirection.DIAGONAL_DOWN_LEFT else FingerDirection.DIAGONAL_DOWN_RIGHT
        }
    }

    private class Deltas(val startMid: Double, val startEnd: Double, val midEnd: Double) {
        val max: Double = max(abs(startMid), max(abs(startEnd), abs(midEnd)))

        private val dominant: Double
            get() = when (max) {
                abs(startEnd) -> startEnd
                abs(startMid) -> startMid
                else -> midEnd
            }

        fun isLeft(): Boolean = dominant > 0
        fun isUp(): Boolean = dominant >= 0
    }

    private class Votes {
        var vertical = 0.0
        var diagonal = 0.0
        var horizontal = 0.0

        fun addAngle(angle: Double, weight: Double) {
            when {
                angle in 75.0..105.0 -> vertical += weight
                angle in 25.0..155.0 -> diagonal += weight
                else -> horizontal += weight
            }
        }
    }

    private companion object {
        fun distance3d(a: HandLandmark, b: HandLandmark): Double {
            val dx = a.x - b.x
            val dy = a.y - b.y
            val dz = a.z - b.z
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

        /** Angle of the line in degrees, 0..180, with image y pointing down. */
        fun slope(x1: Double, y1: Double, x2: Double, y2: Double): Double {
            val degrees = atan((y1 - y2) / (x1 - x2)) * 180 / Math.PI
            return if (degrees <= 0) -degrees else 180 - degrees
        }
    }
}
