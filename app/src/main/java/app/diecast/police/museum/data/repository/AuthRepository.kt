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
     * A message coming from the OAuth flow (an error, for example). The screens
     * observe it and show it. It arrives as a deep link through MainActivity
     * and is forwarded from there.
     */
    private val _authMessage = MutableStateFlow<String?>(null)
    val authMessage: StateFlow<String?> = _authMessage.asStateFlow()

    fun setAuthMessage(message: String?) { _authMessage.value = message }
    fun consumeAuthMessage() { _authMessage.value = null }

    /**
     * The "sign-in completed" event.
     *
     * The *state* of the session (session != null) cannot tell us about a sign-in
     * *event*: the session is persisted, so it already exists on every screen open
     * and the null -> non-null transition only happens when the session is
     * **newly created**. That is why a separate event flow is kept.
     *
     * `replay = 0`: the event is delivered if a screen is listening at that moment;
     * otherwise it is dropped (if no screen is open there is nowhere to navigate to).
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
    //  Sign in / sign up
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

    /** Turns the tokens returned by OAuth into a session. */
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
            return AuthResult.Failure("Could not load user info (HTTP ${userResult.code}).")
        }
        val raw = userResult.body
        val id = runCatching {
            (Json.default.parseToJsonElement(raw) as? JsonObject)
                ?.get("id")?.jsonPrimitive?.content
        }.getOrNull() ?: return AuthResult.Failure("Could not load session info.")

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

    /** Returns the session token if it is still valid; refreshes it when it has expired. */
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
     * The Google OAuth start URL (PKCE flow).
     *
     * The generated `code_verifier` is stored **persistently**: while OAuth
     * completes in the browser the app stays in the background and the system
     * may kill it. If the verifier only lived in memory we would get a
     * "verification data not found" error on return.
     */
    suspend fun googleOAuthUrl(): String {
        val verifier = Pkce.newCodeVerifier()
        store.savePkceVerifier(verifier)
        return Pkce.authorizeUrl(Config.AUTH_URL, Config.REDIRECT_URI, verifier)
    }

    /**
     * PKCE return: Supabase sends `?code=...`; the tokens are exchanged.
     *
     * Note: the verification data is single use; it is cleared after every
     * attempt, successful or not.
     */
    suspend fun exchangeCodeForSession(code: String): AuthResult {
        val verifier = store.readPkceVerifier()
        store.clearPkceVerifier()
        if (verifier.isNullOrBlank()) {
            return AuthResult.Failure(
                "Login verification data not found. Please try again."
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
                ?: "Sign-in failed (HTTP ${result.code})"
            return AuthResult.Failure(message)
        }

        val session = runCatching {
            Json.default.decodeFromString<AuthSession>(result.body)
        }.getOrNull() ?: return AuthResult.Failure("Could not read the session response.")

        // When email confirmation is enabled the session arrives without one.
        val access = session.accessToken
        val refresh = session.refreshToken
        val userId = session.user?.id
        if (access.isNullOrBlank() || refresh.isNullOrBlank() || userId.isNullOrBlank()) {
            return if (email != null) AuthResult.NeedsEmailConfirmation(email)
            else AuthResult.Failure("Session response is missing a token.")
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
