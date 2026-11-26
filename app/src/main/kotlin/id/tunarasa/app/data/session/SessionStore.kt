package id.tunarasa.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import id.tunarasa.app.ui.chat.UiChatMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "tunarasa_session")

class SessionStore(private val context: Context) {
    companion object {
        private val KEY_SESSION_ID = stringPreferencesKey("x_session_id")
        private val KEY_ADMIN_TOKEN = stringPreferencesKey("admin_bearer_token")
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
}

/** Menyimpan riwayat sesi obrolan terakhir di memori untuk pembuatan ringkasan lokal */
object ActiveConversationCache {
    var institutionName: String = "Layanan Publik"
    var messages: List<UiChatMessage> = emptyList()

    fun update(institution: String, currentMessages: List<UiChatMessage>) {
        institutionName = institution
        messages = currentMessages.toList()
    }
}
