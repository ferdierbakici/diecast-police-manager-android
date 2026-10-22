package app.diecast.police.museum.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.diecast.police.museum.data.model.ALL
import app.diecast.police.museum.data.model.FilterOptions
import app.diecast.police.museum.data.model.Filters
import app.diecast.police.museum.data.model.TagFilter
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.data.repository.MuseumRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: MuseumRepository,
    /** The filter the screen starts with (a tag that was tapped on the detail screen). */
    initialFilters: Filters = Filters()
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

    private val _state = MutableStateFlow(UiState(filters = initialFilters))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** The debounce of the search text. */
    private var debounceJob: Job? = null

    /** The running search. A new search cancels the previous one (the last one wins). */
    private var searchJob: Job? = null

    /** The running page load of the infinite scroll. A new search cancels it. */
    private var loadMoreJob: Job? = null

    init {
        loadOptions()
        search()
    }

    // ------------------------------------------------------------------ //

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(filters = it.filters.copy(search = query)) }
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(350) // debounce
            search()
        }
    }

    /** Applies the selections made in the filter panel. */
    fun applyFilters(filters: Filters) {
        _state.update { it.copy(filters = filters) }
        search()
    }

    /**
     * Applies a filter criterion that was requested by tapping a tag
     * (country / brand / manufacturer / series / emergency service) on the detail screen.
     */
    fun applyTagFilter(tag: TagFilter) {
        val current = _state.value.filters
        val target = current.withTagFilter(tag)
        // The screen may already start with this filter (then nothing has to be done).
        if (current == target) return
        _state.update { it.copy(filters = target) }
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
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
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
            } catch (e: CancellationException) {
                // The page load was cancelled (the screen was left) - not an error.
                throw e
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
        // Only the newest search may write its result (otherwise an older, slower
        // response could overwrite the newer one - e.g. after a tag was tapped).
        searchJob?.cancel()
        // A page load of the old result list must not be appended to the new one.
        loadMoreJob?.cancel()
        searchJob = viewModelScope.launch {
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
            } catch (e: CancellationException) {
                // The search was replaced by a newer one - that is not an error.
                // The state must not be touched here, the newer search owns it.
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Search failed.")
                }
            }
        }
    }
}

/** Is there any selection other than "All" in the filter panel? */
fun Filters.hasNonDefaultSelection(): Boolean =
    country != ALL || manufacturer != ALL || brand != ALL || series != ALL ||
        serviceGroup != ALL || service != ALL || status != ALL
