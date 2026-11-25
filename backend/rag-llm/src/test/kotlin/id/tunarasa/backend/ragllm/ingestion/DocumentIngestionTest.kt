package id.tunarasa.backend.ragllm.ingestion

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import id.tunarasa.backend.ragllm.DefaultRagLlmService
import id.tunarasa.contracts.RagProcessingStatus.COMPLETED
import id.tunarasa.contracts.RagProcessingStatus.FAILED
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentIngestionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var pinecone: HttpServer
    private lateinit var index: PineconeIndex
    private val vectors = ConcurrentHashMap<String, JsonObject>()
    private val upsertCalls = AtomicInteger()
    private val failOnUpsertCall = AtomicInteger()
    private val embeddingDimensions = AtomicInteger(1024)

    @Before
    fun startPinecone() {
        pinecone = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        pinecone.createContext("/embed") { exchange ->
            val request = exchange.readRequest()
            val count = request.getValue("inputs").jsonArray.size
            val vector = List(embeddingDimensions.get()) { "0.1" }.joinToString(",")
            val data = List(count) { """{"values":[$vector]}""" }.joinToString(",")
            exchange.reply(200, """{"data":[$data]}""")
        }
        pinecone.createContext("/vectors/upsert") { exchange ->
            val request = exchange.readRequest()
            if (upsertCalls.incrementAndGet() == failOnUpsertCall.get()) {
                exchange.reply(503, """{"message":"private provider response"}""")
            } else {
                val namespace = request.getValue("namespace").jsonPrimitive.content
                val batch = request.getValue("vectors").jsonArray
                for (record in batch) {
                    val vector = record.jsonObject
                    vectors["$namespace/${vector.getValue("id").jsonPrimitive.content}"] = vector
                }
                exchange.reply(200, """{"upsertedCount":${batch.size}}""")
            }
        }
        pinecone.start()
        val origin = URI.create("http://127.0.0.1:${pinecone.address.port}")
        index = PineconeIndex("test-key", origin, origin.resolve("/embed"))
    }

    @After
    fun stopPinecone() = pinecone.stop(0)

    @Test
    fun `retry uses the same vector IDs and never writes to another institution namespace`() = runBlocking {
        val a = document("Bawa KTP ke loket A.")
        val b = document("Bawa KK ke loket B.")
        val source = mapOf((7 to "instansi-a") to a, (7 to "instansi-b") to b)
        val service = DefaultRagLlmService(RagDocumentSource { id, slug -> source[id to slug] }, index)

        val first = service.ingestDocument(7, "instansi-a")
        assertEquals(COMPLETED, first.status)
        assertEquals(1, first.chunkCount)
        assertEquals(first, service.ingestDocument(7, "instansi-a"))
        assertEquals(COMPLETED, service.ingestDocument(7, "instansi-b").status)
        assertEquals(FAILED, service.ingestDocument(7, "instansi-c").status)

        assertEquals(setOf("institution_instansi-a/rag-file-7#0", "institution_instansi-b/rag-file-7#0"), vectors.keys)
        val metadata = vectors.getValue("institution_instansi-a/rag-file-7#0").getValue("metadata").jsonObject
        assertEquals("7", metadata.getValue("document_id").jsonPrimitive.content)
        assertEquals("panduan.txt", metadata.getValue("filename").jsonPrimitive.content)
        assertEquals("0", metadata.getValue("chunk_index").jsonPrimitive.content)
        assertEquals("Bawa KTP ke loket A.", metadata.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun `a partial provider failure can be retried without duplicate vectors`() = runBlocking {
        val upload = document("a".repeat(20_000))
        val service = DefaultRagLlmService(RagDocumentSource { _, _ -> upload }, index)
        failOnUpsertCall.set(2)

        val failed = service.ingestDocument(7, "instansi-a")
        assertEquals(FAILED, failed.status)
        assertEquals(0, failed.chunkCount)
        assertTrue(failed.error.orEmpty().contains("HTTP 503"))
        assertFalse(failed.error.orEmpty().contains("private provider response"))
        assertEquals(32, vectors.size)

        val completed = service.ingestDocument(7, "instansi-a")
        assertEquals(COMPLETED, completed.status)
        assertTrue(completed.chunkCount > 32)
        assertEquals(completed.chunkCount, vectors.size)
        assertTrue("institution_instansi-a/rag-file-7#32" in vectors.keys)
    }

    @Test
    fun `unexpected vector dimension fails before upsert`() = runBlocking {
        val upload = document("Pendaftaran layanan.")
        val service = DefaultRagLlmService(RagDocumentSource { _, _ -> upload }, index)
        embeddingDimensions.set(12)

        val result = service.ingestDocument(12, "instansi-a")
        assertEquals(FAILED, result.status)
        assertEquals(0, upsertCalls.get())
    }

    private fun document(text: String): RagDocument {
        val path = folder.newFile().toPath()
        Files.writeString(path, text)
        return RagDocument(path, "panduan.txt")
    }

    private fun HttpExchange.readRequest(): JsonObject {
        assertEquals("POST", requestMethod)
        assertEquals("test-key", requestHeaders.getFirst("Api-Key"))
        assertEquals("2025-10", requestHeaders.getFirst("X-Pinecone-Api-Version"))
        return requestBody.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    }

    private fun HttpExchange.reply(status: Int, payload: String) {
        val body = payload.toByteArray()
        sendResponseHeaders(status, body.size.toLong())
        responseBody.use { it.write(body) }
    }
}
