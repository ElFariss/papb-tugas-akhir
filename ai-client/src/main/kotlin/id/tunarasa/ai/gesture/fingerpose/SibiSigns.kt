package id.tunarasa.ai.gesture.fingerpose

import id.tunarasa.ai.gesture.fingerpose.Finger.INDEX
import id.tunarasa.ai.gesture.fingerpose.Finger.MIDDLE
import id.tunarasa.ai.gesture.fingerpose.Finger.PINKY
import id.tunarasa.ai.gesture.fingerpose.Finger.RING
import id.tunarasa.ai.gesture.fingerpose.Finger.THUMB
import id.tunarasa.ai.gesture.fingerpose.FingerCurl.FULL_CURL
import id.tunarasa.ai.gesture.fingerpose.FingerCurl.HALF_CURL
import id.tunarasa.ai.gesture.fingerpose.FingerCurl.NO_CURL
import id.tunarasa.ai.gesture.fingerpose.FingerDirection.DIAGONAL_DOWN_RIGHT
import id.tunarasa.ai.gesture.fingerpose.FingerDirection.DIAGONAL_UP_LEFT
import id.tunarasa.ai.gesture.fingerpose.FingerDirection.DIAGONAL_UP_RIGHT
import id.tunarasa.ai.gesture.fingerpose.FingerDirection.HORIZONTAL_LEFT
import id.tunarasa.ai.gesture.fingerpose.FingerDirection.HORIZONTAL_RIGHT
import id.tunarasa.ai.gesture.fingerpose.FingerDirection.VERTICAL_UP

/**
 * SIBI A-Z definitions, ported verbatim from `frontend/src/components/handsigns/Asign.ts`..`Zsign.ts`.
 *
 * Known upstream issue, kept for parity: 19 signs list the *thumb's* direction on `INDEX`, so the thumb
 * direction is ignored and the index finger accepts an extra one. Fix it only together with
 * real-hand benchmark numbers (see README, "Known limitations").
 */
