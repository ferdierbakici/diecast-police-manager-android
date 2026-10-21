package app.diecast.police.museum.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.diecast.police.museum.data.repository.AuthRepository
import app.diecast.police.museum.data.repository.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val info: String? = null,
        val signedIn: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

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

    private fun submit(block: suspend () -> AuthResult) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, info = null) }
            when (val result = block()) {
                is AuthResult.Success ->
                    _state.update { it.copy(loading = false, signedIn = true) }
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

    fun consumeNavigation() {
        _state.update { it.copy(signedIn = false) }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, info = null) }
    }
}
