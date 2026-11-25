package id.tunarasa.backend.ragllm

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** A provider failure that the server can map to a gateway error. */
class RagProviderFailure internal constructor(message: String) : IOException(message)

internal class JsonHttpClient {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    suspend fun post(endpoint: URI, operation: String, payload: JsonObject, vararg headers: String): JsonObject {
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .headers(*headers)
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()
        val response = try {
            runInterruptible(Dispatchers.IO) { http.send(request, HttpResponse.BodyHandlers.ofString()) }
        } catch (_: IOException) {
            throw RagProviderFailure("$operation request failed")
        }
        if (response.statusCode() !in 200..299) {
            throw RagProviderFailure("$operation returned HTTP ${response.statusCode()}")
        }
        return try {
            Json.parseToJsonElement(response.body()).jsonObject
        } catch (_: IllegalArgumentException) {
            throw RagProviderFailure("$operation returned an invalid response")
        }
    }
}
