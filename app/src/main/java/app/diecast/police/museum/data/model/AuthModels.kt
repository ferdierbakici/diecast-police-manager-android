package app.diecast.police.museum.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserMetadata(
    /**
     * The Supabase local display name (the user sets it for their own profile).
     */
    @SerialName("display_name") val displayName: String? = null,
    /**
     * The name provided by Google OAuth (e.g. "Ferdi Erbakici").
     */
    @SerialName("full_name") val fullName: String? = null,
    /**
     * The alternative display name provided by Google.
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
     * The name to display.
     *
     * Priority: the user's `display_name` -> Google `full_name` -> Google `name`
     * -> the local part of the email address.
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

/** Session state - written to DataStore. */
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
