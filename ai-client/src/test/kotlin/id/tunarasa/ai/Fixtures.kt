package id.tunarasa.ai

import id.tunarasa.ai.gesture.HandFrame
import id.tunarasa.ai.gesture.HandLandmark

object Fixtures {
    fun lines(name: String): List<String> =
        checkNotNull(javaClass.getResource("/fixtures/$name")) { "Missing fixture $name" }
            .readText().lines().filter { it.isNotEmpty() }

    /** 63 numbers: x, y, z per landmark. */
    fun landmarks(values: List<Double>): List<HandLandmark> =
        values.chunked(3).map { (x, y, z) -> HandLandmark(x, y, z) }

    data class Sample(val intended: Char, val webLetter: Char?, val landmarks: List<HandLandmark>)

    val syntheticAlphabet: List<Sample> by lazy {
        lines("synthetic_alphabet.csv").map { line ->
            val cells = line.split(',')
            Sample(
                cells[0].single(),
                cells[1].single().takeIf { it != '-' },
                landmarks(cells.drop(2).map(String::toDouble)),
            )
        }
    }

    /** Landmarks the web app classifies as [letter]. */
    fun handOf(letter: Char): List<HandLandmark> =
        syntheticAlphabet.first { it.intended == letter && it.webLetter == letter }.landmarks

    fun frame(landmarks: List<HandLandmark>, timestampMillis: Long) =
        HandFrame(landmarks, 640, 480, mirrored = true, detectionConfidence = 0.9f, timestampMillis)
}
