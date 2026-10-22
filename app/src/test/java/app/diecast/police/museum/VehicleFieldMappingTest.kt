package app.diecast.police.museum

import app.diecast.police.museum.data.model.Vehicle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SerialName check: when a JSON key does not match the Kotlin field the value
 * silently becomes null (example: `@SerialName("is_customized")` was missing for
 * `isCustomized` - this test caught it).
 *
 * Here we verify that every key in the live fixture really reaches the model.
 */
class VehicleFieldMappingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun `every field that is filled in the json is also filled in the model`() {
        val text = fixture("last_added.json")
        val array = Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonArray
        val vehicles: List<Vehicle> = json.decodeFromString(text)

        // Compare the raw JSON of the first record with the decoded model.
        val raw = array.first() as JsonObject
        val v = vehicles.first()

        fun rawIsNonNil(key: String): Boolean {
            val el = raw[key] ?: return false
            return el !is kotlinx.serialization.json.JsonNull
        }

        // Field name -> (json key, does the value reach the model as null?)
        val checks: List<Triple<String, String, Boolean>> = listOf(
            Triple("model_name", "model_name", v.modelName != null),
            Triple("model_year", "model_year", v.modelYear != null),
            Triple("scale", "scale", v.scale != null),
            Triple("availability_status", "availability_status", v.availabilityStatus != null),
            Triple("emergency_service", "emergency_service", v.emergencyService != null),
            Triple("model_code", "model_code", v.modelCode != null),
            Triple("color", "color", v.color != null),
            Triple("material", "material", v.material != null),
            Triple("rating", "rating", v.rating != null),
            Triple("currency", "currency", v.currency != null),
            Triple("acquisition_date", "acquisition_date", v.acquisitionDate != null),
            Triple("market_value", "market_value", v.marketValue != null),
            // The actual bug was here:
            Triple("is_customized", "is_customized", v.isCustomized != null),
            Triple("created_at", "created_at", v.createdAt != null),
            Triple("updated_at", "updated_at", v.updatedAt != null),
            Triple("country_id", "country_id", v.countryId != null),
            Triple("manufacturer_id", "manufacturer_id", v.manufacturerId != null),
            Triple("vehicle_brand_id", "vehicle_brand_id", v.vehicleBrandId != null)
        )

        val mismatches = checks.filter { (_, key, mapped) -> rawIsNonNil(key) && !mapped }
        org.junit.Assert.assertTrue(
            "These fields are filled in the JSON but did not reach the model (the SerialName may be missing): " +
                mismatches.joinToString { it.first },
            mismatches.isEmpty()
        )
    }

    @Test
    fun `the embed fields reach the model`() {
        val v: Vehicle = json.decodeFromString<List<Vehicle>>(fixture("last_added.json")).first()
        assertNotNull("countries.name", v.countryName)
        assertNotNull("manufacturers.name", v.manufacturerName)
        assertNotNull("vehicle_brands.name", v.brandName)
    }

    @Test
    fun `the is_customized value is decoded as false`() {
        val v: Vehicle = json.decodeFromString<List<Vehicle>>(fixture("last_added.json")).first()
        org.junit.Assert.assertEquals(false, v.isCustomized)
    }

    @Test
    fun `all fixture records are decoded the same way`() {
        // is_customized must be false in 10/10 records (they all come back the same way).
        val all: List<Vehicle> = json.decodeFromString(fixture("last_added.json"))
        org.junit.Assert.assertTrue(
            "all records must be decoded as is_customized=false",
            all.all { it.isCustomized == false }
        )
    }

    // ------------------------------------------------------------------ //
    //  Customized vehicle (from live data, id=4043)
    // ------------------------------------------------------------------ //

    @Test
    fun `a customized vehicle is decoded and is_customized comes through as true`() {
        val v: Vehicle = json.decodeFromString<List<Vehicle>>(fixture("customized.json")).first()

        assertEquals(4043, v.id)
        assertEquals("Peugeot 403", v.modelName)
        // model_year is NULL in live data - it must stay null
        assertNull("model_year must be NULL", v.modelYear)

        // THIS TEST is the one that catches a missing `@SerialName("is_customized")`.
        // Without the SerialName, isCustomized always stays null and the
        // "Customization" section never shows up on the detail screen.
        assertEquals("is_customized must be decoded as true", true, v.isCustomized)
        assertEquals("customizer_name", "Diecast Police", v.customizerName)
    }

    @Test
    fun `a vehicle that is not customized returns false`() {
        val v: Vehicle = json.decodeFromString<List<Vehicle>>(fixture("last_added.json")).first()
        assertEquals(false, v.isCustomized)
    }
}
