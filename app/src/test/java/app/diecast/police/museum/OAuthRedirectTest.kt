package app.diecast.police.museum

import app.diecast.police.museum.core.OAuthRedirect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OAuth donus adedi ayristirma testleri.
 *
 * Canli GoTrue v2.197.0 davranislari referans alindi:
 *   - PKCE donusu:  `diecastpolice://auth-callback?code=...`
 *   - Implicit:      `diecastpolice://auth-callback#access_token=...&refresh_token=...`
 *   - Hata donusu:   `diecastpolice://auth-callback?error=server_error&error_description=...`
 */
class OAuthRedirectTest {

    // ------------------------------------------------------------------ //
    //  Adres eslesmesi
    // ------------------------------------------------------------------ //

    @Test
    fun `baska sema veya host reddedilir`() {
        assertNull(OAuthRedirect.parse("https", "auth-callback", "?code=abc", null))
        assertNull(OAuthRedirect.parse("diecastpolice", "vehicle", "?code=abc", null))
        assertNull(OAuthRedirect.parse("diecastpolice", "other-host", "?code=abc", null))
    }

    @Test
    fun `dogru sema ve host kabul edilir`() {
        val r = OAuthRedirect.parse("diecastpolice", "auth-callback", "?code=abc", null)
        assertNotNull(r)
        assertEquals("abc", r?.code)
    }

    // ------------------------------------------------------------------ //
    //  PKCE donusu
    // ------------------------------------------------------------------ //

    @Test
    fun `PKCE query'den code okunur`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback",
            "?code=abc123XYZ&sb=12345", null
        )!!
        assertTrue(r.hasCode)
        assertFalse(r.hasTokens)
        assertEquals("abc123XYZ", r.code)
    }

    @Test
    fun `code bos ise hasCode false`() {
        val r = OAuthRedirect.parse("diecastpolice", "auth-callback", "?code=", null)!!
        assertFalse(r.hasCode)
        assertNull(r.code)
    }

    // ------------------------------------------------------------------ //
    //  Implicit donusu
    // ------------------------------------------------------------------ //

    @Test
    fun `implicit fragment'tan token'lar okunur`() {
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
    fun `refresh_token yoksa hasTokens false`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback", null, "#access_token=AT_123"
        )!!
        assertFalse(r.hasTokens)
    }

    @Test
    fun `expires_in okunamazsa null`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback", null, "#access_token=A&refresh_token=R&expires_in=abc"
        )!!
        assertNull(r.expiresIn)
    }

    // ------------------------------------------------------------------ //
    //  Hata donusu
    // ------------------------------------------------------------------ //

    @Test
    fun `hata query'den okunur`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback",
            "?error=server_error&error_description=Unable+to+exchange+external+code", null
        )!!
        assertTrue(r.hasError)
        assertEquals("server_error", r.error)
        // Query yaziminda '+' bosluktur
        assertEquals("Unable to exchange external code", r.errorDescription)
    }

    @Test
    fun `hata fragment'tan okunur`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback", null,
            "#error=access_denied&error_description=User+denied"
        )!!
        assertTrue(r.hasError)
        assertEquals("access_denied", r.error)
        // Fragment'ta '+' gercek plus isareti olarak kalir
        assertEquals("User+denied", r.errorDescription)
    }

    @Test
    fun `hata mesaji description yoksa varsayilan`() {
        val r = OAuthRedirect.parse("diecastpolice", "auth-callback", "?error=access_denied", null)!!
        assertEquals("Giris basarisiz (access_denied).", r.errorMessage())
    }

    @Test
    fun `hata yoksa errorMessage null`() {
        val r = OAuthRedirect.parse("diecastpolice", "auth-callback", "?code=abc", null)!!
        assertNull(r.errorMessage())
    }

    // ------------------------------------------------------------------ //
    //  Query / fragment kodlama
    // ------------------------------------------------------------------ //

    @Test
    fun `yuzde kodlamasi cozulur`() {
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback",
            "?error_description=Kod%20%C3%87%C3%B6z%C3%BCld%C3%BC", null
        )!!
        assertEquals("Kod Çözüldü", r.errorDescription)
    }

    @Test
    fun `cok baytli utf8 karakterler cozulur`() {
        // "s" = U+015F (C5 9F), "u" = U+00FC (C3 BC)
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback",
            "?error_description=%C5%9F%C3%BC", null
        )!!
        assertEquals("\u015F\u00FC", r.errorDescription)
    }

    @Test
    fun `onceki ve sonraki isaretler temizlenir`() {
        val withPrefix = OAuthRedirect.parseParams("?a=1&b=2", plusAsSpace = true)
        val withHash = OAuthRedirect.parseParams("#a=1&b=2", plusAsSpace = true)
        val plain = OAuthRedirect.parseParams("a=1&b=2", plusAsSpace = true)
        assertEquals(plain, withPrefix)
        assertEquals(plain, withHash)
    }

    @Test
    fun `degeri olmayan parametre atlanir`() {
        val p = OAuthRedirect.parseParams("a=1&flag&b=2", plusAsSpace = true)
        assertEquals(2, p.size)
        assertEquals("1", p["a"])
        assertEquals("2", p["b"])
        assertNull(p["flag"])
    }

    @Test
    fun `bos girdi bos map doner`() {
        assertTrue(OAuthRedirect.parseParams(null, plusAsSpace = true).isEmpty())
        assertTrue(OAuthRedirect.parseParams("", plusAsSpace = true).isEmpty())
        assertTrue(OAuthRedirect.parseParams("?", plusAsSpace = true).isEmpty())
        assertTrue(OAuthRedirect.parseParams("#", plusAsSpace = true).isEmpty())
    }

    @Test
    fun `deger bos string olarak kalir`() {
        val p = OAuthRedirect.parseParams("a=", plusAsSpace = true)
        assertEquals("", p["a"])
    }

    @Test
    fun `query onceliklidir fragment'a gore`() {
        // Ikisi de code iceriyorsa query (PKCE) kazanir
        val r = OAuthRedirect.parse(
            "diecastpolice", "auth-callback", "?code=QUERY", "#code=FRAGMENT"
        )!!
        assertEquals("QUERY", r.code)
    }
}
