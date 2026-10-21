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
 * SerialName denetimi: JSON anahtari ile Kotlin alani eslesmezse deger SESSIZCE null olur
 * (ornek: `isCustomized` icin `@SerialName("is_customized")` unutulmustu - bu test yakaladi).
 *
 * Burada canli fixture'daki her anahtarin modele gercekten doldugunu dogrulariz.
 */
class VehicleFieldMappingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun `json'da dolu olan her alan modele de dolu yansimali`() {
        val text = fixture("last_added.json")
        val array = Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonArray
        val vehicles: List<Vehicle> = json.decodeFromString(text)

        // Ilk kaydin ham JSON'u ile cozulen modeli karsilastir.
        val raw = array.first() as JsonObject
        val v = vehicles.first()

        fun rawIsNonNil(key: String): Boolean {
            val el = raw[key] ?: return false
            return el !is kotlinx.serialization.json.JsonNull
        }

        // Alan adi -> (json anahtari, modele yansiyan deger null mu?)
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
            // Asil hata buradaydi:
            Triple("is_customized", "is_customized", v.isCustomized != null),
            Triple("created_at", "created_at", v.createdAt != null),
            Triple("updated_at", "updated_at", v.updatedAt != null),
            Triple("country_id", "country_id", v.countryId != null),
            Triple("manufacturer_id", "manufacturer_id", v.manufacturerId != null),
            Triple("vehicle_brand_id", "vehicle_brand_id", v.vehicleBrandId != null)
        )

        val mismatches = checks.filter { (_, key, mapped) -> rawIsNonNil(key) && !mapped }
        org.junit.Assert.assertTrue(
            "Su alanlar JSON'da dolu ama modele yansimadi (SerialName eksik olabilir): " +
                mismatches.joinToString { it.first },
            mismatches.isEmpty()
        )
    }

    @Test
    fun `embed alanlari modele doluyor`() {
        val v: Vehicle = json.decodeFromString<List<Vehicle>>(fixture("last_added.json")).first()
        assertNotNull("countries.name", v.countryName)
        assertNotNull("manufacturers.name", v.manufacturerName)
        assertNotNull("vehicle_brands.name", v.brandName)
    }

    @Test
    fun `is_customized degeri false olarak cozulur`() {
        val v: Vehicle = json.decodeFromString<List<Vehicle>>(fixture("last_added.json")).first()
        org.junit.Assert.assertEquals(false, v.isCustomized)
    }

    @Test
    fun `tum fixture kayitlari ayni sekilde cozulur`() {
        // 10 kaydin 10'unda da is_customized false olmali (hepsi ayni sekilde donuyor).
        val all: List<Vehicle> = json.decodeFromString(fixture("last_added.json"))
        org.junit.Assert.assertTrue(
            "tum kayitlar is_customized=false cozulmeli",
            all.all { it.isCustomized == false }
        )
    }

    // ------------------------------------------------------------------ //
    //  Ozel yapimli arac (canli veriden id=4043)
    // ------------------------------------------------------------------ //

    @Test
    fun `ozel yapimli arac cozulur ve is_customized true gelir`() {
        val v: Vehicle = json.decodeFromString<List<Vehicle>>(fixture("customized.json")).first()

        assertEquals(4043, v.id)
        assertEquals("Peugeot 403", v.modelName)
        // model_year canli veride NULL - null kalmali
        assertNull("model_year NULL olmali", v.modelYear)

        // BU TEST, `@SerialName("is_customized")` eksikligini yakalayan testtir.
        // SerialName yoksa isCustomized her zaman null kalir ve detay ekraninda
        // "Ozel Yapim" bolumu hic gosterilmez.
        assertEquals("is_customized true cozulmeli", true, v.isCustomized)
        assertEquals("customizer_name", "Diecast Police", v.customizerName)
    }

    @Test
    fun `ozel yapimli olmayan arac false doner`() {
        val v: Vehicle = json.decodeFromString<List<Vehicle>>(fixture("last_added.json")).first()
        assertEquals(false, v.isCustomized)
    }
}
