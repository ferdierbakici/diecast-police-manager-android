package app.diecast.police.museum.data.model

import app.diecast.police.museum.domain.EmergencyGroups

const val ALL = "All"

data class Filters(
    val search: String = "",
    val country: String = ALL,
    val manufacturer: String = ALL,
    val brand: String = ALL,
    val series: String = ALL,
    val serviceGroup: String = ALL,
    val service: String = ALL,
    val status: String = ALL
) {
    val isEmpty: Boolean
        get() = search.isBlank() &&
            country == ALL && manufacturer == ALL && brand == ALL && series == ALL &&
            serviceGroup == ALL && service == ALL && status == ALL

    /** The "Apply Filters" logic of the web app: at least one criterion must be selected. */
    val hasCriteria: Boolean get() = !isEmpty

    fun cleared() = Filters(search = search)

    /**
     * Returns a copy in which only the criterion of the given tag is set.
     *
     * The other criteria are reset (the same way "Clear filters" does it, the search
     * text is kept), so the result list shows exactly what the tapped tag stands for.
     */
    fun withTagFilter(tag: TagFilter): Filters = Filters(
        search = search,
        country = tag.country ?: ALL,
        manufacturer = tag.manufacturer ?: ALL,
        brand = tag.brand ?: ALL,
        series = tag.series ?: ALL,
        // The emergency service also needs its group, otherwise the filter panel
        // would not show the group of the selected service.
        serviceGroup = tag.service?.let { EmergencyGroups.groupOf(it) } ?: ALL,
        service = tag.service ?: ALL,
        status = ALL
    )
}

/**
 * A single filter criterion that was requested by tapping a tag
 * (country / brand / manufacturer / series / emergency service)
 * on the detail screen. `null` means "not selected".
 */
data class TagFilter(
    val country: String? = null,
    val manufacturer: String? = null,
    val brand: String? = null,
    val series: String? = null,
    val service: String? = null
) {
    val isEmpty: Boolean
        get() = country == null && manufacturer == null && brand == null &&
            series == null && service == null
}

data class FilterOptions(
    val countries: List<String> = emptyList(),
    val manufacturers: List<String> = emptyList(),
    val brands: List<String> = emptyList(),
    val series: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    /**
     * Series name -> series id. The series filter is applied on the `series_id` column
     * (the `series` embed is a plain join, a filter on it would be ignored by PostgREST).
     */
    val seriesIds: Map<String, Int> = emptyMap(),
    val loaded: Boolean = false
)

data class Stats(
    val totalAssets: Int = 0,
    val countries: Int = 0,
    val brands: Int = 0
)

/** A page of search results. */
data class PagedResult(
    val items: List<Vehicle> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val pageSize: Int = 50,
    val hasMore: Boolean = false
)
