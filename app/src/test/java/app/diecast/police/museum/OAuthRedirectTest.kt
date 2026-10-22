package app.diecast.police.museum

import app.diecast.police.museum.core.OAuthRedirect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for parsing the OAuth return URL.
 *
 * Based on the observed behaviour of the live GoTrue v2.197.0:
 *   - PKCE return: `diecastpolice://auth-callback?code=...`
 *   - Implicit:    `diecastpolice://auth-callback#access_token=...&refresh_token=...`
 *   - Error:       `diecastpolice://auth-callback?error=server_error&error_description=...`
 */
class OAuthRedirectTest {

    // ------------------------------------------------------------------ //
    //  Address matching
    // ------------------------------------------------------------------ //

    @Test
    fun `another scheme or host is rejected`() {
        assertNull(OAuthRedirect.parse("https", "auth-callback", "?code=abc", null))
        assertNull(OAuthRedirect.parse("diecastpolice", "vehicle", "?code=abc", null))
        assertNull(OAuthRedirect.parse("diecastpolice", "other-host", "?code=abc", null))
    }

    @Test
    fun `the correct scheme and host are accepted`() {
        val r = OAuthRedirect.parse("diecastpolice", "auth-callback", "?code=abc", null)
        assertNotNull(r)
        assertEquals("abc", r?.code)
    }

    // ------------------------------------------------------------------ //
    //  PKCE return
    // ------------------------------------------------------------------ //

    @Test
    fun `the code is read from the PKCE query`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback",
            "?code=abc123XYZ&sb=12345", null
        )!!
        assertTrue(r.hasCode)
        assertFalse(r.hasTokens)
        assertEquals("abc123XYZ", r.code)
    }

    @Test
    fun `hasCode is false when the code is empty`() {
        val r = OAuthRedirect.parse("diecastpolice", "auth-callback", "?code=", null)!!
        assertFalse(r.hasCode)
        assertNull(r.code)
    }

    // ------------------------------------------------------------------ //
    //  Implicit return
    // ------------------------------------------------------------------ //

    @Test
    fun `the tokens are read from the implicit fragment`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback",
            null,
            "#access_token=AT_123&refresh_token=RT_456&expires_in=3600&token_type=bearer"
        )!!
        assertTrue(r.hasTokens)
        assertFalse(r.hasCode)
        assertEquals("AT_123", r.accessToken)
        assertEquals("RT_456", r.refreshToken)
        assertEquals(3600L, r.expiresIn)
    }

    @Test
    fun `hasTokens is false without a refresh_token`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback", null, "#access_token=AT_123"
        )!!
        assertFalse(r.hasTokens)
    }

    @Test
    fun `expires_in is null when it cannot be read`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback", null, "#access_token=A&refresh_token=R&expires_in=abc"
        )!!
        assertNull(r.expiresIn)
    }

    // ------------------------------------------------------------------ //
    //  Error return
    // ------------------------------------------------------------------ //

    @Test
    fun `the error is read from the query`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback",
            "?error=server_error&error_description=Unable+to+exchange+external+code", null
        )!!
        assertTrue(r.hasError)
        assertEquals("server_error", r.error)
        // In query notation '+' is a space
        assertEquals("Unable to exchange external code", r.errorDescription)
    }

    @Test
    fun `the error is read from the fragment`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback", null,
            "#error=access_denied&error_description=User+denied"
        )!!
        assertTrue(r.hasError)
        assertEquals("access_denied", r.error)
        // In a fragment '+' stays a literal plus sign
        assertEquals("User+denied", r.errorDescription)
    }

    @Test
    fun `the error message falls back to a default without a description`() {
        val r = OAuthRedirect.parse("diecastpolice", "auth-callback", "?error=access_denied", null)!!
        assertEquals("Sign-in failed (access_denied).", r.errorMessage())
    }

    @Test
    fun `errorMessage is null when there is no error`() {
        val r = OAuthRedirect.parse("diecastpolice", "auth-callback", "?code=abc", null)!!
        assertNull(r.errorMessage())
    }

    // ------------------------------------------------------------------ //
    //  Query / fragment encoding
    // ------------------------------------------------------------------ //

    @Test
    fun `the percent encoding is decoded`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback",
            "?error_description=Kod%20%C3%87%C3%B6z%C3%BCld%C3%BC", null
        )!!
        assertEquals("Kod Çözüldü", r.errorDescription)
    }

    @Test
    fun `the multi byte utf8 characters are decoded`() {
        // "s" = U+015F (C5 9F), "u" = U+00FC (C3 BC)
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback",
            "?error_description=%C5%9F%C3%BC", null
        )!!
        assertEquals("\u015F\u00FC", r.errorDescription)
    }

    @Test
    fun `the leading and trailing markers are stripped`() {
        val withPrefix = OAuthRedirect.parseParams("?a=1&b=2", plusAsSpace = true)
        val withHash = OAuthRedirect.parseParams("#a=1&b=2", plusAsSpace = true)
        val plain = OAuthRedirect.parseParams("a=1&b=2", plusAsSpace = true)
        assertEquals(plain, withPrefix)
        assertEquals(plain, withHash)
    }

    @Test
    fun `a parameter without a value is skipped`() {
        val p = OAuthRedirect.parseParams("a=1&flag&b=2", plusAsSpace = true)
        assertEquals(2, p.size)
        assertEquals("1", p["a"])
        assertEquals("2", p["b"])
        assertNull(p["flag"])
    }

    @Test
    fun `an empty input returns an empty map`() {
        assertTrue(OAuthRedirect.parseParams(null, plusAsSpace = true).isEmpty())
        assertTrue(OAuthRedirect.parseParams("", plusAsSpace = true).isEmpty())
        assertTrue(OAuthRedirect.parseParams("?", plusAsSpace = true).isEmpty())
        assertTrue(OAuthRedirect.parseParams("#", plusAsSpace = true).isEmpty())
    }

    @Test
    fun `an empty value stays an empty string`() {
        val p = OAuthRedirect.parseParams("a=", plusAsSpace = true)
        assertEquals("", p["a"])
    }

    @Test
    fun `the query has priority over the fragment`() {
        // If both contain a code, the query (PKCE) wins
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback", "?code=QUERY", "#code=FRAGMENT"
        )!!
        assertEquals("QUERY", r.code)
    }
}
