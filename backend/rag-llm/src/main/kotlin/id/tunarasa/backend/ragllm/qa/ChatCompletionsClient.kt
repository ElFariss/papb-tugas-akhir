package id.tunarasa.backend.ragllm.qa

import id.tunarasa.backend.ragllm.JsonHttpClient
import id.tunarasa.backend.ragllm.RagProviderFailure
import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal class ChatCompletionsClient(
    private val endpoint: URI,
    private val apiKey: String,
    private val model: String,
) {
    private val http = JsonHttpClient()

    suspend fun complete(instructions: String, input: JsonObject): String {
        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", 800)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", instructions)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", input.toString())
                }
            }
        }
        val response = http.post(endpoint, "Chat completion", payload, "Authorization", "Bearer $apiKey")
        val choice = (response["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        val message = choice?.get("message") as? JsonObject
        return (message?.get("content") as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.takeIf { it.isNotBlank() }
            ?: throw RagProviderFailure("Chat completion returned no message")
    }

    companion object {
        fun configured(baseUrl: String, apiKey: String, model: String): ChatCompletionsClient {
            val base = runCatching { URI.create(baseUrl.trimEnd('/')) }.getOrNull()
            require(base != null && base.host != null && base.rawUserInfo == null &&
                base.rawQuery == null && base.rawFragment == null
                && (base.scheme == "https" ||
                    (base.scheme == "http" && base.host in setOf("localhost", "127.0.0.1", "::1")))
            ) { "Use an HTTPS chat provider URL or a loopback HTTP URL" }
            require(apiKey.isNotBlank() && apiKey.all { it in '!'..'~' }) { "Invalid chat API key" }
            require(model.isNotBlank()) { "A chat model is required" }
            return ChatCompletionsClient(URI.create("$base/chat/completions"), apiKey, model)
        }
    }
}
