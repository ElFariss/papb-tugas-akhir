package id.tunarasa.backend.platform.institutions

import id.tunarasa.backend.platform.auth.authorizeRoles
import id.tunarasa.backend.platform.auth.canAccessInstitution
import id.tunarasa.backend.platform.auth.userPrincipal
import id.tunarasa.backend.platform.db.DatabaseFactory
import id.tunarasa.backend.platform.db.InstitutionsTable
import id.tunarasa.backend.platform.db.RagFilesTable
import id.tunarasa.contracts.RagLlmService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.io.File
import java.time.Instant

@Serializable
data class RagFileDto(
    val id: Int,
    val institutionId: Int,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val processingStatus: String,
    val errorMessage: String? = null,
    val uploadedBy: String? = null,
    val createdAt: String,
)

object RagUploadRoutes {
    private val allowedExtensions = setOf("pdf", "txt", "md", "docx")
    private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB

    fun register(route: Route, ragLlmService: RagLlmService? = null) {
        route.route("/api/v1/institutions/{id}/rag-files") {
            authenticate("supabase-jwt") {
                authorizeRoles("super_admin", "institution_admin") {
                    // GET /api/v1/institutions/{id}/rag-files
                    get {
                        val principal = call.userPrincipal()!!
                        val instId = call.parameters["id"]?.toIntOrNull()
                            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid institution ID"))

                        if (!principal.canAccessInstitution(instId)) {
                            return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden: Cannot access another institution files"))
                        }

                        val files = DatabaseFactory.dbQuery {
                            RagFilesTable
                                .selectAll()
                                .where { RagFilesTable.institutionId eq instId }
                                .map {
                                    RagFileDto(
                                        id = it[RagFilesTable.id],
                                        institutionId = it[RagFilesTable.institutionId],
                                        fileName = it[RagFilesTable.fileName],
                                        filePath = it[RagFilesTable.filePath],
                                        fileSize = it[RagFilesTable.fileSize],
                                        processingStatus = it[RagFilesTable.processingStatus],
                                        errorMessage = it[RagFilesTable.errorMessage],
                                        uploadedBy = it[RagFilesTable.uploadedBy],
                                        createdAt = it[RagFilesTable.createdAt].toString(),
                                    )
                                }
                        }

                        call.respond(HttpStatusCode.OK, files)
                    }

                    // POST /api/v1/institutions/{id}/rag-files (Multipart File Upload)
                    post {
                        val principal = call.userPrincipal()!!
                        val instId = call.parameters["id"]?.toIntOrNull()
                            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid institution ID"))

                        if (!principal.canAccessInstitution(instId)) {
                            return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden: Cannot upload file for another institution"))
                        }

                        val instSlug = DatabaseFactory.dbQuery {
                            InstitutionsTable
                                .selectAll()
                                .where { InstitutionsTable.id eq instId }
                                .singleOrNull()
                                ?.get(InstitutionsTable.slug)
                        } ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Institution not found"))

                        val multipart = call.receiveMultipart()
                        var uploadedFileName: String? = null
                        var savedFilePath: String? = null
                        var savedFileSize: Long = 0L

                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                val originalName = part.originalFileName ?: "document"
                                val extension = originalName.substringAfterLast('.', "").lowercase()

                                if (extension !in allowedExtensions) {
                                    part.dispose()
                                    return@forEachPart
                                }

                                val uploadDir = File("uploads/rag_files/$instId").apply { mkdirs() }
                                val destinationFile = File(uploadDir, "${System.currentTimeMillis()}_$originalName")

                                val bytes = part.provider().readRemaining().readByteArray()
                                if (bytes.size > MAX_FILE_SIZE_BYTES) {
                                    part.dispose()
                                    return@forEachPart
                                }

                                destinationFile.writeBytes(bytes)

                                uploadedFileName = originalName
                                savedFilePath = destinationFile.absolutePath
                                savedFileSize = bytes.size.toLong()
                            }
                            part.dispose()
                        }

                        if (savedFilePath == null || uploadedFileName == null) {
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Invalid file upload. Allowed formats: pdf, txt, md, docx. Max size: 10MB"),
                            )
                        }

                        val newFileId = DatabaseFactory.dbQuery {
                            RagFilesTable.insert {
                                it[institutionId] = instId
                                it[fileName] = uploadedFileName
                                it[filePath] = savedFilePath
                                it[fileSize] = savedFileSize
                                it[processingStatus] = "pending"
                                it[uploadedBy] = principal.userId
                                it[createdAt] = Instant.now()
                                it[updatedAt] = Instant.now()
                            }[RagFilesTable.id]
                        }

                        // Trigger AI-2 Ingestion asynchronously if service is available
                        if (ragLlmService != null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    DatabaseFactory.dbQuery {
                                        RagFilesTable.update({ RagFilesTable.id eq newFileId }) {
                                            it[processingStatus] = "processing"
                                            it[updatedAt] = Instant.now()
                                        }
                                    }

                                    val result = ragLlmService.ingestDocument(newFileId, instSlug)

                                    DatabaseFactory.dbQuery {
                                        RagFilesTable.update({ RagFilesTable.id eq newFileId }) {
                                            it[processingStatus] = result.status.wire
                                            it[errorMessage] = result.error
                                            it[updatedAt] = Instant.now()
                                        }
                                    }
                                } catch (e: Exception) {
                                    DatabaseFactory.dbQuery {
                                        RagFilesTable.update({ RagFilesTable.id eq newFileId }) {
                                            it[processingStatus] = "failed"
                                            it[errorMessage] = e.message
                                            it[updatedAt] = Instant.now()
                                        }
                                    }
                                }
                            }
                        }

                        val responseDto = RagFileDto(
                            id = newFileId,
                            institutionId = instId,
                            fileName = uploadedFileName!!,
                            filePath = savedFilePath!!,
                            fileSize = savedFileSize,
                            processingStatus = "pending",
                            uploadedBy = principal.userId,
                            createdAt = Instant.now().toString(),
                        )

                        call.respond(HttpStatusCode.Created, responseDto)
                    }

                    // DELETE /api/v1/institutions/{id}/rag-files/{fileId}
                    delete("/{fileId}") {
                        val principal = call.userPrincipal()!!
                        val instId = call.parameters["id"]?.toIntOrNull()
                            ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid institution ID"))
                        val fileId = call.parameters["fileId"]?.toIntOrNull()
                            ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid file ID"))

                        if (!principal.canAccessInstitution(instId)) {
                            return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden: Cannot delete file from another institution"))
                        }

                        val filePathToDelete = DatabaseFactory.dbQuery {
                            val record = RagFilesTable
                                .selectAll()
                                .where { (RagFilesTable.id eq fileId) and (RagFilesTable.institutionId eq instId) }
                                .singleOrNull()

                            if (record != null) {
                                val path = record[RagFilesTable.filePath]
                                RagFilesTable.deleteWhere { RagFilesTable.id eq fileId }
                                path
                            } else null
                        }

                        if (filePathToDelete != null) {
                            try {
                                File(filePathToDelete).delete()
                            } catch (_: Exception) {}
                            call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
                        } else {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "File record not found"))
                        }
                    }
                }
            }
        }
    }
}
