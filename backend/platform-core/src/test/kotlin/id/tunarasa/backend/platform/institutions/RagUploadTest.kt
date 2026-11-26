package id.tunarasa.backend.platform.institutions

import id.tunarasa.backend.platform.db.DatabaseFactory
import id.tunarasa.backend.platform.db.InstitutionsTable
import id.tunarasa.backend.platform.db.RagFilesTable
import id.tunarasa.contracts.Answer
import id.tunarasa.contracts.Evaluation
import id.tunarasa.contracts.FaqCluster
import id.tunarasa.contracts.IngestionResult
import id.tunarasa.contracts.RagLlmService
import id.tunarasa.contracts.RagProcessingStatus
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test
import java.time.Instant

class MockRagLlmService : RagLlmService {
    var lastIngestedFileId: Int? = null
    var lastIngestedSlug: String? = null

    override suspend fun ingestDocument(ragFileId: Int, institutionSlug: String): IngestionResult {
        lastIngestedFileId = ragFileId
        lastIngestedSlug = institutionSlug
        return IngestionResult(ragFileId = ragFileId, status = RagProcessingStatus.COMPLETED, chunkCount = 12)
    }

    override suspend fun askRag(question: String, sessionId: String, institutionSlug: String?): Answer = TODO("Not needed for test")
    override suspend fun askGesture(gestureText: String, sessionId: String?, language: String, gestureConfidence: Float?): Answer = TODO("Not needed for test")
    override suspend fun clusterFaqs(institutionId: Int): List<FaqCluster> = TODO("Not needed for test")
    override suspend fun evaluateResponse(qaId: Int): Evaluation = TODO("Not needed for test")
}

class RagUploadTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            DatabaseFactory.init()
        }
    }

    @Test
    fun testRagFileUploadAndIngestionTrigger(): Unit = runBlocking {
        // 1. Create Institution
        val instId = DatabaseFactory.dbQuery {
            InstitutionsTable.insert {
                it[name] = "Dinas Kesehatan Kota Malang"
                it[slug] = "dinkes-kota-malang"
                it[status] = "active"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }[InstitutionsTable.id]
        }

        // 2. Insert RagFile Record
        val fileId = DatabaseFactory.dbQuery {
            RagFilesTable.insert {
                it[institutionId] = instId
                it[fileName] = "panduan_layanan_kesehatan.pdf"
                it[filePath] = "/tmp/uploads/panduan_layanan_kesehatan.pdf"
                it[fileSize] = 1024500L
                it[processingStatus] = "pending"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }[RagFilesTable.id]
        }
        assertNotNull(fileId)

        // 3. Trigger Mock AI-2 Ingestion
        val mockService = MockRagLlmService()
        val result = mockService.ingestDocument(fileId, "dinkes-kota-malang")

        assertEquals(fileId, mockService.lastIngestedFileId)
        assertEquals("dinkes-kota-malang", mockService.lastIngestedSlug)
        assertEquals(RagProcessingStatus.COMPLETED, result.status)

        // 4. Update DB status
        DatabaseFactory.dbQuery {
            RagFilesTable.update({ RagFilesTable.id eq fileId }) {
                it[processingStatus] = result.status.wire
                it[updatedAt] = Instant.now()
            }
        }

        val record = DatabaseFactory.dbQuery {
            RagFilesTable.selectAll().where { RagFilesTable.id eq fileId }.single()
        }
        assertEquals("completed", record[RagFilesTable.processingStatus])
    }
}
