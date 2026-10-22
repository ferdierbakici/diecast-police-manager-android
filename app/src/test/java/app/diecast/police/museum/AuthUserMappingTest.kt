package app.diecast.police.museum

import app.diecast.police.museum.data.model.AuthSession
import app.diecast.police.museum.data.model.AuthUser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests the mapping of the user name in the session responses.
 *
 * Google OAuth provides `user_metadata.full_name`; the Supabase local display
 * name (`display_name`) is set by the user.
 *
 * Priority order: display_name -> full_name -> name -> the local part of the email address.
 */
class AuthUserMappingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `the google full_name is used`() {
        val user = json.decodeFromString<AuthUser>(
            """{"id":"u1","email":"ferdi@example.com","user_metadata":{"full_name":"Ferdi Erbakici"}}"""
        )
        assertEquals("Ferdi Erbakici", user.displayName)
    }

    @Test
    fun `display_name has priority over full_name`() {
        val user = json.decodeFromString<AuthUser>(
            """{"id":"u1","email":"ferdi@example.com","user_metadata":{"display_name":"Sample User","full_name":"Ferdi Erbakici"}}"""
        )
        assertEquals("Sample User", user.displayName)
    }

    @Test
    fun `the google name field is used`() {
        val user = json.decodeFromString<AuthUser>(
            """{"id":"u1","email":"ferdi@example.com","user_metadata":{"name":"Ferdi E"}}"""
        )
        assertEquals("Ferdi E", user.displayName)
    }

    @Test
    fun `without metadata the local part of the email is used`() {
        val user = json.decodeFromString<AuthUser>(
            """{"id":"u1","email":"ferdi@example.com"}"""
        )
        assertEquals("ferdi", user.displayName)
    }

    @Test
    fun `blank names fall back to the local part of the email`() {
        val user = json.decodeFromString<AuthUser>(
            """{"id":"u1","email":"ferdi@example.com","user_metadata":{"display_name":"","full_name":"  "}}"""
        )
        assertEquals("ferdi", user.displayName)
    }

    @Test
    fun `the user info is read from the pkce token response`() {
        val session = json.decodeFromString<AuthSession>(
            """{"access_token":"a","refresh_token":"r","expires_in":3600,"token_type":"bearer",
               "user":{"id":"u1","email":"ferdi@example.com","user_metadata":{"full_name":"Ferdi Erbakici"}}}"""
        )
        assertNotNull(session.user)
        assertEquals("Ferdi Erbakici", session.user?.displayName)
        assertEquals("u1", session.user?.id)
    }
}
