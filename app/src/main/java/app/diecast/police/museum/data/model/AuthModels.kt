package app.diecast.police.museum.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserMetadata(
    /**
     * Supabase yerel alan adı (kullanıcı kendi profili için ayarlar).
     */
    @SerialName("display_name") val displayName: String? = null,
    /**
     * Google OAuth sağlayıcısının verdiği ad (örn. "Ferdi Erbakici").
     */
    @SerialName("full_name") val fullName: String? = null,
    /**
     * Google'ın verdiği alternatif alan adı.
     */
    @SerialName("name") val name: String? = null
)

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null,
    @SerialName("user_metadata") val userMetadata: UserMetadata? = null
) {
    /**
     * Gosterilecek ad.
     *
     * Oncelik: kullaniciya ait `display_name` -> Google `full_name` -> Google `name`
     * -> e-posta yerel adi.
     */
    val displayName: String?
        get() = userMetadata?.displayName?.takeIf { it.isNotBlank() }
            ?: userMetadata?.fullName?.takeIf { it.isNotBlank() }
            ?: userMetadata?.name?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')
}

@Serializable
data class AuthSession(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("token_type") val tokenType: String? = null,
    val user: AuthUser? = null
)

@Serializable
data class AuthErrorBody(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val msg: String? = null,
    val message: String? = null,
    @SerialName("error_code") val errorCode: String? = null
)

/** Oturum durumu - DataStore'a yazilir. */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String?,
    val displayName: String?
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now >= expiresAt
}
