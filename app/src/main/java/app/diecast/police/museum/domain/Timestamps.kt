package app.diecast.police.museum.domain

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Produces the same result as the web app's `a.status_changed_at || a.created_at || a.updated_at`
 * resolution. The sorting is critical for the "Recently in Collection" section: PostgREST's
 * multi-column `order=` does not produce the same result as the web app's JS sorting,
 * so the candidate list is fetched lightweight and sorted here.
 */
object Timestamps {

    fun resolvedRaw(
        statusChangedAt: String?,
        createdAt: String?,
        updatedAt: String?
    ): String? = firstNonBlank(statusChangedAt, createdAt, updatedAt)

    fun resolvedMillis(
        statusChangedAt: String?,
        createdAt: String?,
        updatedAt: String?
    ): Long? = parseIsoMillis(resolvedRaw(statusChangedAt, createdAt, updatedAt))

    fun parseIsoMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().replace(' ', 'T')
        return try {
            OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }
}
