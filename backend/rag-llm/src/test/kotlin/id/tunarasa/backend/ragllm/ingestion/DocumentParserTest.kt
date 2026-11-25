package id.tunarasa.backend.ragllm.ingestion

import java.nio.file.Files
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentParserTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `reads and normalizes UTF-8 text before splitting with overlap`() {
        val prefix = "a".repeat(600)
        val overlap = "b".repeat(100)
        val suffix = "c".repeat(30)
        val file = folder.newFile("upload").toPath()
        Files.writeString(file, "\uFEFF$prefix$overlap$suffix\r\nLokét\tKTP")

        val chunks = DocumentParser.parse(RagDocument(file, "Panduan.TXT"))

        assertEquals(listOf(prefix + overlap, "$overlap$suffix Lokét KTP"), chunks)
    }

    @Test
    fun `keeps surrogate pairs intact across a chunk boundary`() {
        val file = folder.newFile("upload").toPath()
        Files.writeString(file, "a".repeat(699) + "😀" + "b".repeat(10))

        val chunks = DocumentParser.parse(RagDocument(file, "panduan.txt"))

        assertEquals("a".repeat(699), chunks.first())
        assertEquals("a".repeat(100) + "😀" + "b".repeat(10), chunks.last())
    }

    @Test
    fun `extracts text from a PDF using its original filename`() {
        val file = folder.newFile("storage-id")
        PDDocument().use { pdf ->
            val page = PDPage()
            pdf.addPage(page)
            PDPageContentStream(pdf, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText("Bawa KTP ke loket.")
                stream.endText()
            }
            pdf.save(file)
        }

        assertEquals(listOf("Bawa KTP ke loket."), DocumentParser.parse(RagDocument(file.toPath(), "info.pdf")))
    }

    @Test
    fun `rejects empty and unsupported documents`() {
        val file = folder.newFile("upload").toPath()
        Files.writeString(file, "\r\n  \t")
        val empty = assertThrows(InvalidDocument::class.java) {
            DocumentParser.parse(RagDocument(file, "info.txt"))
        }
        val unsupported = assertThrows(InvalidDocument::class.java) {
            DocumentParser.parse(RagDocument(file, "info.docx"))
        }
        assertTrue(empty.message.orEmpty().contains("no extractable text"))
        assertTrue(unsupported.message.orEmpty().contains("Unsupported"))
    }

    @Test
    fun `rejects extracted text above the limit`() {
        val file = folder.newFile("upload").toPath()
        Files.writeString(file, "a".repeat(250_001))

        assertThrows(InvalidDocument::class.java) {
            DocumentParser.parse(RagDocument(file, "info.txt"))
        }
    }
}
