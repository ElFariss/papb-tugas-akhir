package id.tunarasa.backend.ragllm.ingestion

import id.tunarasa.contracts.institutionNamespace
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal class PineconeFailure(message: String) : IOException(message)

internal class PineconeIndex(
    private val apiKey: String,
    indexOrigin: URI,
    private val embedEndpoint: URI = URI.create("https://api.pinecone.io/embed"),
) {
    private val upsertEndpoint = indexOrigin.resolve("/vectors/upsert")
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    suspend fun upsert(ragFileId: Int, institutionSlug: String, fileName: String, chunks: List<String>) {
        val namespace = institutionNamespace(institutionSlug)
        for ((batchIndex, batch) in chunks.chunked(BATCH_SIZE).withIndex()) {
            val embeddings = embed(batch)
            val payload = buildJsonObject {
                put("namespace", namespace)
                putJsonArray("vectors") {
                    for ((index, chunk) in batch.withIndex()) {
                        val chunkIndex = batchIndex * BATCH_SIZE + index
                        addJsonObject {
                            put("id", "rag-file-$ragFileId#$chunkIndex")
                            put("values", embeddings[index])
                            put("metadata", buildJsonObject {
                                put("document_id", ragFileId.toString())
                                put("filename", fileName)
                                put("chunk_index", chunkIndex)
                                put("content", chunk)
                            })
                        }
                    }
                }
            }
            val response = post(upsertEndpoint, "upsert", payload)
            if ((response["upsertedCount"] as? JsonPrimitive)?.intOrNull != batch.size) {
                throw PineconeFailure("Pinecone did not confirm the full vector batch")
            }
        }
    }

    private suspend fun embed(chunks: List<String>) = try {
        val payload = buildJsonObject {
            put("model", MODEL)
            put("parameters", buildJsonObject {
                put("input_type", "passage")
                put("truncate", "END")
            })
            putJsonArray("inputs") {
                chunks.forEach { chunk -> addJsonObject { put("text", chunk) } }
            }
        }
        val data = post(embedEndpoint, "embedding", payload).getValue("data").jsonArray
        if (data.size != chunks.size) throw PineconeFailure("Pinecone returned an unexpected embedding count")

        data.map { item ->
            item.jsonObject.getValue("values").jsonArray.also { values ->
                if (values.size != DIMENSIONS || values.any { it !is JsonPrimitive || it.isString || it.floatOrNull?.isFinite() != true }) {
                    throw PineconeFailure("Pinecone returned an invalid embedding vector")
                }
            }
        }
    } catch (_: IllegalArgumentException) {
        throw PineconeFailure("Pinecone returned an invalid embedding response")
    } catch (_: NoSuchElementException) {
        throw PineconeFailure("Pinecone returned an incomplete embedding response")
    }

    private suspend fun post(endpoint: URI, operation: String, payload: JsonObject): JsonObject {
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(30))
            .header("Api-Key", apiKey)
            .header("X-Pinecone-Api-Version", "2025-10")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()
        val response = try {
            runInterruptible(Dispatchers.IO) { http.send(request, HttpResponse.BodyHandlers.ofString()) }
        } catch (_: IOException) {
            throw PineconeFailure("Pinecone $operation request failed")
        }
        if (response.statusCode() !in 200..299) {
            throw PineconeFailure("Pinecone $operation returned HTTP ${response.statusCode()}")
        }
        return try {
            Json.parseToJsonElement(response.body()).jsonObject
        } catch (_: IllegalArgumentException) {
            throw PineconeFailure("Pinecone returned an invalid $operation response")
        }
    }

    companion object {
        private const val MODEL = "multilingual-e5-large"
        private const val DIMENSIONS = 1024
        private const val BATCH_SIZE = 32

        fun configured(apiKey: String, indexHost: String): PineconeIndex {
            require(apiKey.isNotBlank() && apiKey.all { it in '!'..'~' }) { "Invalid Pinecone API key" }
            val origin = URI.create("https://$indexHost")
            require(origin.host?.endsWith(".pinecone.io", ignoreCase = true) == true &&
                origin.port == -1 && origin.rawUserInfo == null && origin.rawPath.isEmpty() &&
                origin.rawQuery == null && origin.rawFragment == null
            ) { "Use the hostname of a Pinecone dense vector index" }
            return PineconeIndex(apiKey, origin)
        }
    }
}
