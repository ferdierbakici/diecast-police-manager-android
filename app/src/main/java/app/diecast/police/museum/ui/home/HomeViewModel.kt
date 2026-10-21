package app.diecast.police.museum.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.diecast.police.museum.data.model.Stats
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.data.repository.MuseumRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MuseumRepository
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val stats: Stats = Stats(),
        val recentlyInCollection: List<Vehicle> = emptyList(),
        val lastAdded: List<Vehicle> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val statsDeferred = async { repository.fetchStats() }
                val collectionDeferred = async { repository.fetchRecentlyInCollection(limit = 10) }
                val lastAddedDeferred = async { repository.fetchLastAdded(limit = 10) }

                val stats = statsDeferred.await()
                val collection = collectionDeferred.await()
                val lastAdded = lastAddedDeferred.await()

                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        stats = stats,
                        recentlyInCollection = collection,
                        lastAdded = lastAdded
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Veriler alinamadi.")
                }
            }
        }
    }
}
