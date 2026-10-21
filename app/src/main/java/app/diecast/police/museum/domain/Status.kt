package app.diecast.police.museum.domain

/**
 * Web uygulamasindaki `getStatusDisplayLabel` / `getStatusFilterValues` mantiginin
 * birebir karsiligi. Kaynak: src/app/components/vehicle-types.tsx + src/app/page.tsx
 */
object Status {

    /** Web'deki "All" gondericisi (data/model/Filters.kt ile ayni deger). */
    private const val ALL_VALUE = "All"

    val COLLECTION = listOf("Available", "Available - Displayed", "In Stock", "Collection")
    val MISSING = listOf("Not Available", "Missing")

    private val collectionLower = COLLECTION.map { it.lowercase() }
    private val missingLower = MISSING.map { it.lowercase() }

    /** Gorunen etiket: Collection / Missing / digerleri kendi adiyla. */
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
     * Filtreye gidecek ham durum degerleri.
     * Web `getStatusFilterValues` ile birebir: "All" -> [] (filtre uygulanmaz).
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

    /** Koleksiyonda sayilan durumlar ("Recently in Collection" bolumu icin). */
    fun isCollectionStatus(status: String?): Boolean =
        status?.trim()?.lowercase()?.let { collectionLower.any { v -> v == it } } == true
}
