package app.diecast.police.museum.core

/**
 * Parsing of the OAuth return URL.
 *
 * Supabase puts the parameters in different places depending on the flow:
 *   - PKCE     : `diecastpolice://auth-callback?code=...`   (query)
 *   - Implicit : `diecastpolice://auth-callback#access_token=...` (fragment)
 *   - Error    : `diecastpolice://auth-callback?error=...&error_description=...`
 *
 * This type is deliberately free of Android dependencies so that it can be
 * tested in plain Kotlin.
 */
data class OAuthRedirect(
    val code: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
    val error: String? = null,
    val errorDescription: String? = null
) {
    /** PKCE return: the server sends `?code=`, so the tokens have to be exchanged. */
    val hasCode: Boolean get() = !code.isNullOrBlank()

    /** Implicit return: the tokens arrive in the fragment. */
    val hasTokens: Boolean
        get() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    val hasError: Boolean get() = !error.isNullOrBlank()

    /** The error text shown to the user. */
    fun errorMessage(): String? {
        if (!hasError) return null
        return errorDescription?.takeIf { it.isNotBlank() } ?: "Sign-in failed ($error)."
    }

    companion object {

        const val SCHEME = "diecastpolice"
        const val HOST = "auth-callback"

        /**
         * Parses the return URL.
         *
         * @param scheme the scheme of the URL (e.g. "diecastpolice")
         * @param host the host of the URL (e.g. "auth-callback")
         * @param query the query part of the URL (e.g. "?code=...")
         * @param fragment the fragment part of the URL (e.g. "#access_token=...")
         */
        fun parse(
            scheme: String?,
            host: String?,
            query: String?,
            fragment: String?
        ): OAuthRedirect? {
            if (scheme != SCHEME || host != HOST) return null

            val q = parseParams(query, plusAsSpace = true)
            val f = parseParams(fragment, plusAsSpace = false)
            // The query takes priority; the fragment only fills in what is missing.
            val all = f + q

            return OAuthRedirect(
                code = all["code"]?.takeIf { it.isNotBlank() },
                accessToken = all["access_token"]?.takeIf { it.isNotBlank() },
                refreshToken = all["refresh_token"]?.takeIf { it.isNotBlank() },
                expiresIn = all["expires_in"]?.toLongOrNull(),
                error = all["error"]?.takeIf { it.isNotBlank() },
                errorDescription = all["error_description"]?.takeIf { it.isNotBlank() }
            )
        }

        /**
         * "a=1&b=2" -> map.
         *
         * When [plusAsSpace] is true, `+` is read as a space (query notation).
         * In a fragment `+` is a literal plus sign, so false is passed there.
         */
        fun parseParams(raw: String?, plusAsSpace: Boolean): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            val body = raw.removePrefix("?").removePrefix("#")
            if (body.isBlank()) return emptyMap()
            return body.split("&").mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = part.substring(0, idx)
                var value = part.substring(idx + 1)
                if (plusAsSpace) value = value.replace("+", " ")
                key to percentDecode(value)
            }.toMap()
        }

        /** Decodes percent encoding (letters/digits are kept as-is). */
        private fun percentDecode(value: String): String {
            if (!value.contains('%') && !value.contains('+')) return value
            val bytes = java.io.ByteArrayOutputStream(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '%' && i + 2 < value.length) {
                    val hex = value.substring(i + 1, i + 3)
                    val b = hex.toIntOrNull(16)
                    if (b != null) {
                        bytes.write(b)
                        i += 3
                        continue
                    }
                }
                bytes.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
            return String(bytes.toByteArray(), Charsets.UTF_8)
        }
    }
}
