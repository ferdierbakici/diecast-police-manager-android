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

    /** Aktif oturum (OAuth donusu dahil). */
    val session: StateFlow<Session?> = authRepository.sessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * "Giris tamamlandi" olayi.
     *
     * Oturumun varligi degil, oturumun **yeni olustugu an** bildirilir; boylece
     * oturumu olan kullanici "Hesap" sekmesine her girdiginde ekran kapanmaz.
     */
    val signedInEvents: SharedFlow<Session> = authRepository.signedInEvents

    /** Deep link ile gelen OAuth mesaji (hata gibi). */
    val authMessage: StateFlow<String?> = authRepository.authMessage

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "E-posta ve sifre gerekli.") }
            return
        }
        submit { authRepository.signIn(email, password) }
    }

    fun signUp(email: String, password: String, displayName: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "E-posta ve sifre gerekli.") }
            return
        }
        if (password.length < 6) {
            _state.update { it.copy(error = "Sifre en az 6 karakter olmali.") }
            return
        }
        submit { authRepository.signUp(email, password, displayName) }
    }

    /** OAuth donusunden gelen token ile giris. */
    fun signInWithTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        submit {
            authRepository.signInWithTokens(accessToken, refreshToken, expiresIn)
        }
    }

    /**
     * Google OAuth baslatma URL'i (PKCE).
     *
     * URL uretilirken `code_verifier` kalici olarak yazilir; tarayicida
     * tamamlanan akis donuste bu veriyle token'a degistirilir.
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

    /** Tarayiciya gonderildikten sonra URL'yi temizler (tek seferlik). */
    fun consumeGoogleUrl() {
        _googleUrl.value = null
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /** Deep link ile gelen OAuth hata mesajini ekrana tasi. */
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
                            info = "Hesap olusturuldu. E-posta adresinize gelen dogrulama baglantisiyla " +
                                "aktivin, sonra giris yapin."
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
