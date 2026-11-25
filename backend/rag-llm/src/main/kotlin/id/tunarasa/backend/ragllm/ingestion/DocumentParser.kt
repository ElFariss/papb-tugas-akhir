package id.tunarasa.backend.ragllm.ingestion

import java.io.Writer
import java.nio.file.Files
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

internal class InvalidDocument(message: String) : IllegalArgumentException(message)

internal object DocumentParser {
    private const val MAX_FILE_BYTES = 20L * 1024 * 1024
    private const val MAX_TEXT_LENGTH = 250_000
    private const val CHUNK_SIZE = 700
    private const val CHUNK_OVERLAP = 100
    private val whitespace = Regex("[\\p{Z}\\s]+")

    fun parse(document: RagDocument): List<String> {
        if (!Files.isRegularFile(document.path)) throw InvalidDocument("Document file is unavailable")
        if (Files.size(document.path) > MAX_FILE_BYTES) throw InvalidDocument("Document exceeds 20 MiB")

        val output = TextBuffer()
        when (document.fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> Loader.loadPDF(document.path.toFile()).use { pdf ->
                if (!pdf.currentAccessPermission.canExtractContent()) {
                    throw InvalidDocument("PDF does not allow text extraction")
                }
                PDFTextStripper().writeText(pdf, output)
            }
            "txt", "text" -> Files.newBufferedReader(document.path, Charsets.UTF_8).use { it.copyTo(output) }
            else -> throw InvalidDocument("Unsupported document format. Use PDF or UTF-8 TXT")
        }

        val text = whitespace.replace(output.toString().removePrefix("\uFEFF"), " ").trim()
        if (text.isEmpty()) throw InvalidDocument("Document contains no extractable text")

        return buildList {
            var start = 0
            while (start < text.length) {
                var end = minOf(start + CHUNK_SIZE, text.length)
                if (end < text.length) {
                    val wordBoundary = text.lastIndexOf(' ', end)
                    if (wordBoundary >= start + CHUNK_SIZE / 2) {
                        end = wordBoundary
                    } else if (text[end].isLowSurrogate()) {
                        end--
                    }
                }
                add(text.substring(start, end).trim())
                if (end == text.length) break
                start = end - CHUNK_OVERLAP
                if (text[start].isLowSurrogate()) start--
            }
        }
    }

    private class TextBuffer : Writer() {
        private val text = StringBuilder()

        override fun write(buffer: CharArray, offset: Int, length: Int) {
            if (length > MAX_TEXT_LENGTH - text.length) {
                throw InvalidDocument("Extracted text exceeds 250,000 characters")
            }
            text.append(buffer, offset, length)
        }

        override fun flush() = Unit
        override fun close() = Unit
        override fun toString(): String = text.toString()
    }
}
