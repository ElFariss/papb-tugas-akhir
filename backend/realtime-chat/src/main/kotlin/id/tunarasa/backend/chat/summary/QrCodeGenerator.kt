package id.tunarasa.backend.chat.summary

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.util.Base64

/** Port of `QRCodeService._create_qr_code` (ZXing instead of `qrcode`). Level L, 4-module quiet zone, 300px PNG. */
class QrCodeGenerator {

    fun matrix(data: String, size: Int = 300): BitMatrix {
        require(data.isNotEmpty()) { "QR data must not be empty" }
        require(size in 64..2048) { "QR size out of range" }
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 4,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        return QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size, hints)
    }

    fun png(data: String, size: Int = 300): ByteArray {
        val out = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(matrix(data, size), "PNG", out)
        return out.toByteArray()
    }

    /** Bare base64 (no `data:` prefix): the reference clients prepend `data:image/png;base64,` themselves. */
    fun base64Png(data: String, size: Int = 300): String = Base64.getEncoder().encodeToString(png(data, size))
}
