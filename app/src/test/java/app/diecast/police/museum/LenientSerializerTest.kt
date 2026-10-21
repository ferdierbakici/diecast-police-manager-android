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
 * Lenient serializer'larin JSON tiplerine gore dogru cozumledigini dogrular.
 * Canli veride ayni kolon farkli tiplerde donuyor (orn. `rating` sayı ya da metin,
 * `is_customized` boolean ya da metin) - bu serializer'lar olmadan cozumleme cokuyor.
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
    fun `dogru tipler dogru cozulur`() {
        val p = json.decodeFromString<Probe>("""{"b":false,"i":0,"s":"x"}""")
        assertEquals(false, p.b)
        assertEquals(0, p.i)
        assertEquals("x", p.s)
    }

    @Test
    fun `null degerler null kalir`() {
        val p = json.decodeFromString<Probe>("""{"b":null,"i":null,"s":null}""")
        assertNull(p.b)
        assertNull(p.i)
        assertNull(p.s)
    }

    @Test
    fun `metin olarak gelen boolean'lar cozulur`() {
        assertEquals(true, json.decodeFromString<Probe>("""{"b":"true"}""").b)
        assertEquals(false, json.decodeFromString<Probe>("""{"b":"false"}""").b)
        assertEquals(true, json.decodeFromString<Probe>("""{"b":"1"}""").b)
        assertEquals(false, json.decodeFromString<Probe>("""{"b":"0"}""").b)
        assertEquals(true, json.decodeFromString<Probe>("""{"b":"yes"}""").b)
    }

    @Test
    fun `metin olarak gelen sayilar cozulur`() {
        assertEquals(5, json.decodeFromString<Probe>("""{"i":"5"}""").i)
        assertEquals(-3, json.decodeFromString<Probe>("""{"i":"-3"}""").i)
    }

    @Test
    fun `cozumlenemeyen degerler null olur (uygulama cokmez)`() {
        val p = json.decodeFromString<Probe>("""{"b":"belirsiz","i":"abc"}""")
        assertNull(p.b)
        assertNull(p.i)
    }
}
