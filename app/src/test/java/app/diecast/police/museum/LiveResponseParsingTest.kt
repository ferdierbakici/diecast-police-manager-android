package app.diecast.police.museum

import app.diecast.police.museum.data.model.StatusCandidate
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.domain.Status
import app.diecast.police.museum.domain.Timestamps
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Canli Supabase API'sinden alinan GERCEK yanitlar (app/src/test/resources/fixtures
 * klasorundeki json dosyalari) bu uygulamanin kendi modellerinden gecirilir. Boylece "canlida boyle donuyor, model
 * bunu cozebiliyor mu?" sorusu cihaz olmadan JVM'de kanitlanir.
 *
 * Fixture'lar calistirma aninda degil, onceden `curl` ile kaydedilmistir; testler
 * internete bagimli degildir.
 */
class LiveResponseParsingTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private fun decodeVehicles(text: String): List<Vehicle> =
        json.decodeFromString(text)

    // ------------------------------------------------------------------ //

    @Test
    fun `son eklenenler fixture'i tam Vehicle modeline cozulur`() {
        val text = fixture("last_added.json")
        val vehicles = decodeVehicles(text)

        assertTrue("en az 10 kayit beklenir, gelen: ${vehicles.size}", vehicles.size >= 10)
        assertTrue(
            "id'ler azalan olmali (order=id.desc)",
            vehicles.zipWithNext().all { (a, b) -> a.id > b.id }
        )

        val first = vehicles.first()
        assertNotNull("model_name okunmali", first.modelName)
        assertTrue("id > 0", first.id > 0)

        // Embed'ler NESNE olarak cozulmeli (dizi degil) - !inner formu boyle donuyor.
        assertNotNull("countries embed'i cozulmeli", first.countries)
        assertTrue(
            "countryName dolu olmali",
            !first.countryName.isNullOrBlank()
        )
        assertNotNull("manufacturers embed'i cozulmeli", first.manufacturers)

        // Ayni dosyadaki TUM kayitlar cozulebilmeli (tek bozuk satir sayfayi cokertmemeli).
        vehicles.forEachIndexed { i, v ->
            assertTrue("kayit $i id > 0 olmali", v.id > 0)
        }
    }

    @Test
    fun `son eklenenler ilk kaydin alanlari beklenen degerlerle dolu`() {
        val first = decodeVehicles(fixture("last_added.json")).first()

        assertEquals("ilk kayit id", 5965, first.id)
        assertEquals("model_name", "FIAT 238", first.modelName)
        assertEquals("model_year (LenientInt)", 1980, first.modelYear)
        assertEquals("scale", "1/43", first.scale)
        assertEquals("renk", "White", first.color)
        assertEquals("malzeme", "Diecast", first.material)
        assertEquals("ulke embed adi", "Italy", first.countryName)
        assertEquals("doviz", "EUR", first.currency)
        assertEquals("puan (LenientInt)", 0, first.rating)
        assertEquals("ozel yapim (LenientBool)", false, first.isCustomized)
        assertEquals("model_length NULL -> null", null, first.modelLength)
    }

    @Test
    fun `arama fixture'i cozulur ve sorgu terimi ile uyumlu`() {
        val vehicles = decodeVehicles(fixture("search_ambulance.json"))

        assertTrue("ambulance aramasi kayit dondurmeli", vehicles.isNotEmpty())
        assertTrue(
            "her kayit ambulance icermeli (case-insensitive)",
            vehicles.all {
                it.modelName?.lowercase()?.contains("ambulance") == true
            }
        )
    }

    @Test
    fun `amerikan aksanli ve unicode servis adlari bozulmadan cozulur`() {
        val vehicles = decodeVehicles(fixture("last_added.json"))
        val withService = vehicles.filter { !it.emergencyService.isNullOrBlank() }

        assertTrue("servis adi olan kayit beklenir", withService.isNotEmpty())

        // Em dash (U+2014) iceren servis adlari mojibake olmadan gelmeli.
        val emDash = withService.filter { it.emergencyService!!.contains('\u2014') }
        assertTrue(
            "en az bir servis adi em dash icermeli (gercek veri boyle)",
            emDash.isNotEmpty()
        )
        emDash.forEach {
            assertTrue(
                "em dash etrafinda bosluk korunmali: '${it.emergencyService}'",
                it.emergencyService!!.contains(" \u2014 ")
            )
        }
    }

    // ------------------------------------------------------------------ //

    @Test
    fun `recently in collection adaylari siralanip ilk 10 id web ile ayni cikar`() {
        val text = fixture("recent_candidates.json")
        val candidates: List<StatusCandidate> = json.decodeFromString(text)

        assertTrue("aday listesi 70+ olmali, gelen: ${candidates.size}", candidates.size >= 70)

        val topIds = candidates
            .sortedWith(
                compareByDescending<StatusCandidate> {
                    Timestamps.resolvedMillis(it.statusChangedAt, it.createdAt, it.updatedAt)
                }
            )
            .take(10)
            .map { it.id }

        // Web uygulamasinin canli referansi ile BIREBIR ayni olmali.
        assertEquals(
            listOf(3846, 5684, 5683, 5714, 5695, 5688, 2192, 5496, 5485, 2195),
            topIds
        )
    }

    @Test
    fun `timestamp cozumleme status_changed_at oncelikli ve UTC varsayiyor`() {
        // status_changed_at dolu -> o kullanilir
        assertEquals(
            Timestamps.parseIsoMillis("2026-10-21T15:10:45.90612"),
            Timestamps.resolvedMillis("2026-10-21T15:10:45.90612", "2020-01-01T00:00:00", null)
        )
        // status_changed_at yok -> created_at
        assertEquals(
            Timestamps.parseIsoMillis("2021-05-05T10:00:00"),
            Timestamps.resolvedMillis(null, "2021-05-05T10:00:00", "2022-01-01T00:00:00")
        )
        // ikisi de yok -> updated_at
        assertEquals(
            Timestamps.parseIsoMillis("2022-01-01T00:00:00"),
            Timestamps.resolvedMillis(null, null, "2022-01-01T00:00:00")
        )
        // hicbiri yok -> null
        assertEquals(null, Timestamps.resolvedMillis(null, null, null))
    }

    // ------------------------------------------------------------------ //

    @Test
    fun `durum filtre degerleri web ile ayni`() {
        // "All" -> filtre uygulanmaz (web getStatusFilterValues ile ayni)
        assertEquals(emptyList<String>(), Status.filterValues("All"))
        assertEquals(emptyList<String>(), Status.filterValues(""))

        // Collection -> 4 ham deger
        assertEquals(Status.COLLECTION, Status.filterValues("Collection"))
        assertEquals(
            listOf("Available", "Available - Displayed", "In Stock", "Collection"),
            Status.filterValues("Collection")
        )

        // Missing -> 2 ham deger
        assertEquals(listOf("Not Available", "Missing"), Status.filterValues("Missing"))

        // Diger ham durumlar (canlida: Ordered, To Process) kendi adiyla gecer
        assertEquals(listOf("Ordered"), Status.filterValues("Ordered"))
        assertEquals(listOf("To Process"), Status.filterValues("To Process"))
    }

    @Test
    fun `durum gorunen etiketi web ile ayni`() {
        assertEquals("Missing", Status.displayLabel(null))
        assertEquals("Missing", Status.displayLabel(""))
        assertEquals("Collection", Status.displayLabel("Available"))
        assertEquals("Collection", Status.displayLabel("Available - Displayed"))
        assertEquals("Collection", Status.displayLabel("In Stock"))
        assertEquals("Collection", Status.displayLabel("Collection"))
        assertEquals("Collection", Status.displayLabel("  collection  "))
        assertEquals("Missing", Status.displayLabel("Missing"))
        assertEquals("Missing", Status.displayLabel("Not Available"))
        // Canli veride bulunan iki ham durum etiketi degismeden gecer
        assertEquals("Ordered", Status.displayLabel("Ordered"))
        assertEquals("To Process", Status.displayLabel("To Process"))
    }

    @Test
    fun `model stats fixture'i cozulur`() {
        val text = fixture("model_stats.json")
        val arr = Json.parseToJsonElement(text)
        assertTrue("model_stats bir dizi olmali", arr is kotlinx.serialization.json.JsonArray)
    }
}
