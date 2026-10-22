package app.diecast.police.museum.data.repository

import app.diecast.police.museum.core.Config
import app.diecast.police.museum.data.model.ALL
import app.diecast.police.museum.data.model.FilterOptions
import app.diecast.police.museum.data.model.Filters
import app.diecast.police.museum.data.model.PagedResult
import app.diecast.police.museum.data.model.Stats
import app.diecast.police.museum.data.model.StatusCandidate
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.data.remote.Json
import app.diecast.police.museum.data.remote.RestClient
import app.diecast.police.museum.domain.EmergencyGroups
import app.diecast.police.museum.domain.Status
import app.diecast.police.museum.domain.Timestamps
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A lightweight record for the counts. */
@Serializable
private data class StatusPair(
    @SerialName("emergency_service") val emergencyService: String? = null,
    @SerialName("availability_status") val availabilityStatus: String? = null
)

@Serializable
data class ModelCounts(
    @SerialName("collection_count") val collectionCount: Int = 0,
    @SerialName("wishlist_count") val wishlistCount: Int = 0
)

class MuseumRepository(private val rest: RestClient) {

    private var cachedOptions: FilterOptions? = null

    /** Only one request may load the filter options (they are needed by every search). */
    private val optionsMutex = Mutex()

    // ------------------------------------------------------------------ //
    //  Statistics
    // ------------------------------------------------------------------ //

    suspend fun fetchStats(): Stats {
        val total = countOf("vehicles")
        val countries = countOf("countries")
        val brands = countOf("vehicle_brands")
        return Stats(totalAssets = total, countries = countries, brands = brands)
    }

    private suspend fun countOf(table: String): Int {
        val result = rest.get(
            path = "/$table",
            params = mapOf("select" to "id"),
            headers = mapOf("Prefer" to "count=exact", "Range" to "0-0")
        )
        return if (result.isSuccess) result.totalCount else 0
    }

    // ------------------------------------------------------------------ //
    //  Home screen sections
    // ------------------------------------------------------------------ //

    /** "Last 10 added models" - same as the web app: order=id.desc (not created_at). */
    suspend fun fetchLastAdded(limit: Int = 10): List<Vehicle> {
        val result = rest.get(
            path = "/vehicles",
            params = mapOf(
                "select" to Config.VEHICLE_SELECT,
                "order" to "id.desc",
                "limit" to limit.toString()
            )
        )
        return decodeVehicles(result.body)
    }

    /**
     * "Recently in Collection" - exactly the same 10 records as the web app.
     *
     * 1) A server-side `or` filter narrows the candidates from 1042 to 74.
     * 2) The sorting is done on the client (PostgREST's multi-column order= does
     *    not produce the same result as the web app).
     * 3) The detailed records of the selected 10 ids are fetched.
     */
    suspend fun fetchRecentlyInCollection(limit: Int = 10): List<Vehicle> {
        val collectionIn = Status.COLLECTION.joinToString(",")
        val notCollection = Status.COLLECTION.joinToString(",")
        val result = rest.get(
            path = "/vehicles",
            params = mapOf(
                "select" to "id,previous_status,status_changed_at,created_at,updated_at",
                "availability_status" to "in.($collectionIn)",
                "or" to "(previous_status.is.null,previous_status.not.in.($notCollection))",
                "limit" to "2000"
            )
        )
        if (!result.isSuccess) return emptyList()

        val candidates = Json.default.decodeFromString<List<StatusCandidate>>(result.body)
        val topIds = candidates
            .sortedWith(
                compareByDescending<StatusCandidate> {
                    Timestamps.resolvedMillis(it.statusChangedAt, it.createdAt, it.updatedAt)
                }
            )
            .take(limit)
            .map { it.id }

        if (topIds.isEmpty()) return emptyList()

        val detail = rest.get(
            path = "/vehicles",
            params = mapOf(
                "select" to Config.VEHICLE_SELECT,
                "id" to "in.(${topIds.joinToString(",")})"
            )
        )
        if (!detail.isSuccess) return emptyList()

        // PostgREST returns `id=in.(...)` results in id order; therefore the
        // "most recently added to the collection" order computed in step 2 has to be
        // RESTORED here. (Adding order=id.desc would completely break this ordering.)
        val byId = decodeVehicles(detail.body).associateBy { it.id }
        return topIds.mapNotNull { byId[it] }
    }

    // ------------------------------------------------------------------ //
    //  Search
    // ------------------------------------------------------------------ //

    suspend fun searchVehicles(
        filters: Filters,
        page: Int = 0,
        pageSize: Int = 50
    ): PagedResult {
        // The group filter needs every service value, so load the options first if they are missing.
        if (cachedOptions == null) fetchFilterOptions()
        val params = buildSearchParams(
            filters = filters,
            page = page,
            pageSize = pageSize,
            options = cachedOptions ?: FilterOptions()
        )
        val result = rest.get(
            path = "/vehicles",
            params = params,
            headers = mapOf("Prefer" to "count=exact")
        )
        if (!result.isSuccess) {
            throw MuseumException(result.errorMessage)
        }
        val items = decodeVehicles(result.body)
        val total = result.totalCount
        return PagedResult(
            items = items,
            total = total,
            page = page,
            pageSize = pageSize,
            hasMore = (page + 1) * pageSize < total
        )
    }

