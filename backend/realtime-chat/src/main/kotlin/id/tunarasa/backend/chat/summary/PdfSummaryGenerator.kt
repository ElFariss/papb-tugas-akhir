package id.tunarasa.backend.chat.summary

import org.openpdf.text.Chunk
import org.openpdf.text.Document
import org.openpdf.text.Element
import org.openpdf.text.Font
import org.openpdf.text.PageSize
import org.openpdf.text.Paragraph
import org.openpdf.text.pdf.PdfWriter
import org.openpdf.text.pdf.draw.LineSeparator
import java.awt.Color
import java.io.ByteArrayOutputStream

/**
 * Port of `QRCodeService.create_note_pdf` (ReportLab) to OpenPDF: A4, 2 cm margins, centred brand header with a
 * blue rule, right-aligned grey metadata (ID, created), optional bold title, grey rule, justified body.
 * Pagination, wrapping and justification are done by the PDF library instead of the reference's hand-rolled loop.
 */
class PdfSummaryGenerator {

    fun noteToPdf(title: String?, content: String, accessId: String, createdAt: String): ByteArray {
        val out = ByteArrayOutputStream()
        val cm = 28.35f
        val doc = Document(PageSize.A4, 2 * cm, 2 * cm, 2 * cm, 2 * cm)
        try {
            PdfWriter.getInstance(doc, out)
            doc.open()

            val head = Paragraph("RINGKASAN PERCAKAPAN TUNARASA", Font(Font.HELVETICA, 18f, Font.BOLD, Color.BLACK))
            head.setAlignment(Element.ALIGN_CENTER)
            head.setSpacingAfter(6f)
            doc.add(head)
            doc.add(Paragraph(Chunk(LineSeparator(2f, 60f, Color.BLUE, Element.ALIGN_CENTER, 0f))))

            val meta = Font(Font.HELVETICA, 9f, Font.NORMAL, Color.GRAY)
            for (line in listOf("ID: $accessId", "Dibuat: $createdAt")) {
                val p = Paragraph(line, meta)
                p.setAlignment(Element.ALIGN_RIGHT)
                doc.add(p)
            }
            doc.add(Paragraph(" "))

            if (!title.isNullOrBlank() && title != DEFAULT_TITLE) {
                val t = Paragraph(title, Font(Font.HELVETICA, 14f, Font.BOLD, Color.BLACK))
                t.setAlignment(Element.ALIGN_CENTER)
                t.setSpacingAfter(8f)
                doc.add(t)
            }
            doc.add(Paragraph(Chunk(LineSeparator(0.5f, 100f, Color.GRAY, Element.ALIGN_CENTER, 0f))))
            doc.add(Paragraph(" "))

            val bodyFont = Font(Font.HELVETICA, 11f, Font.NORMAL, Color.BLACK)
            content.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { para ->
                val p = Paragraph(para.replace(Regex("\\s*\\n\\s*"), " "), bodyFont)
                p.setAlignment(Element.ALIGN_JUSTIFIED)
                p.setLeading(0f, 1.3f)
                p.setSpacingAfter(8f)
                doc.add(p)
            }
        } finally {
            if (doc.isOpen) doc.close()
        }
        return out.toByteArray()
    }
}
