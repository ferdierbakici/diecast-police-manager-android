package app.diecast.police.museum

import app.diecast.police.museum.core.Pkce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64

/**
 * PKCE (RFC 7636) validations.
 *
 * The behaviour of the live Supabase (GoTrue v2.197.0) was verified:
 *   - `GET /auth/v1/authorize?...&flow_type=pkce&code_challenge=<43 characters>`
 *     -> HTTP 302 (accepted)
 *   - a 200 character code_challenge -> HTTP 400 (S256 must be 43 characters)
 *   - `POST /auth/v1/token?grant_type=pkce` {"auth_code","code_verifier"}
 *     -> HTTP 404 flow_state_not_found (field names correct, endpoint active)
 */
class PkceTest {

    @Test
    fun `the code_verifier is within the RFC 7636 length range`() {
        repeat(20) {
            val verifier = Pkce.newCodeVerifier()
            assertTrue(
                "the verifier must be 43-128 characters, found ${verifier.length}",
                verifier.length in 43..128
            )
        }
    }

    @Test
    fun `the code_verifier contains only base64url characters`() {
        repeat(20) {
            val verifier = Pkce.newCodeVerifier()
            assertTrue(
                "a character that is not base64url: $verifier",
                verifier.matches(Regex("^[A-Za-z0-9\\-._~]+$"))
            )
        }
    }

    @Test
    fun `the code_verifier is different every time`() {
        val values = (1..50).map { Pkce.newCodeVerifier() }.toSet()
        assertEquals("50 different verifiers must be produced", 50, values.size)
    }

    @Test
    fun `the code_challenge is S256 and 43 characters`() {
        val verifier = Pkce.newCodeVerifier()
        val challenge = Pkce.codeChallenge(verifier)
        assertEquals("the S256 code_challenge must be 43 characters", 43, challenge.length)
    }

    @Test
    fun `the code_challenge is exactly the base64url SHA-256`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        )
        assertEquals(expected, Pkce.codeChallenge(verifier))
    }

    @Test
    fun `the RFC 7636 test vector`() {
        // RFC 7636 Appendix B - known test vector
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expected, Pkce.codeChallenge(verifier))
    }

    @Test
    fun `the authorize URL contains the PKCE parameters`() {
        val verifier = Pkce.newCodeVerifier()
        val url = Pkce.authorizeUrl(
            "https://example.supabase.co/auth/v1",
            "diecastpolice://auth-callback",
            verifier
        )

        assertTrue(url.startsWith("https://example.supabase.co/auth/v1/authorize?"))
        assertTrue("it must contain provider=google", url.contains("provider=google"))
        assertTrue("it must contain flow_type=pkce", url.contains("flow_type=pkce"))
        assertTrue(
            "it must contain code_challenge_method=s256",
            url.contains("code_challenge_method=s256")
        )
        assertTrue(
            "the code_challenge must be computed correctly",
            url.contains("code_challenge=${Pkce.codeChallenge(verifier)}")
        )
    }

    @Test
    fun `redirect_to is encoded as a URI`() {
        val verifier = Pkce.newCodeVerifier()
        val url = Pkce.authorizeUrl(
            "https://example.supabase.co/auth/v1",
            "diecastpolice://auth-callback",
            verifier
        )
        val raw = url.substringAfter("redirect_to=").substringBefore("&")

        // Must be encoded: '://' must not appear in plain form
        assertNotEquals("redirect_to is not encoded", "diecastpolice://auth-callback", raw)
        assertTrue(
            "the escaped value is wrong: $raw",
            !raw.contains("://")
        )
        // But the value the server resolves must be the same
        assertEquals(
            "diecastpolice://auth-callback",
            URLDecoder.decode(raw, "UTF-8")
        )
    }

    @Test
    fun `the authorize URL matches the real deep link`() {
        val verifier = Pkce.newCodeVerifier()
        val url = Pkce.authorizeUrl(
            "https://eojfccviyaambajvlsqu.supabase.co/auth/v1",
            "diecastpolice://auth-callback",
            verifier
        )
        // The live server accepts these parameters (it returned HTTP 302)
        assertTrue(url.contains("eojfccviyaambajvlsqu.supabase.co"))
        assertTrue(url.contains("redirect_to=diecastpolice%3A%2F%2Fauth-callback"))
    }
}
