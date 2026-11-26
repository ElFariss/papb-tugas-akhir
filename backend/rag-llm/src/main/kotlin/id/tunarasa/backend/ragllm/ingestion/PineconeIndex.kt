package id.tunarasa.backend.ragllm.ingestion

import id.tunarasa.backend.ragllm.JsonHttpClient
import id.tunarasa.backend.ragllm.RagProviderFailure
import id.tunarasa.contracts.Source
import id.tunarasa.contracts.institutionNamespace
import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal class PineconeIndex(
    private val apiKey: String,
    indexOrigin: URI,
    private val embedEndpoint: URI = URI.create("https://api.pinecone.io/embed"),
) {
    private val upsertEndpoint = indexOrigin.resolve("/vectors/upsert")
    private val queryEndpoint = indexOrigin.resolve("/query")
    private val http = JsonHttpClient()

    suspend fun upsert(ragFileId: Int, institutionSlug: String, fileName: String, chunks: List<String>) {
        val namespace = institutionNamespace(institutionSlug)
        for ((batchIndex, batch) in chunks.chunked(BATCH_SIZE).withIndex()) {
            val embeddings = embed(batch, "passage")
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
                throw RagProviderFailure("Pinecone did not confirm the full vector batch")
            }
        }
    }

    suspend fun query(institutionSlug: String, question: String, completedIds: Set<Int>): List<Source> {
        if (completedIds.isEmpty()) return emptyList()

        val namespace = institutionNamespace(institutionSlug)
        val vector = embed(listOf(question), "query").single()
        return completedIds.map(Int::toString).chunked(FILTER_BATCH_SIZE)
            .flatMap { documentIds -> queryBatch(namespace, vector, documentIds.toSet()) }
            .sortedByDescending(Source::score)
            .distinctBy { it.documentId to it.excerpt }
            .take(TOP_K)
    }

    private suspend fun queryBatch(namespace: String, vector: JsonArray, documentIds: Set<String>): List<Source> {
        val payload = buildJsonObject {
            put("namespace", namespace)
            put("vector", vector)
            put("topK", TOP_K)
            put("includeMetadata", true)
            put("filter", buildJsonObject {
                put("document_id", buildJsonObject {
                    putJsonArray("\$in") { documentIds.forEach { add(it) } }
                })
            })
        }
        val response = post(queryEndpoint, "query", payload)
        val matches = response["matches"] as? JsonArray
            ?: throw RagProviderFailure("Pinecone returned an invalid query response")
        if (matches.isNotEmpty() && (response["namespace"] as? JsonPrimitive)?.content != namespace) {
            throw RagProviderFailure("Pinecone returned a different namespace")
        }
        return matches.mapNotNull { item ->
            val match = item as? JsonObject ?: return@mapNotNull null
            val metadata = match["metadata"] as? JsonObject ?: return@mapNotNull null
            val documentId = (metadata["document_id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val id = (match["id"] as? JsonPrimitive)?.content
            val excerpt = (metadata["content"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val score = (match["score"] as? JsonPrimitive)?.doubleOrNull
            if (documentId !in documentIds || id?.startsWith("rag-file-$documentId#") != true ||
                excerpt.isNullOrBlank() || score?.isFinite() != true
            ) {
                null
            } else {
                Source(documentId, excerpt, score)
            }
        }
    }

    private suspend fun embed(chunks: List<String>, inputType: String) = try {
        val payload = buildJsonObject {
            put("model", MODEL)
            put("parameters", buildJsonObject {
                put("input_type", inputType)
                put("truncate", "END")
            })
            putJsonArray("inputs") {
                chunks.forEach { chunk -> addJsonObject { put("text", chunk) } }
            }
        }
        val data = post(embedEndpoint, "embedding", payload).getValue("data").jsonArray
        if (data.size != chunks.size) throw RagProviderFailure("Pinecone returned an unexpected embedding count")

        data.map { item ->
            item.jsonObject.getValue("values").jsonArray.also { values ->
                if (values.size != DIMENSIONS || values.any { it !is JsonPrimitive || it.isString || it.floatOrNull?.isFinite() != true }) {
                    throw RagProviderFailure("Pinecone returned an invalid embedding vector")
                }
            }
        }
    } catch (_: IllegalArgumentException) {
        throw RagProviderFailure("Pinecone returned an invalid embedding response")
    } catch (_: NoSuchElementException) {
        throw RagProviderFailure("Pinecone returned an incomplete embedding response")
    }

    private suspend fun post(endpoint: URI, operation: String, payload: JsonObject): JsonObject =
        http.post(endpoint, "Pinecone $operation", payload, "Api-Key", apiKey, "X-Pinecone-Api-Version", "2025-10")

    companion object {
        private const val MODEL = "multilingual-e5-large"
        private const val DIMENSIONS = 1024
        private const val BATCH_SIZE = 32
        private const val FILTER_BATCH_SIZE = 1_000
        private const val TOP_K = 5

        fun configured(apiKey: String, indexHost: String): PineconeIndex {
            require(apiKey.isNotBlank() && apiKey.all { it in '!'..'~' }) { "Invalid Pinecone API key" }
            val origin = URI.create("https://$indexHost")
            require(origin.host?.endsWith(".pinecone.io", ignoreCase = true) == true &&
                origin.port == -1 && origin.rawUserInfo == null && origin.rawPath.isEmpty() &&
                origin.rawQuery == null && origin.rawFragment == null
            ) { "Use the hostname of a Pinecone dense vector index" }
            return PineconeIndex(apiKey, origin)
        }

        fun configured(apiKey: String, indexHost: String, embedUrl: String?): PineconeIndex {
            require(apiKey.isNotBlank() && apiKey.all { it in '!'..'~' }) { "Invalid Pinecone API key" }
            val indexUri = URI.create(if ("://" in indexHost) indexHost else "https://$indexHost")
            require(isAllowedProviderUri(indexUri) && indexUri.rawPath.isEmpty() &&
                (indexUri.scheme == "http" || indexUri.port == -1)
            ) {
                "Use an HTTPS Pinecone index hostname or a loopback HTTP URL"
            }
            val endpoint = embedUrl?.let(URI::create) ?: URI.create("https://api.pinecone.io/embed")
            require(isAllowedProviderUri(endpoint)) { "Use HTTPS or a loopback HTTP embedding URL" }
            return PineconeIndex(apiKey, indexUri, endpoint)
        }

        private fun isAllowedProviderUri(uri: URI): Boolean =
            uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                (uri.scheme == "https" ||
                    (uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "::1")))
    }
}
