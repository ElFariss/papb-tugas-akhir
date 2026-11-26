package id.tunarasa.app.ui.summary

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object SummaryPdfHelper {

    fun createSummaryPdfFile(
        context: Context,
        token: String,
        institutionName: String,
        createdAt: String,
        content: String
    ): File {
        val dir = File(context.cacheDir, "summaries").apply { mkdirs() }
        val shortToken = token.take(8).ifBlank { "sesi" }
        val file = File(dir, "ringkasan-percakapan-$shortToken.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
            color = android.graphics.Color.parseColor("#1E3A8A")
        }
        val metaPaint = Paint().apply {
            textSize = 11f
            color = android.graphics.Color.parseColor("#475569")
        }
        val bodyPaint = Paint().apply {
            textSize = 12f
            color = android.graphics.Color.parseColor("#0F172A")
        }

        var y = 56f
        val leftMargin = 44f
        val maxWidth = 507f

        canvas.drawText("Ringkasan Percakapan — TunaRasa", leftMargin, y, titlePaint)
        y += 24f
        canvas.drawText("Institusi: $institutionName", leftMargin, y, metaPaint)
        y += 18f
        canvas.drawText("Tanggal: $createdAt  |  Token: $token", leftMargin, y, metaPaint)
        y += 28f

        // Bungkus teks per baris agar tidak terpotong di tepi halaman
        val paragraphs = content.split("\n")
        for (paragraph in paragraphs) {
            val words = paragraph.split(" ")
            var currentLine = ""
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (bodyPaint.measureText(testLine) <= maxWidth) {
                    currentLine = testLine
                } else {
                    if (y > 780f) break
                    canvas.drawText(currentLine, leftMargin, y, bodyPaint)
                    y += 18f
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty() && y <= 780f) {
                canvas.drawText(currentLine, leftMargin, y, bodyPaint)
                y += 22f
            }
        }

        document.finishPage(page)
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    fun saveToDownloads(context: Context, sourceFile: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return false

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                }
                true
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                val dest = File(downloadsDir, sourceFile.name)
                sourceFile.copyTo(dest, overwrite = true)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun sharePdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ringkasan Percakapan TunaRasa")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Bagikan ringkasan PDF"))
    }
}
