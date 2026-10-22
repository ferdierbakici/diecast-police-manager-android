package app.diecast.police.museum.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.diecast.police.museum.data.model.ALL
import app.diecast.police.museum.data.model.FilterOptions
import app.diecast.police.museum.data.model.Filters
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.data.repository.MuseumRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: MuseumRepository
) : ViewModel() {

    data class UiState(
        val filters: Filters = Filters(),
        val results: List<Vehicle> = emptyList(),
        val total: Int = 0,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val options: FilterOptions = FilterOptions(),
        val optionsLoading: Boolean = false,
        val hasMore: Boolean = false,
        val page: Int = 0
    ) {
        val pageSize: Int get() = 50
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadOptions()
        search()
    }

    // ------------------------------------------------------------------ //

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(filters = it.filters.copy(search = query)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350) // debounce
            search()
        }
    }

    /** Filtre panelindeki secimleri uygular. */
    fun applyFilters(filters: Filters) {
        _state.update { it.copy(filters = filters) }
        search()
    }

    fun clearFilters() {
        _state.update { it.copy(filters = Filters(search = it.filters.search)) }
        search()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore) return

        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val nextPage = current.page + 1
                val result = repository.searchVehicles(
                    filters = current.filters,
                    page = nextPage,
                    pageSize = current.pageSize
                )
                _state.update {
                    it.copy(
                        results = it.results + result.items,
                        total = result.total,
                        hasMore = result.hasMore,
                        page = nextPage,
                        loadingMore = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, error = e.message) }
            }
        }
    }

    fun loadOptions() {
        if (_state.value.options.loaded || _state.value.optionsLoading) return
        _state.update { it.copy(optionsLoading = true) }
        viewModelScope.launch {
            try {
                val options = repository.fetchFilterOptions()
                _state.update { it.copy(options = options, optionsLoading = false) }
            } catch (_: Exception) {
                _state.update { it.copy(optionsLoading = false) }
            }
        }
    }

    fun retry() {
        _state.update { it.copy(error = null) }
        search()
    }

    // ------------------------------------------------------------------ //

    private fun search() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, page = 0) }
            try {
                val result = repository.searchVehicles(
                    filters = _state.value.filters,
                    page = 0,
                    pageSize = _state.value.pageSize
                )
                _state.update {
                    it.copy(
                        results = result.items,
                        total = result.total,
                        hasMore = result.hasMore,
                        loading = false,
                        error = null,
                        page = 0
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Search failed.")
                }
            }
        }
    }
}

/** Filtre panelinde "Tumu" degeri disinda secim var mi? */
fun Filters.hasNonDefaultSelection(): Boolean =
    country != ALL || manufacturer != ALL || brand != ALL ||
        serviceGroup != ALL || service != ALL || status != ALL
