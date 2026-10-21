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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Sayim icin hafif kayit. */
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

    // ------------------------------------------------------------------ //
    //  Istatistikler
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
    //  Ana sayfa bolumleri
    // ------------------------------------------------------------------ //

    /** "Last 10 added models" - web ile ayni: order=id.desc (created_at degil). */
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
     * "Recently in Collection" - web ile birebir ayni 10 kayit.
     *
     * 1) Sunucu tarafi `or` filtresiyle adaylar 1042 -> 74'e iner.
     * 2) Siralama istemcide yapilir (PostgREST cok kolonlu order= web ile ayni sonucu vermiyor).
     * 3) Secilen 10 id icin detayli kayitlar cekilir.
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

        // PostgREST `id=in.(...)` sonuclari id sirasinda doner; bu yuzden 2. adimda
        // hesaplanan "en son koleksiyona katilan" sirasi burada GERI KURULMALIDIR.
        // (order=id.desc eklemek bu siralamayi tamamen bozar.)
        val byId = decodeVehicles(detail.body).associateBy { it.id }
        return topIds.mapNotNull { byId[it] }
    }

    // ------------------------------------------------------------------ //
    //  Arama
    // ------------------------------------------------------------------ //

    suspend fun searchVehicles(
        filters: Filters,
        page: Int = 0,
        pageSize: Int = 50
    ): PagedResult {
        // Grup filtresi tum servis degerlerine ihtiyac duyar; secenekler yuklenmediyse once yukle.
        if (cachedOptions == null) fetchFilterOptions()
        val params = buildSearchParams(filters, page, pageSize)
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

    private fun buildSearchParams(
        filters: Filters,
        page: Int,
        pageSize: Int
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

        // Web mantigi: grup + servis birlikte seciliyse sadece servis uygulanir.
        if (filters.service != ALL) {
            params["emergency_service"] = "eq.${filters.service}"
        } else if (filters.serviceGroup != ALL) {
            val groupValues = cachedOptions?.services.orEmpty().filter {
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

    // ------------------------------------------------------------------ //
    //  Detay
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
    //  Filtre secenekleri
    // ------------------------------------------------------------------ //

    suspend fun fetchFilterOptions(forceRefresh: Boolean = false): FilterOptions {
        cachedOptions?.let { if (!forceRefresh) return it }

        val countries = fetchNameList("/countries")
        val manufacturers = fetchNameList("/manufacturers")
        val brands = fetchNameList("/vehicle_brands")

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
            statuses = statuses.sorted(),
            services = services.toList(),
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

    // ------------------------------------------------------------------ //
    //  Kullanici koleksiyonu / wishlist
    // ------------------------------------------------------------------ //

    /**
     * Kullanicinin koleksiyonu / wishlist'i.
     *
     * Iki adimda yapilir (ic ice embed yerine), cunku yalnizca canli veride
     * dogrulanmis sorgu desenleri kullaniliyor:
     *   1) user_model_status -> vehicle_id listesi
     *   2) vehicles -> id=in.(...) ile detayli kayitlar (100'erli parcalar)
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
     * Koleksiyona ekle / kaldir (status = "collection" | "wishlist" | null -> sil).
     *
     * Web'in birebir ayni istegi: `on_conflict=user_id,vehicle_id` +
     * `Prefer: resolution=merge-duplicates`. Yazma icin **kullanicinin JWT'si**
     * gerekir; RLS anon yazmaya izin vermiyor (canli API'de dogrulandi:
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

    /** Kullanicinin bu arac icin mevcut durumu ("collection" | "wishlist" | null). */
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
