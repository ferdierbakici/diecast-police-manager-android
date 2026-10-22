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
 * REAL responses captured from the live Supabase API (the json files in
 * app/src/test/resources/fixtures) are pushed through this app's own models. That way
 * the question "does this come back like that in production, and can the model parse it?"
 * is proven on the JVM without a device.
 *
 * The fixtures were recorded with `curl` beforehand, not at run time; the tests
 * do not depend on the internet.
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
    fun `the last added fixture decodes into the full Vehicle model`() {
        val text = fixture("last_added.json")
        val vehicles = decodeVehicles(text)

        assertTrue("at least 10 records are expected, got: ${vehicles.size}", vehicles.size >= 10)
        assertTrue(
            "the ids must be descending (order=id.desc)",
            vehicles.zipWithNext().all { (a, b) -> a.id > b.id }
        )

        val first = vehicles.first()
        assertNotNull("model_name must be read", first.modelName)
        assertTrue("id > 0", first.id > 0)

        // The embeds must be decoded as OBJECTS (not arrays) - the !inner form returns them that way.
        assertNotNull("the countries embed must be decoded", first.countries)
        assertTrue(
            "countryName must not be empty",
            !first.countryName.isNullOrBlank()
        )
        assertNotNull("the manufacturers embed must be decoded", first.manufacturers)

        // ALL records in the same file must be decodable (a single broken row must not break the page).
        vehicles.forEachIndexed { i, v ->
            assertTrue("record $i must have id > 0", v.id > 0)
        }
    }

    @Test
    fun `the fields of the first record of the last added fixture hold the expected values`() {
        val first = decodeVehicles(fixture("last_added.json")).first()

        assertEquals("the id of the first record", 5965, first.id)
        assertEquals("model_name", "FIAT 238", first.modelName)
        assertEquals("model_year (LenientInt)", 1980, first.modelYear)
        assertEquals("scale", "1/43", first.scale)
        assertEquals("color", "White", first.color)
        assertEquals("material", "Diecast", first.material)
        assertEquals("the country embed name", "Italy", first.countryName)
        assertEquals("currency", "EUR", first.currency)
        assertEquals("rating (LenientInt)", 0, first.rating)
        assertEquals("customization (LenientBool)", false, first.isCustomized)
        assertEquals("model_length NULL -> null", null, first.modelLength)
    }

    @Test
    fun `the search fixture decodes and matches the query term`() {
        val vehicles = decodeVehicles(fixture("search_ambulance.json"))

        assertTrue("the ambulance search must return records", vehicles.isNotEmpty())
        assertTrue(
            "every record must contain ambulance (case-insensitive)",
            vehicles.all {
                it.modelName?.lowercase()?.contains("ambulance") == true
            }
        )
    }

    @Test
    fun `service names with american accents and unicode are decoded without corruption`() {
        val vehicles = decodeVehicles(fixture("last_added.json"))
        val withService = vehicles.filter { !it.emergencyService.isNullOrBlank() }

        assertTrue("records with a service name are expected", withService.isNotEmpty())

        // Service names containing an em dash (U+2014) must arrive without mojibake.
        val emDash = withService.filter { it.emergencyService!!.contains('\u2014') }
        assertTrue(
            "at least one service name must contain an em dash (the real data is like that)",
            emDash.isNotEmpty()
        )
        emDash.forEach {
            assertTrue(
                "the spaces around the em dash must be preserved: '${it.emergencyService}'",
                it.emergencyService!!.contains(" \u2014 ")
            )
        }
    }

    // ------------------------------------------------------------------ //

    @Test
    fun `the recently in collection candidates are sorted and the first 10 ids match the web app`() {
        val text = fixture("recent_candidates.json")
        val candidates: List<StatusCandidate> = json.decodeFromString(text)

        assertTrue("the candidate list must have 70+ entries, got: ${candidates.size}", candidates.size >= 70)

        val topIds = candidates
            .sortedWith(
                compareByDescending<StatusCandidate> {
                    Timestamps.resolvedMillis(it.statusChangedAt, it.createdAt, it.updatedAt)
                }
            )
            .take(10)
            .map { it.id }

        // It must be EXACTLY the same as the live reference of the web app.
        assertEquals(
            listOf(3846, 5684, 5683, 5714, 5695, 5688, 2192, 5496, 5485, 2195),
            topIds
        )
    }

    @Test
    fun `the timestamp resolution prefers status_changed_at and assumes UTC`() {
        // status_changed_at is present -> it is used
        assertEquals(
            Timestamps.parseIsoMillis("2026-10-21T15:10:45.90612"),
            Timestamps.resolvedMillis("2026-10-21T15:10:45.90612", "2020-01-01T00:00:00", null)
        )
        // status_changed_at is missing -> created_at
        assertEquals(
            Timestamps.parseIsoMillis("2021-05-05T10:00:00"),
            Timestamps.resolvedMillis(null, "2021-05-05T10:00:00", "2022-01-01T00:00:00")
        )
        // both are missing -> updated_at
        assertEquals(
            Timestamps.parseIsoMillis("2022-01-01T00:00:00"),
            Timestamps.resolvedMillis(null, null, "2022-01-01T00:00:00")
        )
        // none of them -> null
        assertEquals(null, Timestamps.resolvedMillis(null, null, null))
    }

    // ------------------------------------------------------------------ //

    @Test
    fun `the status filter values are the same as the web app`() {
        // "All" -> no filter is applied (same as the web app's getStatusFilterValues)
        assertEquals(emptyList<String>(), Status.filterValues("All"))
        assertEquals(emptyList<String>(), Status.filterValues(""))

        // Collection -> 4 raw values
        assertEquals(Status.COLLECTION, Status.filterValues("Collection"))
        assertEquals(
            listOf("Available", "Available - Displayed", "In Stock", "Collection"),
            Status.filterValues("Collection")
        )

        // Missing -> 2 raw values
        assertEquals(listOf("Not Available", "Missing"), Status.filterValues("Missing"))

        // Other raw statuses (in the live data: Ordered, To Process) pass through with their own name
        assertEquals(listOf("Ordered"), Status.filterValues("Ordered"))
        assertEquals(listOf("To Process"), Status.filterValues("To Process"))
    }

    @Test
    fun `the visible status label is the same as the web app`() {
        assertEquals("Missing", Status.displayLabel(null))
        assertEquals("Missing", Status.displayLabel(""))
        assertEquals("Collection", Status.displayLabel("Available"))
        assertEquals("Collection", Status.displayLabel("Available - Displayed"))
        assertEquals("Collection", Status.displayLabel("In Stock"))
        assertEquals("Collection", Status.displayLabel("Collection"))
        assertEquals("Collection", Status.displayLabel("  collection  "))
        assertEquals("Missing", Status.displayLabel("Missing"))
        assertEquals("Missing", Status.displayLabel("Not Available"))
        // The two raw statuses found in the live data pass through with an unchanged label
        assertEquals("Ordered", Status.displayLabel("Ordered"))
        assertEquals("To Process", Status.displayLabel("To Process"))
    }

    @Test
    fun `the model stats fixture decodes`() {
        val text = fixture("model_stats.json")
        val arr = Json.parseToJsonElement(text)
        assertTrue("model_stats must be an array", arr is kotlinx.serialization.json.JsonArray)
    }
}
