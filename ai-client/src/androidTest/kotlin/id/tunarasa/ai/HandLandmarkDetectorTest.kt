package id.tunarasa.ai

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.tunarasa.ai.gesture.RecognizerStatus
import id.tunarasa.ai.gesture.SibiConfig
import id.tunarasa.ai.gesture.mediapipe.HandLandmarkDetector
import id.tunarasa.ai.gesture.mediapipe.MediaPipeGestureRecognizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Proves the packaged model and native library load and run on a device. */
@RunWith(AndroidJUnit4::class)
class HandLandmarkDetectorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun blankImageHasNoHand() {
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val hand = AtomicReference<Any>()
        HandLandmarkDetector(
            context,
            SibiConfig(),
            onFrame = { frame, _ -> hand.set(frame); done.countDown() },
            onError = { failure.set(it); done.countDown() },
        ).use { detector ->
            detector.detect(Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888), SystemClock.uptimeMillis())
            assertTrue("no result in time", done.await(30, TimeUnit.SECONDS))
        }
        assertNull(failure.get())
        assertNull(hand.get())
    }

    @Test
    fun recognizerInitialisesAndCloses() {
        val recognizer = MediaPipeGestureRecognizer(context)
        runBlocking { recognizer.initialize() }
        assertEquals(RecognizerStatus.Ready, recognizer.status.value)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { recognizer.close() }
        assertEquals(RecognizerStatus.Closed, recognizer.status.value)
    }
}
