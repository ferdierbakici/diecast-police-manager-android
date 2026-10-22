package app.diecast.police.museum.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.diecast.police.museum.data.model.ALL
import app.diecast.police.museum.data.model.Filters
import app.diecast.police.museum.data.model.TagFilter
import app.diecast.police.museum.ui.appViewModel
import app.diecast.police.museum.ui.components.EmptyState
import app.diecast.police.museum.ui.components.ErrorState
import app.diecast.police.museum.ui.components.LoadingState
import app.diecast.police.museum.ui.components.VehicleListItem
import app.diecast.police.museum.ui.theme.MuseumInkSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVehicleClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * A filter criterion that was requested by tapping a tag on the detail screen.
     * It is applied as soon as the screen appears and then consumed.
     */
    pendingFilter: TagFilter? = null,
    onPendingFilterConsumed: () -> Unit = {}
) {
    // The screen starts with the filter of a tapped tag (no second search is needed then).
    val viewModel: SearchViewModel = appViewModel { container ->
        val tag = pendingFilter
        SearchViewModel(
            repository = container.museumRepository,
            initialFilters = if (tag == null) Filters() else Filters().withTagFilter(tag)
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // A tapped tag switches to the search screen with the matching filter applied.
    // (A filter that arrives later - while the screen already exists - is applied here.)
    LaunchedEffect(pendingFilter) {
        val filter = pendingFilter ?: return@LaunchedEffect
        viewModel.applyTagFilter(filter)
        onPendingFilterConsumed()
    }

    // Infinite scroll: load the next page when the end is near.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= state.results.size - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Column(modifier = modifier.fillMaxSize()) {

        // ---- Search field ----
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = state.filters.search,
                onValueChange = viewModel::onSearchQueryChange,
                singleLine = true,
                placeholder = { Text("Search model...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filters.search.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BadgedBox(
                    badge = {
                        val active = state.filters.activeFilterCount()
                        if (active > 0) {
                            Badge { Text(active.toString()) }
                        }
                    }
                ) {
                    FilterChip(
                        selected = state.filters.hasNonDefaultSelection(),
                        onClick = { sheetOpen = true },
                        label = { Text("Filters") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.FilterList,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    )
                }

                if (state.filters.hasNonDefaultSelection()) {
                    FilterChip(
                        selected = false,
                        onClick = viewModel::clearFilters,
                        label = { Text("Clear filters") }
                    )
                }

                Box(modifier = Modifier.weight(1f))

                if (!state.loading) {
                    Text(
                        text = "${state.total} results",
                        style = MaterialTheme.typography.labelMedium,
                        color = MuseumInkSoft
                    )
                }
            }
        }

        // ---- Results ----
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.loading && state.results.isEmpty() -> LoadingState(label = "Searching...")

                state.error != null && state.results.isEmpty() -> ErrorState(
                    message = state.error ?: "Search failed.",
                    onRetry = viewModel::retry
                )

                state.results.isEmpty() -> EmptyState(
                    title = "No results found",
                    subtitle = "Try a different search term or change the filters."
                )

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.results, key = { it.id }) { vehicle ->
                        VehicleListItem(
                            vehicle = vehicle,
                            onClick = { onVehicleClick(vehicle.id) }
                        )
                    }

                    if (state.loadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (sheetOpen) {
        FilterSheet(
            initial = state.filters,
            options = state.options,
            onDismiss = { sheetOpen = false },
            onApply = {
                viewModel.applyFilters(it)
                sheetOpen = false
            },
            onClear = {
                viewModel.clearFilters()
                sheetOpen = false
            }
        )
    }
}

private fun app.diecast.police.museum.data.model.Filters.activeFilterCount(): Int {
    var n = 0
    if (country != ALL) n++
    if (manufacturer != ALL) n++
    if (brand != ALL) n++
    if (series != ALL) n++
    if (serviceGroup != ALL) n++
    if (service != ALL) n++
    if (status != ALL) n++
    return n
}
