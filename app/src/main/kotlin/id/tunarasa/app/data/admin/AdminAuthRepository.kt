package id.tunarasa.app.data.admin

import android.util.Patterns
import id.tunarasa.app.data.session.AdminSessionInfo
import id.tunarasa.app.data.session.SessionStore
import kotlinx.coroutines.delay

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

        // TODO(BE-1): Ganti blok simulasi ini dengan POST /api/v1/auth/sign-in
        // setelah kontrak payload BE-1 difinalisasi.
        delay(500)

        val role = if (email.contains("super", ignoreCase = true)) "super_admin" else "admin"
        val mockToken = "dev_token_${System.currentTimeMillis()}"
        sessionStore.saveAdminSession(
            token = mockToken,
            email = email,
            role = role
        )

        return AdminAuthResult.Success(
            AdminSessionInfo(
                token = mockToken,
                email = email,
                role = role
            )
        )
    }

    suspend fun getActiveSession(): AdminSessionInfo? = sessionStore.getAdminSession()

    suspend fun signOut() {
        // TODO(BE-1): Panggil endpoint revoke/logout jika disediakan BE-1.
        sessionStore.clearAdminSession()
    }
}
