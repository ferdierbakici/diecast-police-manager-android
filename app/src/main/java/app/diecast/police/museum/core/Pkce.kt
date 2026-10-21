package app.diecast.police.museum.core

import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE (RFC 7636) destegi.
 *
 * Neden PKCE? Native uygulama OAuth donusunde token'lari URL icinde tasir.
 * PKCE ile sunucu bize sadece tek kullanimlik bir `code` verir; token'i biz
 * urettigimiz `code_verifier` ile degistiririz. Boylece:
 *   - token URL'de (ve arac kayitlarinda) gecmez,
 *   - ele gecirilmis bir `code` tek basina kullanilamaz.
 *
 * Supabase tarafinda akis:
 *   1) BASLAT: GET /auth/v1/authorize?provider=google&redirect_to=...
 *              &flow_type=pkce&code_challenge=<S256(verifier)>&code_challenge_method=s256
 *   2) DONUS:  diecastpolice://auth-callback?code=<code>
 *   3) DEGISTIR: POST /auth/v1/token?grant_type=pkce
 *                 { "auth_code": "<code>", "code_verifier": "<verifier>" }
 */
object Pkce {

    /** code_verifier 43-128 karakter olmali; 64 bayt -> 86 karakter (base64url). */
    private const val VERIFIER_BYTES = 64

    private val secureRandom = SecureRandom()

    /** Yeni, tahmin edilemez bir code_verifier uretir. */
    fun newCodeVerifier(): String {
        val bytes = ByteArray(VERIFIER_BYTES)
        secureRandom.nextBytes(bytes)
        return base64Url(bytes)
    }

    /** code_verifier -> code_challenge (S256 = base64url(SHA-256(verifier))). */
    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    /**
     * PKCE'li Supabase authorize URL'i.
     *
     * `redirect_to` de URI olarak kodlanir (`diecastpolice://auth-callback` ->
     * `diecastpolice%3A%2F%2Fauth-callback`); aksi halde ozel sema query
     * ayraclariyla karisabilir.
     */
    fun authorizeUrl(authUrl: String, redirectUri: String, verifier: String): String =
        "$authUrl/authorize" +
            "?provider=google" +
            "&redirect_to=${encode(redirectUri)}" +
            "&flow_type=pkce" +
            "&code_challenge=${codeChallenge(verifier)}" +
            "&code_challenge_method=s256"

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
