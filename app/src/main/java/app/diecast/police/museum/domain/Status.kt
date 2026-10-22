package app.diecast.police.museum.domain

/**
 * An exact copy of the `getStatusDisplayLabel` / `getStatusFilterValues` logic
 * of the web app. Source: src/app/components/vehicle-types.tsx + src/app/page.tsx
 */
object Status {

    /** The "All" sender of the web app (same value as data/model/Filters.kt). */
    private const val ALL_VALUE = "All"

    val COLLECTION = listOf("Available", "Available - Displayed", "In Stock", "Collection")
    val MISSING = listOf("Not Available", "Missing")

    private val collectionLower = COLLECTION.map { it.lowercase() }
    private val missingLower = MISSING.map { it.lowercase() }

    /** The visible label: Collection / Missing / everything else keeps its own name. */
    fun displayLabel(status: String?): String {
        val normalized = status?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return "Missing"
        if (collectionLower.any { it == normalized }) return "Collection"
        if (missingLower.any { it == normalized }) return "Missing"
        if (normalized == "collection") return "Collection"
        if (normalized == "missing") return "Missing"
        return status?.trim() ?: "Unknown"
    }

    /**
     * The raw status values that go into the filter.
     * Exactly as in the web app's `getStatusFilterValues`: "All" -> [] (no filter applied).
     */
    fun filterValues(status: String?): List<String> {
        val normalized = status?.trim().orEmpty()
        return when {
            normalized.isEmpty() || normalized == ALL_VALUE -> emptyList()
            normalized == "Collection" -> COLLECTION
            normalized == "Missing" -> MISSING
            else -> listOf(normalized)
        }
    }

    /** The statuses counted as "in the collection" (for the "Recently in Collection" section). */
    fun isCollectionStatus(status: String?): Boolean =
        status?.trim()?.lowercase()?.let { collectionLower.any { v -> v == it } } == true
}
