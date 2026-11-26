package id.tunarasa.app.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val cause: Throwable? = null,
    ) : ApiResult<Nothing>
}

class ApiClient(
    val baseUrl: String = DEFAULT_BASE_URL,
    private val tokenProvider: suspend () -> String? = { null },
    enableLogging: Boolean = true,
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val httpClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets) // Siap untuk BE-2 (:backend:realtime-chat)
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        if (enableLogging) {
            install(Logging) {
                level = LogLevel.HEADERS
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("TunarasaHttp", message)
                    }
                }
            }
        }
        defaultRequest {
            url(baseUrl)
        }
    }

    suspend fun HttpRequestBuilder.attachBearerToken() {
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: return
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    suspend inline fun <reified T> safeRequest(
        block: HttpClient.() -> HttpResponse,
    ): ApiResult<T> {
        return try {
            val response = httpClient.block()
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                val errorMessage = runCatching {
                    response.body<ApiErrorResponse>().error
                }.getOrElse {
                    response.status.description.ifBlank { "HTTP ${response.status.value}" }
                }
                ApiResult.Error(
                    message = errorMessage,
                    statusCode = response.status.value,
                )
            }
        } catch (e: HttpRequestTimeoutException) {
            ApiResult.Error(message = "Koneksi timeout, coba lagi.", cause = e)
        } catch (e: IOException) {
            ApiResult.Error(message = "Tidak dapat terhubung ke server.", cause = e)
        } catch (e: SerializationException) {
            ApiResult.Error(message = "Format respons server tidak sesuai.", cause = e)
        } catch (e: Exception) {
            ApiResult.Error(message = e.localizedMessage ?: "Terjadi kesalahan.", cause = e)
        }
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        // 10.0.2.2 mengarah ke localhost komputer host dari Android Emulator
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"
    }
}
