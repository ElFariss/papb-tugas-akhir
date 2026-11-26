package id.tunarasa.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "tunarasa_session")

data class AdminSessionInfo(
    val token: String,
    val email: String,
    val role: String
)

class SessionStore(private val context: Context) {
    companion object {
        private val KEY_SESSION_ID = stringPreferencesKey("x_session_id")
        private val KEY_ADMIN_TOKEN = stringPreferencesKey("admin_bearer_token")
        private val KEY_ADMIN_EMAIL = stringPreferencesKey("admin_email")
        private val KEY_ADMIN_ROLE = stringPreferencesKey("admin_role")
    }

    /** Mengambil X-Session-Id persisten atau membuat baru sesuai regex ^[a-zA-Z0-9_-]+$ */
    suspend fun getOrCreateSessionId(): String {
        val existing = context.sessionDataStore.data
            .map { prefs -> prefs[KEY_SESSION_ID] }
            .first()

        if (!existing.isNullOrBlank()) return existing

        val generated = "sess_${UUID.randomUUID().toString().replace("-", "")}"
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_SESSION_ID] = generated
        }
        return generated
    }

    suspend fun getAdminToken(): String? {
        return context.sessionDataStore.data
            .map { prefs -> prefs[KEY_ADMIN_TOKEN] }
            .first()
    }

    suspend fun saveAdminToken(token: String?) {
        context.sessionDataStore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(KEY_ADMIN_TOKEN)
            } else {
                prefs[KEY_ADMIN_TOKEN] = token
            }
        }
    }

    suspend fun saveAdminSession(token: String, email: String, role: String = "admin") {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_ADMIN_TOKEN] = token
            prefs[KEY_ADMIN_EMAIL] = email
            prefs[KEY_ADMIN_ROLE] = role
        }
    }

    suspend fun getAdminSession(): AdminSessionInfo? {
        return context.sessionDataStore.data.map { prefs ->
            val token = prefs[KEY_ADMIN_TOKEN]
            if (token.isNullOrBlank()) {
                null
            } else {
                AdminSessionInfo(
                    token = token,
                    email = prefs[KEY_ADMIN_EMAIL].orEmpty(),
                    role = prefs[KEY_ADMIN_ROLE] ?: "admin"
                )
            }
        }.first()
    }

    suspend fun clearAdminSession() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(KEY_ADMIN_TOKEN)
            prefs.remove(KEY_ADMIN_EMAIL)
            prefs.remove(KEY_ADMIN_ROLE)
        }
    }
}
