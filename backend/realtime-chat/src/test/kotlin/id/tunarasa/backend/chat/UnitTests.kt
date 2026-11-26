package id.tunarasa.backend.chat

import id.tunarasa.backend.chat.gateway.FixedWindowLimiter
import id.tunarasa.backend.chat.session.InMemoryActiveSessionStore
import id.tunarasa.backend.chat.summary.DEFAULT_TITLE
import id.tunarasa.backend.chat.summary.PdfSummaryGenerator
import id.tunarasa.backend.chat.summary.QrCodeGenerator
import id.tunarasa.backend.chat.summary.TitleSanitizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class TitleSanitizerTest {
    @Test fun `joins a character array title`() =
        assertEquals("Cara Membuat KTP", TitleSanitizer.clean("""["C","a","r","a"," ","M","e","m","b","u","a","t"," ","K","T","P"]"""))

    @Test fun `strips JUDUL prefix and quotes`() =
        assertEquals("Cara membuat KTP baru", TitleSanitizer.clean("JUDUL: \"Cara membuat KTP baru\""))

    @Test fun `splits glued words`() = assertEquals("Cara Buat Paspor", TitleSanitizer.clean("CaraBuatPaspor"))

    @Test fun `short or null falls back to the default`() {
        assertEquals(DEFAULT_TITLE, TitleSanitizer.clean("ab"))
        assertEquals(DEFAULT_TITLE, TitleSanitizer.clean(null))
    }
}

class ActiveSessionStoreTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = now
    }

    @Test fun `entries expire after the ttl and replaceWith evicts the rest`() = runBlocking {
        val clock = MutableClock(Instant.parse("2025-01-01T00:00:00Z"))
        val store = InMemoryActiveSessionStore(Duration.ofMinutes(30), clock)
        store.touch("a"); store.touch("b")
        assertEquals(2, store.count())
        store.replaceWith(setOf("b", "c"))
        assertEquals(2, store.count())
        store.remove("c")
        assertEquals(1, store.count())
        clock.now = clock.now.plus(Duration.ofMinutes(31))
        assertEquals(0, store.count())
    }
}

class FixedWindowLimiterTest {
    @Test fun `allows the budget then blocks until the window rolls`() {
        var now = 0L
        val l = FixedWindowLimiter(2) { now }
        assertTrue(l.tryAcquire("ip")); assertTrue(l.tryAcquire("ip"))
        assertFalse(l.tryAcquire("ip"))
        assertTrue(l.tryAcquire("other"))
        now = 60_001
        assertTrue(l.tryAcquire("ip"))
    }
}

class QrAndPdfTest {
    @Test fun `qr is a png`() {
        val png = QrCodeGenerator().png("https://tunarasa.my.id/summary/abc")
        assertEquals(0x89, png[0].toInt() and 0xFF)
        assertEquals('P'.code, png[1].toInt())
    }

    @Test fun `note pdf is a pdf and survives long multi paragraph text`() {
        val body = (1..80).joinToString("\n\n") { "Paragraf $it. " + "kalimat ".repeat(40) }
        val pdf = PdfSummaryGenerator().noteToPdf("Cara Membuat KTP", body, "id-123", "2025-01-01 10:00")
        assertEquals("%PDF", String(pdf, 0, 4, Charsets.US_ASCII))
        assertTrue(pdf.size > 2000)
    }
}
