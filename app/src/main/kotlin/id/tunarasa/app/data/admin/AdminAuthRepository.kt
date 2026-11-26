package id.tunarasa.app.data.admin

import android.util.Patterns
import id.tunarasa.app.BuildConfig
import id.tunarasa.app.data.ApiClient
import id.tunarasa.app.data.ApiResult
import id.tunarasa.app.data.PlatformApi
import id.tunarasa.app.data.session.AdminSessionInfo
import id.tunarasa.app.data.session.SessionStore
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface AdminAuthResult {
    data class Success(val session: AdminSessionInfo) : AdminAuthResult
    data class Error(val message: String) : AdminAuthResult
}

class AdminAuthRepository(
    private val sessionStore: SessionStore
) {
    suspend fun signIn(rawEmail: String, password: String): AdminAuthResult {
        val email = rawEmail.trim()

        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return AdminAuthResult.Error("Format email tidak valid.")
        }
        if (password.length < 6) {
            return AdminAuthResult.Error("Kata sandi minimal 6 karakter.")
        }

        val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val publicKey = BuildConfig.SUPABASE_PUBLIC_KEY
        if (supabaseUrl.isBlank() || publicKey.isBlank()) {
            return AdminAuthResult.Error("Konfigurasi login admin belum tersedia.")
        }

        val supabase = ApiClient(baseUrl = supabaseUrl, enableLogging = false)
        val tokenResult: ApiResult<SupabaseToken> = try {
            supabase.safeRequest {
                post("/auth/v1/token?grant_type=password") {
                    header("apikey", publicKey)
                    contentType(ContentType.Application.Json)
                    setBody(PasswordRequest(email, password))
                }
            }
        } finally {
            supabase.close()
        }
        val token = when (tokenResult) {
            is ApiResult.Success -> tokenResult.data.accessToken
            is ApiResult.Error -> return AdminAuthResult.Error(
                if (tokenResult.statusCode in listOf(400, 401)) "Email atau kata sandi tidak valid."
                else tokenResult.message
            )
        }

        val backend = ApiClient(tokenProvider = { token }, enableLogging = false)
        val profile = try {
            val platform = PlatformApi(backend)
            when (val current = platform.getCurrentUser()) {
                is ApiResult.Success -> current
                is ApiResult.Error -> if (current.statusCode == 404) platform.syncUser() else current
            }
        } finally {
            backend.close()
        }
        val user = when (profile) {
            is ApiResult.Success -> profile.data
            is ApiResult.Error -> return AdminAuthResult.Error(profile.message)
        }
        if (!isActiveAdmin(user.status, user.role)) {
            return AdminAuthResult.Error("Akun ini tidak memiliki akses admin aktif.")
        }
        sessionStore.saveAdminSession(token, user.email, user.role)
        return AdminAuthResult.Success(AdminSessionInfo(token, user.email, user.role))
    }

    suspend fun getActiveSession(): AdminSessionInfo? {
        val session = sessionStore.getAdminSession() ?: return null
        val backend = ApiClient(tokenProvider = { session.token }, enableLogging = false)
        try {
            return when (val result = PlatformApi(backend).getCurrentUser()) {
                is ApiResult.Success -> {
                    val user = result.data
                    if (isActiveAdmin(user.status, user.role)) {
                        sessionStore.saveAdminSession(session.token, user.email, user.role)
                        AdminSessionInfo(session.token, user.email, user.role)
                    } else {
                        sessionStore.clearAdminSession()
                        null
                    }
                }
                is ApiResult.Error -> {
                    if (result.statusCode in listOf(401, 403, 404)) sessionStore.clearAdminSession()
                    null
                }
            }
        } finally {
            backend.close()
        }
    }

    suspend fun signOut() {
        sessionStore.clearAdminSession()
    }

    private fun isActiveAdmin(status: String, role: String): Boolean =
        status == "active" && role in setOf("super_admin", "institution_admin")
}

@Serializable
private data class PasswordRequest(val email: String, val password: String)

@Serializable
private data class SupabaseToken(@SerialName("access_token") val accessToken: String)
