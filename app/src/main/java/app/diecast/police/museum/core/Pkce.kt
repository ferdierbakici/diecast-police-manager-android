package app.diecast.police.museum.core

import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE (RFC 7636) support.
 *
 * Why PKCE? A native app carries the tokens inside the URL on the OAuth return.
 * With PKCE the server gives us only a single-use `code`; we exchange it for
 * the token using the `code_verifier` we generated. That way:
 *   - the token never travels in the URL (nor in log records),
 *   - a leaked `code` is useless on its own.
 *
 * Flow on the Supabase side:
 *   1) START: GET /auth/v1/authorize?provider=google&redirect_to=...
 *              &flow_type=pkce&code_challenge=<S256(verifier)>&code_challenge_method=s256
 *   2) RETURN: diecastpolice://auth-callback?code=<code>
 *   3) EXCHANGE: POST /auth/v1/token?grant_type=pkce
 *                 { "auth_code": "<code>", "code_verifier": "<verifier>" }
 */
object Pkce {

    /** code_verifier must be 43-128 characters; 64 bytes -> 86 characters (base64url). */
    private const val VERIFIER_BYTES = 64

    private val secureRandom = SecureRandom()

    /** Generates a new, unpredictable code_verifier. */
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
     * The Supabase authorize URL with PKCE.
     *
     * `redirect_to` is URI-encoded (`diecastpolice://auth-callback` ->
     * `diecastpolice%3A%2F%2Fauth-callback`); otherwise the custom scheme
     * may clash with the query separators.
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
