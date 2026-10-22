package app.diecast.police.museum.core

/**
 * OAuth donus adresinin ayristirilmasi.
 *
 * Supabase farkli akislarda farkli yerlere parametre koyar:
 *   - PKCE     : `diecastpolice://auth-callback?code=...`   (query)
 *   - Implicit : `diecastpolice://auth-callback#access_token=...` (fragment)
 *   - Hata     : `diecastpolice://auth-callback?error=...&error_description=...`
 *
 * Bu tip bilerek Android'e bagli degildir; saf Kotlin'de test edilebilir.
 */
data class OAuthRedirect(
    val code: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
    val error: String? = null,
    val errorDescription: String? = null
) {
    /** PKCE donusu: sunucu `?code=` gonderir, token degisimi gerekir. */
    val hasCode: Boolean get() = !code.isNullOrBlank()

    /** Implicit donusu: token'lar fragment'ta gelir. */
    val hasTokens: Boolean
        get() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    val hasError: Boolean get() = !error.isNullOrBlank()

    /** Kullaniciya gosterilecek hata metni. */
    fun errorMessage(): String? {
        if (!hasError) return null
        return errorDescription?.takeIf { it.isNotBlank() } ?: "Sign-in failed ($error)."
    }

    companion object {

        const val SCHEME = "diecastpolice"
        const val HOST = "auth-callback"

        /**
         * Donus adresini ayristirir.
         *
         * @param scheme adresin semasi (or. "diecastpolice")
         * @param host adresin sunucusu (or. "auth-callback")
         * @param query adresin query kisimi (or. "?code=...")
         * @param fragment adresin fragment kismi (or. "#access_token=...")
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
            // Query onceliklidir; fragment yalnizca eksikleri tamamlar.
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
         * [plusAsSpace] true ise `+` bosluk olarak yorumlanir (query yazimi).
         * Fragment'ta `+` gercek bir plus isareti oldugu icin false verilir.
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

        /** Yuzde kodlamasini cozer (tanimlanmis karakterler haric harf/rakam korunur). */
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