    // ------------------------------------------------------------------ //
    //  Detail
    // ------------------------------------------------------------------ //

    suspend fun fetchVehicle(id: Int): Vehicle? {
        val result = rest.get(
            path = "/vehicles",
            params = mapOf(
                "select" to Config.VEHICLE_SELECT,
                "id" to "eq.$id",
                "limit" to "1"
            )
        )
        if (!result.isSuccess) return null
        return decodeVehicles(result.body).firstOrNull()
    }

    // ------------------------------------------------------------------ //
    //  Filter options
    // ------------------------------------------------------------------ //

    suspend fun fetchFilterOptions(forceRefresh: Boolean = false): FilterOptions =
        optionsMutex.withLock {
            cachedOptions?.let { if (!forceRefresh) return@withLock it }
            loadFilterOptions()
        }

    private suspend fun loadFilterOptions(): FilterOptions {
        val countries = fetchNameList("/countries")
        val manufacturers = fetchNameList("/manufacturers")
        val brands = fetchNameList("/vehicle_brands")
        val series = fetchIdNameList("/series")

        val statuses = linkedSetOf<String>()
        val services = sortedSetOf<String>()
        var offset = 0
        while (true) {
            val result = rest.get(
                path = "/vehicles",
                params = mapOf(
                    "select" to "emergency_service,availability_status",
                    "offset" to offset.toString(),
                    "limit" to "1000"
                )
            )
            if (!result.isSuccess) break
            val pairs = Json.default.decodeFromString<List<StatusPair>>(result.body)
            pairs.forEach { pair ->
                pair.availabilityStatus?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    statuses.add(Status.displayLabel(it))
                }
                pair.emergencyService?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    services.add(it)
                }
            }
            if (pairs.size < 1000) break
            offset += 1000
        }

        val options = FilterOptions(
            countries = countries,
            manufacturers = manufacturers,
            brands = brands,
            series = series.map { it.second },
            statuses = statuses.sorted(),
            services = services.toList(),
            seriesIds = series.associate { it.second to it.first },
            loaded = true
        )
        cachedOptions = options
        return options
    }

    private suspend fun fetchNameList(path: String): List<String> {
        val result = rest.get(path, mapOf("select" to "name", "order" to "name"))
        if (!result.isSuccess) return emptyList()
        return Json.default.decodeFromString<List<NameRow>>(result.body)
            .mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }
    }

    @Serializable
    private data class NameRow(val name: String? = null)

    /** Name + id pairs (needed for the series filter, which is applied on `series_id`). */
    private suspend fun fetchIdNameList(path: String): List<Pair<Int, String>> {
        val result = rest.get(path, mapOf("select" to "id,name", "order" to "name"))
        if (!result.isSuccess) return emptyList()
        return Json.default.decodeFromString<List<IdNameRow>>(result.body)
            .mapNotNull { row ->
                val id = row.id ?: return@mapNotNull null
                val name = row.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                id to name
            }
    }

    @Serializable
    private data class IdNameRow(val id: Int? = null, val name: String? = null)

    // ------------------------------------------------------------------ //
    //  User collection / wishlist
    // ------------------------------------------------------------------ //

    /**
     * The user's collection / wishlist.
     *
     * Done in two steps (instead of a nested embed) because only query
     * patterns verified against live data are used:
     *   1) user_model_status -> list of vehicle_id values
     *   2) vehicles -> detailed records with id=in.(...) (in chunks of 100)
     */
    suspend fun fetchUserVehicles(
        userId: String,
        status: String,
        token: String
    ): List<Vehicle> {
        val idsResult = rest.get(
            path = "/user_model_status",
            params = mapOf(
                "select" to "vehicle_id",
                "user_id" to "eq.$userId",
                "status" to "eq.$status"
            ),
            token = token
        )
        if (!idsResult.isSuccess) return emptyList()

        val ids = Json.default
            .decodeFromString<List<UserStatusRow>>(idsResult.body)
            .mapNotNull { it.vehicleId }
        if (ids.isEmpty()) return emptyList()

        val out = mutableListOf<Vehicle>()
        ids.chunked(100).forEach { chunk ->
            val result = rest.get(
                path = "/vehicles",
                params = mapOf(
                    "select" to Config.VEHICLE_SELECT,
                    "id" to "in.(${chunk.joinToString(",")})",
                    "order" to "id.desc"
                )
            )
            if (result.isSuccess) out += decodeVehicles(result.body)
        }
        return out
    }

    @Serializable
    private data class UserStatusRow(
        @SerialName("vehicle_id") val vehicleId: Int? = null,
        val status: String? = null
    )

    /**
     * Add / remove from the collection (status = "collection" | "wishlist" | null -> delete).
     *
     * Exactly the same request as the web app: `on_conflict=user_id,vehicle_id` +
     * `Prefer: resolution=merge-duplicates`. Writing requires the **user's JWT**;
     * the RLS policy does not allow anon writes (verified against the live API:
     * 42501 "new row violates row-level security policy").
     */
    suspend fun setUserStatus(
        userId: String,
        vehicleId: Int,
        status: String?,
        token: String
    ): Boolean {
        return if (status == null) {
            val url = "${Config.REST_URL}/user_model_status?user_id=eq.$userId&vehicle_id=eq.$vehicleId"
            rest.delete(url, token = token).isSuccess
        } else {
            val body = """{"user_id":"$userId","vehicle_id":$vehicleId,"status":"$status"}"""
            val result = rest.post(
                fullUrl = "${Config.REST_URL}/user_model_status?on_conflict=user_id,vehicle_id",
                jsonBody = body,
                token = token,
                headers = mapOf("Prefer" to "resolution=merge-duplicates")
            )
            result.isSuccess
        }
    }

    /** The user's current status for this vehicle ("collection" | "wishlist" | null). */
    suspend fun fetchUserStatus(
        userId: String,
        vehicleId: Int,
        token: String
    ): String? {
        val result = rest.get(
            path = "/user_model_status",
            params = mapOf(
                "select" to "status",
                "user_id" to "eq.$userId",
                "vehicle_id" to "eq.$vehicleId"
            ),
            token = token
        )
        if (!result.isSuccess) return null
        return runCatching {
            Json.default.decodeFromString<List<StatusOnlyRow>>(result.body)
                .firstOrNull()?.status
        }.getOrNull()
    }

    @Serializable
    private data class StatusOnlyRow(val status: String? = null)

    suspend fun fetchCounts(vehicleIds: List<Int>): Map<Int, ModelCounts> {
        if (vehicleIds.isEmpty()) return emptyMap()
        val out = mutableMapOf<Int, ModelCounts>()
        vehicleIds.chunked(150).forEach { chunk ->
            val result = rest.get(
                path = "/model_stats",
                params = mapOf(
                    "select" to "vehicle_id,collection_count,wishlist_count",
                    "vehicle_id" to "in.(${chunk.joinToString(",")})"
                )
            )
            if (!result.isSuccess) return@forEach
            Json.default.decodeFromString<List<CountRow>>(result.body).forEach { row ->
                row.vehicleId?.let { out[it] = ModelCounts(row.collectionCount, row.wishlistCount) }
            }
        }
        return out
    }

    @Serializable
    private data class CountRow(
        @SerialName("vehicle_id") val vehicleId: Int? = null,
        @SerialName("collection_count") val collectionCount: Int = 0,
        @SerialName("wishlist_count") val wishlistCount: Int = 0
    )

    // ------------------------------------------------------------------ //

    private fun decodeVehicles(body: String): List<Vehicle> = runCatching {
        Json.default.decodeFromString<List<Vehicle>>(body)
    }.getOrElse { emptyList() }
}

