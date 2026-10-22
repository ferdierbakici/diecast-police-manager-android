package app.diecast.police.museum.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A lightweight record fetched in step 1 of the "Recently in Collection" section.
 * Because the sorting happens on the client, only timestamps and the id are carried.
 */
@Serializable
data class StatusCandidate(
    val id: Int,
    @SerialName("previous_status") val previousStatus: String? = null,
    @SerialName("status_changed_at") val statusChangedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