object SibiSigns {
    val ALL: List<GestureDescription> = listOf(
        sign('A') {
            curl(THUMB, NO_CURL)
            direction(THUMB, DIAGONAL_UP_RIGHT)
            curl(INDEX, FULL_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, VERTICAL_UP)
        },
        sign('B') {
            curl(THUMB, HALF_CURL)
            direction(INDEX, DIAGONAL_UP_LEFT)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(INDEX, NO_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(MIDDLE, NO_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, NO_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, NO_CURL)
            direction(PINKY, VERTICAL_UP)
        },
        sign('C') {
            curl(THUMB, NO_CURL)
            direction(THUMB, DIAGONAL_UP_RIGHT)
            curl(INDEX, NO_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(MIDDLE, HALF_CURL)
            direction(MIDDLE, DIAGONAL_UP_RIGHT)
            curl(RING, HALF_CURL)
            direction(RING, DIAGONAL_UP_RIGHT)
            curl(PINKY, HALF_CURL)
            direction(PINKY, DIAGONAL_UP_RIGHT)
        },
        sign('D') {
            curl(THUMB, HALF_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(INDEX, NO_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, VERTICAL_UP)
        },
        sign('E') {
            curl(THUMB, HALF_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(INDEX, FULL_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, VERTICAL_UP)
        },
        sign('F') {
            curl(THUMB, HALF_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(INDEX, FULL_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(MIDDLE, NO_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, NO_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, NO_CURL)
            direction(PINKY, VERTICAL_UP)
        },
        sign('G') {
            curl(THUMB, NO_CURL)
            direction(THUMB, DIAGONAL_UP_RIGHT)
            curl(INDEX, NO_CURL)
            direction(INDEX, HORIZONTAL_RIGHT)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, DIAGONAL_UP_RIGHT)
            curl(RING, FULL_CURL)
            direction(RING, HORIZONTAL_RIGHT)
            curl(PINKY, FULL_CURL)
            direction(PINKY, HORIZONTAL_RIGHT)
        },
        sign('H') {
            curl(THUMB, NO_CURL)
            direction(THUMB, HORIZONTAL_RIGHT)
            curl(INDEX, NO_CURL)
            direction(INDEX, HORIZONTAL_RIGHT)
            curl(MIDDLE, NO_CURL)
            direction(MIDDLE, HORIZONTAL_RIGHT)
            curl(RING, FULL_CURL)
            direction(RING, HORIZONTAL_RIGHT)
            curl(PINKY, FULL_CURL)
            direction(PINKY, HORIZONTAL_RIGHT)
        },
        sign('I') {
            curl(THUMB, HALF_CURL)
            direction(INDEX, DIAGONAL_UP_LEFT)
            curl(INDEX, FULL_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, NO_CURL)
            direction(PINKY, VERTICAL_UP)
        },
        sign('J') {
            curl(THUMB, NO_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(INDEX, FULL_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, DIAGONAL_UP_RIGHT)
            curl(RING, FULL_CURL)
            direction(RING, HORIZONTAL_RIGHT)
            curl(PINKY, NO_CURL)
            direction(PINKY, HORIZONTAL_RIGHT)
        },
        sign('K') {
            curl(THUMB, NO_CURL)
            direction(INDEX, DIAGONAL_UP_LEFT)
            curl(INDEX, NO_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(MIDDLE, NO_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, VERTICAL_UP)
        },
        sign('L') {
            curl(THUMB, NO_CURL)
            direction(THUMB, DIAGONAL_UP_RIGHT)
            curl(INDEX, NO_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, VERTICAL_UP)
        },
        sign('M') {
            curl(THUMB, HALF_CURL)
            direction(INDEX, DIAGONAL_UP_LEFT)
            curl(INDEX, FULL_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, VERTICAL_UP)
        },
        sign('N') {
            curl(THUMB, HALF_CURL)
            direction(INDEX, DIAGONAL_UP_LEFT)
            curl(INDEX, FULL_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, DIAGONAL_UP_LEFT)
        },
        sign('O') {
            curl(THUMB, NO_CURL)
            direction(THUMB, DIAGONAL_UP_RIGHT)
            curl(INDEX, HALF_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(MIDDLE, HALF_CURL)
            direction(MIDDLE, DIAGONAL_UP_RIGHT)
            curl(RING, FULL_CURL)
            direction(RING, DIAGONAL_UP_RIGHT)
            curl(PINKY, FULL_CURL)
            direction(PINKY, DIAGONAL_UP_RIGHT)
        },
        sign('P') {
            curl(THUMB, NO_CURL)
            direction(INDEX, HORIZONTAL_RIGHT)
            curl(INDEX, NO_CURL)
            direction(INDEX, HORIZONTAL_RIGHT)
            curl(MIDDLE, HALF_CURL)
            direction(MIDDLE, DIAGONAL_DOWN_RIGHT)
            curl(RING, FULL_CURL)
            direction(RING, DIAGONAL_DOWN_RIGHT)
            curl(PINKY, FULL_CURL)
            direction(PINKY, DIAGONAL_DOWN_RIGHT)
        },
        sign('Q') {
            curl(THUMB, NO_CURL)
            direction(INDEX, DIAGONAL_DOWN_RIGHT)
            curl(INDEX, HALF_CURL)
            direction(INDEX, HORIZONTAL_RIGHT)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, HORIZONTAL_RIGHT)
            curl(RING, FULL_CURL)
            direction(RING, DIAGONAL_DOWN_RIGHT)
            curl(PINKY, FULL_CURL)
            direction(PINKY, DIAGONAL_DOWN_RIGHT)
        },
        sign('R') {
            curl(THUMB, HALF_CURL)
            direction(INDEX, DIAGONAL_UP_LEFT)
            curl(INDEX, NO_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(MIDDLE, NO_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, VERTICAL_UP)
        },
        sign('S') {
            curl(THUMB, HALF_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(INDEX, FULL_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, DIAGONAL_UP_LEFT)
        },
        sign('T') {
            curl(THUMB, NO_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(INDEX, FULL_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, DIAGONAL_UP_LEFT)
        },
        sign('U') {
            curl(THUMB, HALF_CURL)
            direction(INDEX, DIAGONAL_UP_LEFT)
            curl(INDEX, NO_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(MIDDLE, NO_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, DIAGONAL_UP_LEFT)
        },
        sign('V') {
            curl(THUMB, HALF_CURL)
            direction(THUMB, DIAGONAL_UP_LEFT)
            curl(INDEX, NO_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(MIDDLE, NO_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, DIAGONAL_UP_LEFT)
        },
        sign('W') {
            curl(THUMB, HALF_CURL)
            direction(INDEX, DIAGONAL_UP_LEFT)
            curl(INDEX, NO_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(MIDDLE, NO_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, NO_CURL)
            direction(RING, DIAGONAL_UP_LEFT)
            curl(PINKY, FULL_CURL)
            direction(PINKY, DIAGONAL_UP_LEFT)
        },
        sign('X') {
            curl(THUMB, HALF_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(INDEX, HALF_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, FULL_CURL)
            direction(PINKY, VERTICAL_UP)
        },
        sign('Y') {
            curl(THUMB, NO_CURL)
            direction(INDEX, DIAGONAL_UP_RIGHT)
            curl(INDEX, FULL_CURL)
            direction(INDEX, VERTICAL_UP)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, VERTICAL_UP)
            curl(RING, FULL_CURL)
            direction(RING, VERTICAL_UP)
            curl(PINKY, NO_CURL)
            direction(PINKY, DIAGONAL_UP_LEFT)
        },
        sign('Z') {
            curl(THUMB, NO_CURL, 0.8)
            direction(INDEX, HORIZONTAL_LEFT)
            curl(INDEX, NO_CURL)
            direction(INDEX, DIAGONAL_UP_LEFT)
            curl(MIDDLE, FULL_CURL)
            direction(MIDDLE, HORIZONTAL_LEFT)
            curl(RING, FULL_CURL)
            direction(RING, HORIZONTAL_LEFT)
            curl(PINKY, FULL_CURL)
            direction(PINKY, HORIZONTAL_LEFT)
        },
    )

    private fun sign(letter: Char, block: GestureDescription.Builder.() -> Unit) =
        GestureDescription.of(letter, block)
}
