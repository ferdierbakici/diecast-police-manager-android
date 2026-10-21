package app.diecast.police.museum.data.model

const val ALL = "All"

data class Filters(
    val search: String = "",
    val country: String = ALL,
    val manufacturer: String = ALL,
    val brand: String = ALL,
    val serviceGroup: String = ALL,
    val service: String = ALL,
    val status: String = ALL
) {
    val isEmpty: Boolean
        get() = search.isBlank() &&
            country == ALL && manufacturer == ALL && brand == ALL &&
            serviceGroup == ALL && service == ALL && status == ALL

    /** Web uygulamasindaki "Apply Filters" mantigi: en az bir kriter secilmeli. */
    val hasCriteria: Boolean get() = !isEmpty

    fun cleared() = Filters(search = search)
}

data class FilterOptions(
    val countries: List<String> = emptyList(),
    val manufacturers: List<String> = emptyList(),
    val brands: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val loaded: Boolean = false
)

data class Stats(
    val totalAssets: Int = 0,
    val countries: Int = 0,
    val brands: Int = 0
)

/** Sayfali arama sonucu. */
data class PagedResult(
    val items: List<Vehicle> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val pageSize: Int = 50,
    val hasMore: Boolean = false
)
