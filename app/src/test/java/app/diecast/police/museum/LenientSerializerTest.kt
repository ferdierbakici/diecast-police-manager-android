package app.diecast.police.museum

import app.diecast.police.museum.data.model.LenientBool
import app.diecast.police.museum.data.model.LenientInt
import app.diecast.police.museum.data.model.LenientString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies that the lenient serializers parse the JSON types correctly.
 * In live data the same column comes back with different types (e.g. `rating` as a
 * number or as text, `is_customized` as a boolean or as text) - without these
 * serializers the parsing fails.
 */
class LenientSerializerTest {

    @Serializable
    data class Probe(
        @Serializable(with = LenientBool::class) val b: Boolean? = null,
        @Serializable(with = LenientInt::class) val i: Int? = null,
        @Serializable(with = LenientString::class) val s: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `the correct types are decoded correctly`() {
        val p = json.decodeFromString<Probe>("""{"b":false,"i":0,"s":"x"}""")
        assertEquals(false, p.b)
        assertEquals(0, p.i)
        assertEquals("x", p.s)
    }

    @Test
    fun `null values stay null`() {
        val p = json.decodeFromString<Probe>("""{"b":null,"i":null,"s":null}""")
        assertNull(p.b)
        assertNull(p.i)
        assertNull(p.s)
    }

    @Test
    fun `booleans delivered as text are decoded`() {
        assertEquals(true, json.decodeFromString<Probe>("""{"b":"true"}""").b)
        assertEquals(false, json.decodeFromString<Probe>("""{"b":"false"}""").b)
        assertEquals(true, json.decodeFromString<Probe>("""{"b":"1"}""").b)
        assertEquals(false, json.decodeFromString<Probe>("""{"b":"0"}""").b)
        assertEquals(true, json.decodeFromString<Probe>("""{"b":"yes"}""").b)
    }

    @Test
    fun `numbers delivered as text are decoded`() {
        assertEquals(5, json.decodeFromString<Probe>("""{"i":"5"}""").i)
        assertEquals(-3, json.decodeFromString<Probe>("""{"i":"-3"}""").i)
    }

    @Test
    fun `values that cannot be decoded become null (the app does not crash)`() {
        val p = json.decodeFromString<Probe>("""{"b":"unknown","i":"abc"}""")
        assertNull(p.b)
        assertNull(p.i)
    }
}
