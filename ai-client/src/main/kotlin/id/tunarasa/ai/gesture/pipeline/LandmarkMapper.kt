package id.tunarasa.ai.gesture.pipeline

import id.tunarasa.ai.gesture.HandLandmark

/** Converts MediaPipe's normalised landmarks to the pixel space Fingerpose expects. */
object LandmarkMapper {
    /**
     * MediaPipe divides x by width and y by height, which distorts every angle on a non-square image, so both are
     * scaled back. z is documented to use roughly the same scale as x.
     */
    fun toPixels(
        x: Float,
        y: Float,
        z: Float,
        imageWidth: Int,
        imageHeight: Int,
        mirror: Boolean,
    ): HandLandmark = HandLandmark(
        x = (if (mirror) 1.0 - x else x.toDouble()) * imageWidth,
        y = y.toDouble() * imageHeight,
        z = z.toDouble() * imageWidth,
    )
}
