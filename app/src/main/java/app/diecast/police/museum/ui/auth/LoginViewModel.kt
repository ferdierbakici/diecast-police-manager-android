package app.diecast.police.museum.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.diecast.police.museum.data.model.Session
import app.diecast.police.museum.data.repository.AuthRepository
import app.diecast.police.museum.data.repository.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val info: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** The active session (including the OAuth return). */
    val session: StateFlow<Session?> = authRepository.sessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The "sign-in completed" event.
     *
     * It reports the moment a session is **newly created**, not the presence of
     * a session, so that a user who is already signed in does not see the screen
     * close every time they open the "Account" tab.
     */
    val signedInEvents: SharedFlow<Session> = authRepository.signedInEvents

    /** An OAuth message arriving through the deep link (an error, for example). */
    val authMessage: StateFlow<String?> = authRepository.authMessage

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Email and password are required.") }
            return
        }
        submit { authRepository.signIn(email, password) }
    }

    fun signUp(email: String, password: String, displayName: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Email and password are required.") }
            return
        }
        if (password.length < 6) {
            _state.update { it.copy(error = "Password must be at least 6 characters.") }
            return
        }
        submit { authRepository.signUp(email, password, displayName) }
    }

    /** Signs in with the tokens returned by OAuth. */
    fun signInWithTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        submit {
            authRepository.signInWithTokens(accessToken, refreshToken, expiresIn)
        }
    }

    /**
     * The Google OAuth start URL (PKCE).
     *
     * While the URL is generated the `code_verifier` is written persistently; the
     * flow completed in the browser is exchanged for a token with that data.
     */
    private val _googleUrl = MutableStateFlow<String?>(null)
    val googleUrl: StateFlow<String?> = _googleUrl.asStateFlow()

    fun startGoogleLogin() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, info = null) }
            _googleUrl.value = authRepository.googleOAuthUrl()
            _state.update { it.copy(loading = false) }
        }
    }

    /** Clears the URL once it has been sent to the browser (one shot). */
    fun consumeGoogleUrl() {
        _googleUrl.value = null
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /** Forwards an OAuth error message arriving through the deep link to the screen. */
    fun showAuthMessage(message: String) {
        _state.update { it.copy(error = message, info = null) }
        authRepository.consumeAuthMessage()
    }

    private fun submit(block: suspend () -> AuthResult) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, info = null) }
            when (val result = block()) {
                is AuthResult.Success ->
                    _state.update { it.copy(loading = false) }
                is AuthResult.NeedsEmailConfirmation ->
                    _state.update {
                        it.copy(
                            loading = false,
                            info = "Account created. Activate it with the verification link " +
                                "sent to your email address, then sign in."
                        )
                    }
                is AuthResult.Failure ->
                    _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, info = null) }
    }
}
