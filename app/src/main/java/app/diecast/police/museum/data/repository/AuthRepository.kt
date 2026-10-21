package app.diecast.police.museum.data.repository

import android.content.Context
import app.diecast.police.museum.core.Config
import app.diecast.police.museum.core.Pkce
import app.diecast.police.museum.data.local.SessionStore
import app.diecast.police.museum.data.model.AuthErrorBody
import app.diecast.police.museum.data.model.AuthSession
import app.diecast.police.museum.data.model.Session
import app.diecast.police.museum.data.remote.Json
import app.diecast.police.museum.data.remote.RestClient
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed interface AuthResult {
    data class Success(val session: Session) : AuthResult
    data class NeedsEmailConfirmation(val email: String) : AuthResult
    data class Failure(val message: String) : AuthResult
}

class AuthRepository(
    context: Context,
    private val rest: RestClient,
    private val store: SessionStore
) {
    val sessionFlow: Flow<Session?> = store.sessionFlow

    /**
     * OAuth akisindan gelen mesaj (hata gibi). Ekranlar bunu izleyip gosterir.
     * Deep link MainActivity uzerinden gelir; ekrana buradan tasinir.
     */
    private val _authMessage = MutableStateFlow<String?>(null)
    val authMessage: StateFlow<String?> = _authMessage.asStateFlow()

    fun setAuthMessage(message: String?) { _authMessage.value = message }
    fun consumeAuthMessage() { _authMessage.value = null }

    /**
     * "Giris tamamlandi" olayi.
     *
     * Oturumun *durumundan* (session != null) giris *olayini* ayirt edemeyiz:
     * oturum kalici olarak saklandigi icin her sayfa acilisinda zaten vardir ve
     * akis null -> non-null gecisi yalnizca oturumun **yeni olustugu** anda
     * olusur. Bu yuzden ayri bir olay akisi tutulur.
     *
     * `replay = 0`: olay, o an dinleyen ekran varsa teslim edilir; yoksa duser
     * (ekran acilmadysa zaten navigate edilecek bir yer yoktur).
     */
    private val _signedInEvents = MutableSharedFlow<Session>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val signedInEvents: SharedFlow<Session> = _signedInEvents.asSharedFlow()

    private suspend fun notifySignedIn(session: Session) {
        _signedInEvents.emit(session)
    }

    // ------------------------------------------------------------------ //
    //  Giris / kayit
    // ------------------------------------------------------------------ //

    suspend fun signIn(email: String, password: String): AuthResult {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }.toString()
        val result = rest.post("${Config.AUTH_URL}/token?grant_type=password", body)
        return toResult(result, email)
    }

    suspend fun signUp(email: String, password: String, displayName: String): AuthResult {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
            put("data", buildJsonObject { put("display_name", displayName.trim()) })
        }.toString()
        val result = rest.post("${Config.AUTH_URL}/signup", body)
        return toResult(result, email)
    }

    /** OAuth donusunden gelen token'lari oturuma cevirir. */
    suspend fun signInWithTokens(
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Long
    ): AuthResult {
        val userResult = rest.getAbsolute(
            url = "${Config.AUTH_URL}/user",
            token = accessToken
        )
        if (!userResult.isSuccess) {
            return AuthResult.Failure("Kullanici bilgisi alinamadi (HTTP ${userResult.code}).")
        }
        val raw = userResult.body
        val id = runCatching {
            (Json.default.parseToJsonElement(raw) as? JsonObject)
                ?.get("id")?.jsonPrimitive?.content
        }.getOrNull() ?: return AuthResult.Failure("Oturum bilgisi alinamadi.")

        val email = runCatching {
            (Json.default.parseToJsonElement(raw) as? JsonObject)
                ?.get("email")?.jsonPrimitive?.content
        }.getOrNull()

        val session = Session(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000,
            userId = id,
            email = email,
            displayName = null
        )
        store.save(session)
        notifySignedIn(session)
        return AuthResult.Success(session)
    }

    suspend fun signOut() {
        val session = store.sessionFlow.firstOrNull()
        session?.let {
            runCatching { rest.post("${Config.AUTH_URL}/logout", token = it.accessToken) }
        }
        store.clear()
    }

    /** Oturum token'ini gecerliyse dondurur; gecmisse yeniler. */
    suspend fun validToken(): String? {
        val session = store.sessionFlow.firstOrNull() ?: return null
        if (!session.isExpired()) return session.accessToken

        val body = buildJsonObject { put("refresh_token", session.refreshToken) }.toString()
        val result = rest.post("${Config.AUTH_URL}/token?grant_type=refresh_token", body)
        if (!result.isSuccess) {
            store.clear()
            return null
        }
        val refreshed = runCatching {
            Json.default.decodeFromString<AuthSession>(result.body)
        }.getOrNull() ?: return null
        val access = refreshed.accessToken ?: return null
        val newSession = session.copy(
            accessToken = access,
            refreshToken = refreshed.refreshToken ?: session.refreshToken,
            expiresAt = System.currentTimeMillis() + (refreshed.expiresIn) * 1000
        )
        store.save(newSession)
        return newSession.accessToken
    }

    /**
     * Google OAuth baslatma URL'i (PKCE akisi).
     *
     * Uretilen `code_verifier` **kalici** olarak saklanir: OAuth tarayicida
     * tamamlanirken uygulama arka planda kalir ve sistem tarafindan
     * oldurulebilir. Verifier bellekte kalsaydi donuste "dogrulama verisi
     * bulunamadi" hatasi alirdik.
     */
    suspend fun googleOAuthUrl(): String {
        val verifier = Pkce.newCodeVerifier()
        store.savePkceVerifier(verifier)
        return Pkce.authorizeUrl(Config.AUTH_URL, Config.REDIRECT_URI, verifier)
    }

    /**
     * PKCE donusu: Supabase `?code=...` gonderir; token degisimi yapilir.
     *
     * Not: dogrulama verisi tek kullanimliktir; basarili/başarisiz her
     * denemeden sonra temizlenir.
     */
    suspend fun exchangeCodeForSession(code: String): AuthResult {
        val verifier = store.readPkceVerifier()
        store.clearPkceVerifier()
        if (verifier.isNullOrBlank()) {
            return AuthResult.Failure(
                "Giris dogrulama verisi bulunamadi. Lutfen tekrar deneyin."
            )
        }
        val body = buildJsonObject {
            put("auth_code", code)
            put("code_verifier", verifier)
        }.toString()
        val result = rest.post("${Config.AUTH_URL}/token?grant_type=pkce", body)
        return toResult(result, null)
    }

    // ------------------------------------------------------------------ //

    private suspend fun toResult(result: app.diecast.police.museum.data.remote.HttpResult, email: String?): AuthResult {
        if (!result.isSuccess) {
            val error = runCatching {
                Json.default.decodeFromString<AuthErrorBody>(result.body)
            }.getOrNull()
            val message = error?.errorDescription
                ?: error?.msg
                ?: error?.message
                ?: error?.error
                ?: "Giris basarisiz (HTTP ${result.code})"
            return AuthResult.Failure(message)
        }

        val session = runCatching {
            Json.default.decodeFromString<AuthSession>(result.body)
        }.getOrNull() ?: return AuthResult.Failure("Oturum yaniti okunamadi.")

        // E-posta dogrulama acikse session'siz gelir.
        val access = session.accessToken
        val refresh = session.refreshToken
        val userId = session.user?.id
        if (access.isNullOrBlank() || refresh.isNullOrBlank() || userId.isNullOrBlank()) {
            return if (email != null) AuthResult.NeedsEmailConfirmation(email)
            else AuthResult.Failure("Oturum yaniti eksik token iceriyor.")
        }

        val stored = Session(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = System.currentTimeMillis() + (session.expiresIn) * 1000,
            userId = userId,
            email = session.user?.email,
            displayName = session.user?.displayName
        )
        store.save(stored)
        notifySignedIn(stored)
        return AuthResult.Success(stored)
    }
}
