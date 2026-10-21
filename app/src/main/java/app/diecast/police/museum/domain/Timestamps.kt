package app.diecast.police.museum.domain

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Web uygulamasindaki `a.status_changed_at || a.created_at || a.updated_at` cozumlemesiyle
 * ayni sonucu verir. Siralama "Recently in Collection" bolumunde kritik: PostgREST'in
 * cok kolonlu `order=` ile siralamasi web'in JS siralamasiyla ayni sonucu vermiyor,
 * bu yuzden aday listesi hafif cekilip burada siralanir.
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
