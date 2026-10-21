package app.diecast.police.museum.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.data.repository.AuthRepository
import app.diecast.police.museum.data.repository.MuseumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CollectionViewModel(
    private val repository: MuseumRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        const val TAB_COLLECTION = "collection"
        const val TAB_WISHLIST = "wishlist"
    }

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val tab: String = TAB_COLLECTION,
        val collection: List<Vehicle> = emptyList(),
        val wishlist: List<Vehicle> = emptyList(),
        val signedIn: Boolean = false
    ) {
        val visible: List<Vehicle>
            get() = if (tab == TAB_WISHLIST) wishlist else collection
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val token = authRepository.validToken()
            val session = authRepository.sessionFlow.firstOrNull()
            if (token == null || session == null) {
                _state.update { it.copy(loading = false, signedIn = false, error = null) }
                return@launch
            }

            try {
                val collection = repository.fetchUserVehicles(
                    userId = session.userId,
                    status = TAB_COLLECTION,
                    token = token
                )
                val wishlist = repository.fetchUserVehicles(
                    userId = session.userId,
                    status = TAB_WISHLIST,
                    token = token
                )
                _state.update {
                    it.copy(
                        loading = false,
                        signedIn = true,
                        collection = collection,
                        wishlist = wishlist,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, signedIn = true, error = e.message)
                }
            }
        }
    }

    fun switchTab(tab: String) {
        _state.update { it.copy(tab = tab) }
    }
}
