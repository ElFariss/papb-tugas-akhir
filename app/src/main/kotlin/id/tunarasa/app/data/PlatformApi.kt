package id.tunarasa.app.data

import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class PlatformApi(private val apiClient: ApiClient) {

    // ==================== Health ====================

    suspend fun getHealth(): ApiResult<HealthStatusDto> {
        return try {
            val response = apiClient.httpClient.get("/api/v1/health")
            // Server tetap mengirim JSON HealthStatus baik saat 200 UP maupun 503 DEGRADED
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.ServiceUnavailable) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Error(
                    message = response.status.description,
                    statusCode = response.status.value,
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(message = e.localizedMessage ?: "Gagal mengecek status server.", cause = e)
        }
    }

    suspend fun getLiveness(): ApiResult<StatusResponse> =
        apiClient.safeRequest { get("/api/v1/health/liveness") }

    suspend fun getReadiness(): ApiResult<StatusResponse> =
        apiClient.safeRequest { get("/api/v1/health/readiness") }

    // ==================== Public Config ====================

    suspend fun getServiceConfig(): ApiResult<ServiceConfigResponse> =
        apiClient.safeRequest { get("/api/v1/service-config") }

    // ==================== Auth ====================

    suspend fun getCurrentUser(): ApiResult<UserResponse> =
        apiClient.safeRequest {
            get("/api/v1/auth/me") {
                with(apiClient) { attachBearerToken() }
            }
        }

    suspend fun syncUser(fullName: String? = null): ApiResult<UserResponse> =
        apiClient.safeRequest {
            post("/api/v1/auth/sync") {
                with(apiClient) { attachBearerToken() }
                contentType(ContentType.Application.Json)
                setBody(SyncUserRequest(fullName = fullName))
            }
        }

    // ==================== Institutions ====================

    suspend fun getInstitutions(): ApiResult<List<InstitutionResponse>> =
        apiClient.safeRequest { get("/api/v1/institutions") }

    suspend fun getInstitutionById(institutionId: Int): ApiResult<InstitutionResponse> =
        apiClient.safeRequest { get("/api/v1/institutions/$institutionId") }

    suspend fun createInstitution(name: String, slug: String? = null): ApiResult<InstitutionResponse> =
        apiClient.safeRequest {
            post("/api/v1/institutions") {
                with(apiClient) { attachBearerToken() }
                contentType(ContentType.Application.Json)
                setBody(CreateInstitutionRequest(name = name, slug = slug))
            }
        }

    suspend fun updateInstitution(
        institutionId: Int,
        name: String? = null,
        status: String? = null,
    ): ApiResult<StatusResponse> =
        apiClient.safeRequest {
            put("/api/v1/institutions/$institutionId") {
                with(apiClient) { attachBearerToken() }
                contentType(ContentType.Application.Json)
                setBody(UpdateInstitutionRequest(name = name, status = status))
            }
        }

    suspend fun getInstitutionSettings(institutionId: Int): ApiResult<List<InstitutionSettingDto>> =
        apiClient.safeRequest {
            get("/api/v1/institutions/$institutionId/settings") {
                with(apiClient) { attachBearerToken() }
            }
        }

    suspend fun updateInstitutionSettings(
        institutionId: Int,
        settings: List<InstitutionSettingDto>,
    ): ApiResult<StatusResponse> =
        apiClient.safeRequest {
            put("/api/v1/institutions/$institutionId/settings") {
                with(apiClient) { attachBearerToken() }
                contentType(ContentType.Application.Json)
                setBody(UpdateInstitutionSettingsRequest(settings = settings))
            }
        }

    // ==================== RAG Files ====================

    suspend fun getRagFiles(institutionId: Int): ApiResult<List<RagFileDto>> =
        apiClient.safeRequest {
            get("/api/v1/institutions/$institutionId/rag-files") {
                with(apiClient) { attachBearerToken() }
            }
        }

    suspend fun uploadRagFile(
        institutionId: Int,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String = "application/octet-stream",
    ): ApiResult<RagFileDto> {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in ALLOWED_RAG_EXTENSIONS) {
            return ApiResult.Error("Format file tidak didukung. Gunakan: pdf, txt, md, atau docx.")
        }
        if (fileBytes.size > MAX_RAG_FILE_SIZE_BYTES) {
            return ApiResult.Error("Ukuran file melebihi batas maksimal 10 MB.")
        }

        return apiClient.safeRequest {
            submitFormWithBinaryData(
                url = "/api/v1/institutions/$institutionId/rag-files",
                formData = formData {
                    append(
                        key = "file",
                        value = fileBytes,
                        headers = Headers.build {
                            append(HttpHeaders.ContentType, mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        },
                    )
                },
            ) {
                with(apiClient) { attachBearerToken() }
                timeout {
                    requestTimeoutMillis = 60_000
                    socketTimeoutMillis = 60_000
                }
            }
        }
    }

    suspend fun deleteRagFile(institutionId: Int, fileId: Int): ApiResult<StatusResponse> =
        apiClient.safeRequest {
            delete("/api/v1/institutions/$institutionId/rag-files/$fileId") {
                with(apiClient) { attachBearerToken() }
            }
        }

    // ==================== Admin ====================

    suspend fun getAppSettings(): ApiResult<List<AppSettingDto>> =
        apiClient.safeRequest {
            get("/api/v1/admin/settings") {
                with(apiClient) { attachBearerToken() }
            }
        }

    suspend fun updateAppSetting(setting: AppSettingDto): ApiResult<StatusResponse> =
        apiClient.safeRequest {
            put("/api/v1/admin/settings") {
                with(apiClient) { attachBearerToken() }
                contentType(ContentType.Application.Json)
                setBody(setting)
            }
        }

    suspend fun getAdminUsers(): ApiResult<List<AdminUserDto>> =
        apiClient.safeRequest {
            get("/api/v1/admin/users") {
                with(apiClient) { attachBearerToken() }
            }
        }

    suspend fun updateUserRole(
        userId: String,
        request: UpdateUserRoleRequest,
    ): ApiResult<StatusResponse> =
        apiClient.safeRequest {
            patch("/api/v1/admin/users/$userId") {
                with(apiClient) { attachBearerToken() }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun createInvitation(request: CreateInvitationRequest): ApiResult<CreateInvitationResponse> =
        apiClient.safeRequest {
            post("/api/v1/admin/invitations") {
                with(apiClient) { attachBearerToken() }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun acceptInvitation(token: String): ApiResult<MessageResponse> =
        apiClient.safeRequest {
            post("/api/v1/admin/invitations/accept") {
                with(apiClient) { attachBearerToken() }
                contentType(ContentType.Application.Json)
                setBody(AcceptInvitationRequest(token = token))
            }
        }

    companion object {
        private val ALLOWED_RAG_EXTENSIONS = setOf("pdf", "txt", "md", "docx")
        private const val MAX_RAG_FILE_SIZE_BYTES = 10 * 1024 * 1024L
    }
}
