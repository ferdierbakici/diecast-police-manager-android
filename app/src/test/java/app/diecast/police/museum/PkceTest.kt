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
 * PKCE (RFC 7636) dogrulamalari.
 *
 * Canli Supabase (GoTrue v2.197.0) davranisi dogrulanmisti:
 *   - `GET /auth/v1/authorize?...&flow_type=pkce&code_challenge=<43 karakter>`
 *     -> HTTP 302 (kabul)
 *   - 200 karakterlik code_challenge -> HTTP 400 (S256 43 karakter olmali)
 *   - `POST /auth/v1/token?grant_type=pkce` {"auth_code","code_verifier"}
 *     -> HTTP 404 flow_state_not_found (alan adlari dogru, uc nokta aktif)
 */
class PkceTest {

    @Test
    fun `code_verifier RFC 7636 uzunluk araliginda`() {
        repeat(20) {
            val verifier = Pkce.newCodeVerifier()
            assertTrue(
                "verifier 43-128 karakter olmali, ${verifier.length} bulundu",
                verifier.length in 43..128
            )
        }
    }

    @Test
    fun `code_verifier sadece base64url karakterleri icerir`() {
        repeat(20) {
            val verifier = Pkce.newCodeVerifier()
            assertTrue(
                "base64url olmayan karakter: $verifier",
                verifier.matches(Regex("^[A-Za-z0-9\\-._~]+$"))
            )
        }
    }

    @Test
    fun `code_verifier her seferinde farklidir`() {
        val values = (1..50).map { Pkce.newCodeVerifier() }.toSet()
        assertEquals("50 farkli verifier uretilmeli", 50, values.size)
    }

    @Test
    fun `code_challenge S256 ve 43 karakter`() {
        val verifier = Pkce.newCodeVerifier()
        val challenge = Pkce.codeChallenge(verifier)
        assertEquals("S256 code_challenge 43 karakter olmali", 43, challenge.length)
    }

    @Test
    fun `code_challenge base64url SHA-256 ile birebir ayni`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        )
        assertEquals(expected, Pkce.codeChallenge(verifier))
    }

    @Test
    fun `RFC 7636 test vektörü`() {
        // RFC 7636 Ek B - known test vector
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expected, Pkce.codeChallenge(verifier))
    }

    @Test
    fun `authorize URL PKCE parametrelerini icerir`() {
        val verifier = Pkce.newCodeVerifier()
        val url = Pkce.authorizeUrl(
            "https://example.supabase.co/auth/v1",
            "diecastpolice://auth-callback",
            verifier
        )

        assertTrue(url.startsWith("https://example.supabase.co/auth/v1/authorize?"))
        assertTrue("provider=google olmali", url.contains("provider=google"))
        assertTrue("flow_type=pkce olmali", url.contains("flow_type=pkce"))
        assertTrue(
            "code_challenge_method=s256 olmali",
            url.contains("code_challenge_method=s256")
        )
        assertTrue(
            "code_challenge dogru hesaplanmali",
            url.contains("code_challenge=${Pkce.codeChallenge(verifier)}")
        )
    }

    @Test
    fun `redirect_to URI olarak kodlanir`() {
        val verifier = Pkce.newCodeVerifier()
        val url = Pkce.authorizeUrl(
            "https://example.supabase.co/auth/v1",
            "diecastpolice://auth-callback",
            verifier
        )
        val raw = url.substringAfter("redirect_to=").substringBefore("&")

        // Kodlanmis olmali: '://' duz halde geciyor olmamali
        assertNotEquals("redirect_to kodlanmamis", "diecastpolice://auth-callback", raw)
        assertTrue(
            "kacisli deger hatali: $raw",
            !raw.contains("://")
        )
        // Ama sunucunun cözecegi deger ayni olmali
        assertEquals(
            "diecastpolice://auth-callback",
            URLDecoder.decode(raw, "UTF-8")
        )
    }

    @Test
    fun `authorize URL gercek deep link ile tutarli`() {
        val verifier = Pkce.newCodeVerifier()
        val url = Pkce.authorizeUrl(
            "https://eojfccviyaambajvlsqu.supabase.co/auth/v1",
            "diecastpolice://auth-callback",
            verifier
        )
        // Canli Sunucu bu parametreleri kabul ediyor (HTTP 302 dondu)
        assertTrue(url.contains("eojfccviyaambajvlsqu.supabase.co"))
        assertTrue(url.contains("redirect_to=diecastpolice%3A%2F%2Fauth-callback"))
    }
}
