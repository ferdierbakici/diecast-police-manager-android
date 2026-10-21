package app.diecast.police.museum.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * "Recently in Collection" bolumunun 1. adiminda cekilen hafif kayit.
 * Siralama istemcide yapildigi icin sadece zaman damgalari ve id tasinir.
 */
@Serializable
data class StatusCandidate(
    val id: Int,
    @SerialName("previous_status") val previousStatus: String? = null,
    @SerialName("status_changed_at") val statusChangedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
