package id.tunarasa.app.data

import kotlinx.serialization.Serializable

// --- Common Responses ---

@Serializable
data class ApiErrorResponse(
    val error: String,
)

@Serializable
data class StatusResponse(
    val status: String,
)

@Serializable
data class MessageResponse(
    val message: String,
)

// --- Health ---

@Serializable
data class HealthStatusDto(
    val status: String,
    val database: String,
    val redis: String,
    val timestamp: Long = 0L,
)

// --- Public Config & Admin ---

@Serializable
data class ServiceConfigResponse(
    val serviceMode: String,
    val maxWordLength: Int = 50,
    val maxQueryLength: Int = 500,
)

@Serializable
data class AppSettingDto(
    val key: String,
    val value: String,
    val description: String? = null,
)

@Serializable
data class AdminUserDto(
    val id: String,
    val email: String,
    val roleName: String,
    val institutionId: Int? = null,
    val fullName: String? = null,
    val status: String,
)

@Serializable
data class UpdateUserRoleRequest(
    val roleName: String? = null,
    val institutionId: Int? = null,
    val status: String? = null,
)

@Serializable
data class CreateInvitationRequest(
    val email: String,
    val roleName: String = "institution_admin",
    val institutionId: Int? = null,
)

@Serializable
data class CreateInvitationResponse(
    val token: String,
    val status: String,
    val expiresAt: String,
)

@Serializable
data class AcceptInvitationRequest(
    val token: String,
)

// --- Auth ---

@Serializable
data class SyncUserRequest(
    val fullName: String? = null,
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val role: String,
    val institutionId: Int? = null,
    val fullName: String? = null,
    val status: String,
)

// --- Institutions & RAG Files ---

@Serializable
data class InstitutionResponse(
    val id: Int,
    val name: String,
    val slug: String,
    val status: String,
)

@Serializable
data class CreateInstitutionRequest(
    val name: String,
    val slug: String? = null,
)

@Serializable
data class UpdateInstitutionRequest(
    val name: String? = null,
    val status: String? = null,
)

@Serializable
data class InstitutionSettingDto(
    val key: String,
    val value: String,
)

@Serializable
data class UpdateInstitutionSettingsRequest(
    val settings: List<InstitutionSettingDto>,
)

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