class MuseumException(message: String) : Exception(message)

/**
 * Builds the PostgREST query parameters of a search.
 *
 * The country / manufacturer / brand filters are applied on the embedded tables
 * (that only works with the `!inner` embeds, see Config.VEHICLE_SELECT).
 * The series filter is applied on the `series_id` column, because the `series`
 * embed is a plain (left) join - PostgREST would silently ignore a filter on it.
 */
internal fun buildSearchParams(
    filters: Filters,
    page: Int,
    pageSize: Int,
    options: FilterOptions = FilterOptions()
): Map<String, String> {
    val params = linkedMapOf(
        "select" to Config.VEHICLE_SELECT,
        "order" to "id.desc",
        "offset" to (page * pageSize).toString(),
        "limit" to pageSize.toString()
    )

    val search = filters.search.trim()
    if (search.isNotEmpty()) {
        params["model_name"] = "ilike.*$search*"
    }

    if (filters.country != ALL) {
        params["countries.name"] = "eq.${filters.country}"
    }
    if (filters.manufacturer != ALL) {
        params["manufacturers.name"] = "eq.${filters.manufacturer}"
    }
    if (filters.brand != ALL) {
        params["vehicle_brands.name"] = "eq.${filters.brand}"
    }
    if (filters.series != ALL) {
        val seriesId = options.seriesIds[filters.series]
        if (seriesId != null) {
            params["series_id"] = "eq.$seriesId"
        }
    }

    // Web logic: when a group and a service are both selected, only the service is applied.
    if (filters.service != ALL) {
        params["emergency_service"] = "eq.${filters.service}"
    } else if (filters.serviceGroup != ALL) {
        val groupValues = options.services.filter {
            EmergencyGroups.groupOf(it) == filters.serviceGroup
        }
        if (groupValues.isNotEmpty()) {
            params["emergency_service"] = "in.(${groupValues.joinToString(",")})"
        }
    }

    val statusValues = Status.filterValues(filters.status)
    when {
        statusValues.isEmpty() -> Unit
        statusValues.size == 1 -> params["availability_status"] = "eq.${statusValues.first()}"
        else -> params["availability_status"] = "in.(${statusValues.joinToString(",")})"
    }

    return params
}

