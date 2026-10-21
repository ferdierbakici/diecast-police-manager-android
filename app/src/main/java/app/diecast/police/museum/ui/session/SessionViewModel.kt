package app.diecast.police.museum.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.diecast.police.museum.data.model.Session
import app.diecast.police.museum.data.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Uygulama genelinde oturum durumu. */
class SessionViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val session: StateFlow<Session?> = authRepository.sessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
