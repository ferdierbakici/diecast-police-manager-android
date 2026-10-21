package app.diecast.police.museum

import app.diecast.police.museum.data.model.AuthSession
import app.diecast.police.museum.data.model.AuthUser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Oturum yanitlarindaki kullanici adi eslemesi.
 *
 * Google OAuth `user_metadata.full_name` verir; Supabase yerel alan adi
 * (`display_name`) kullanici tarafindan ayarlanir.
 *
 * Oncelik sirasi: display_name -> full_name -> name -> e-posta yerel adi.
 */
class AuthUserMappingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `google full_name kullanilir`() {
        val user = json.decodeFromString<AuthUser>(
            """{"id":"u1","email":"ferdi@example.com","user_metadata":{"full_name":"Ferdi Erbakici"}}"""
        )
        assertEquals("Ferdi Erbakici", user.displayName)
    }

    @Test
    fun `display_name full_name'den onceliklidir`() {
        val user = json.decodeFromString<AuthUser>(
            """{"id":"u1","email":"ferdi@example.com","user_metadata":{"display_name":"Kullanici Adi","full_name":"Ferdi Erbakici"}}"""
        )
        assertEquals("Kullanici Adi", user.displayName)
    }

    @Test
    fun `google name alani kullanilir`() {
        val user = json.decodeFromString<AuthUser>(
            """{"id":"u1","email":"ferdi@example.com","user_metadata":{"name":"Ferdi E"}}"""
        )
        assertEquals("Ferdi E", user.displayName)
    }

    @Test
    fun `metadata yoksa e-posta yerel adi kullanilir`() {
        val user = json.decodeFromString<AuthUser>(
            """{"id":"u1","email":"ferdi@example.com"}"""
        )
        assertEquals("ferdi", user.displayName)
    }

    @Test
    fun `bos adlar e-posta yerel adina duser`() {
        val user = json.decodeFromString<AuthUser>(
            """{"id":"u1","email":"ferdi@example.com","user_metadata":{"display_name":"","full_name":"  "}}"""
        )
        assertEquals("ferdi", user.displayName)
    }

    @Test
    fun `pkce token yanitinda kullanici bilgisi okunur`() {
        val session = json.decodeFromString<AuthSession>(
            """{"access_token":"a","refresh_token":"r","expires_in":3600,"token_type":"bearer",
               "user":{"id":"u1","email":"ferdi@example.com","user_metadata":{"full_name":"Ferdi Erbakici"}}}"""
        )
        assertNotNull(session.user)
        assertEquals("Ferdi Erbakici", session.user?.displayName)
        assertEquals("u1", session.user?.id)
    }
}
