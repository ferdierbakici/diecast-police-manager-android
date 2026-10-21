package app.diecast.police.museum.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.diecast.police.museum.data.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "museum_session"
)

/**
 * Oturum kalici olarak saklanir (DataStore). Dosya yedekleme kuralindan cikarilmistir
 * (bkz. res/xml/backup_rules.xml) - cihaz yedegiyle token tasinmaz.
 */
class SessionStore(private val context: Context) {

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES = longPreferencesKey("expires_at")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val NAME = stringPreferencesKey("display_name")

        /**
         * PKCE `code_verifier`.
         *
         * OAuth tarayicida (CustomTab) tamamlanirken uygulama arka planda kalir
         * ve sistem tarafindan oldurulebilir. Verifier bellekte tutulursa donuste
         * "dogrulama verisi bulunamadi" hatasi aliriz; bu yuzden kalici yazilir.
         */
        val PKCE_VERIFIER = stringPreferencesKey("pkce_code_verifier")
    }

    val sessionFlow: Flow<Session?> = context.sessionDataStore.data.map { prefs ->
        val access = prefs[Keys.ACCESS]
        val refresh = prefs[Keys.REFRESH]
        val userId = prefs[Keys.USER_ID]
        val expires = prefs[Keys.EXPIRES]
        if (access.isNullOrBlank() || refresh.isNullOrBlank() || userId.isNullOrBlank()) {
            null
        } else {
            Session(
                accessToken = access,
                refreshToken = refresh,
                expiresAt = expires ?: 0L,
                userId = userId,
                email = prefs[Keys.EMAIL],
                displayName = prefs[Keys.NAME]
            )
        }
    }

    suspend fun save(session: Session) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.ACCESS] = session.accessToken
            prefs[Keys.REFRESH] = session.refreshToken
            prefs[Keys.EXPIRES] = session.expiresAt
            prefs[Keys.USER_ID] = session.userId
            session.email?.let { prefs[Keys.EMAIL] = it }
            session.displayName?.let { prefs[Keys.NAME] = it }
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }

    /** PKCE dogrulama verisini kalici yazar (surec oldurulse bile korunsun). */
    suspend fun savePkceVerifier(verifier: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.PKCE_VERIFIER] = verifier
        }
    }

    /** Kayitli PKCE dogrulama verisini okur. */
    suspend fun readPkceVerifier(): String? {
        val value = context.sessionDataStore.data
            .map { prefs -> prefs[Keys.PKCE_VERIFIER] }
            .firstOrNull()
        return value?.takeIf { it.isNotBlank() }
    }

    /** Kullanildiktan sonra PKCE dogrulama verisini siler (tek kullanimliktir). */
    suspend fun clearPkceVerifier() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(Keys.PKCE_VERIFIER)
        }
    }
}
