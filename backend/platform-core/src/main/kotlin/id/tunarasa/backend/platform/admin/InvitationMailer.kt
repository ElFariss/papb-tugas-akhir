package id.tunarasa.backend.platform.admin

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ResendEmailRequest(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String,
)

object InvitationMailer {
    private val logger = KtorSimpleLogger("InvitationMailer")
    private val apiKey = System.getenv("RESEND_API_KEY")
    private val fromEmail = System.getenv("RESEND_FROM_EMAIL") ?: "Tunarasa <no-reply@tunarasa.id>"

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun sendInvitationEmail(
        toEmail: String,
        invitationToken: String,
        institutionName: String?,
    ): Boolean {
        if (apiKey.isNullOrBlank()) {
            logger.warn("RESEND_API_KEY is not set. Skipping actual email delivery to $toEmail. Token: $invitationToken")
            return true
        }

        val appUrl = System.getenv("APP_BASE_URL") ?: "https://tunarasa.id"
        val inviteLink = "$appUrl/admin/invite?token=$invitationToken"

        val htmlContent = """
            <div style="font-family: sans-serif; padding: 20px;">
                <h2>Undangan Admin Platform Tunarasa</h2>
                <p>Anda diundang menjadi Admin untuk <strong>${institutionName ?: "Sistem Tunarasa"}</strong>.</p>
                <p>Klik tombol di bawah ini untuk menerima undangan dan mengaktifkan akun Anda:</p>
                <p style="margin: 24px 0;">
                    <a href="$inviteLink" style="background-color: #2563eb; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold;">Terima Undangan</a>
                </p>
                <p style="color: #6b7280; font-size: 14px;">Tautan ini akan kadaluarsa dalam 48 jam.</p>
            </div>
        """.trimIndent()

        return try {
            val response = httpClient.post("https://api.resend.com/emails") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
                setBody(
                    ResendEmailRequest(
                        from = fromEmail,
                        to = listOf(toEmail),
                        subject = "Undangan Admin Tunarasa - ${institutionName ?: "Platform Access"}",
                        html = htmlContent,
                    ),
                )
            }

            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                logger.info("Successfully sent invitation email to $toEmail")
                true
            } else {
                logger.error("Failed to send Resend email to $toEmail. HTTP Status: ${response.status}")
                false
            }
        } catch (e: Exception) {
            logger.error("Exception sending invitation email via Resend to $toEmail", e)
            false
        }
    }
}
