package app.diecast.police.museum

import app.diecast.police.museum.data.model.ALL
import app.diecast.police.museum.data.model.CountryRef
import app.diecast.police.museum.data.model.FilterOptions
import app.diecast.police.museum.data.model.Filters
import app.diecast.police.museum.data.model.NameRef
import app.diecast.police.museum.data.model.TagFilter
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.data.repository.buildSearchParams
import app.diecast.police.museum.ui.detail.buildTagChips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tags under the image of the detail screen are clickable: tapping one opens
 * the search screen with the matching filter applied.
 *
 * These tests verify the pure logic of that feature:
 *   1) the tag of a vehicle (which filter belongs to which text)
 *   2) the filter that is built from a tag
 *   3) the PostgREST parameters that are built from that filter
 */
class TagFilterTest {

    private val vehicle = Vehicle(
        id = 5965,
        modelName = "FIAT 238",
        emergencyService = "Police Car",
        countries = CountryRef(name = "Germany", flagEmoji = "🇩🇪"),
        manufacturers = NameRef(name = "Welly"),
        vehicleBrands = NameRef(name = "Siku"),
        series = NameRef(name = "POLICE CARS")
    )

    private val options = FilterOptions(
        countries = listOf("Germany", "Italy"),
        manufacturers = listOf("Welly"),
        brands = listOf("Siku"),
        series = listOf("POLICE CARS"),
        services = listOf("Police Car", "Ambulance"),
        seriesIds = mapOf("POLICE CARS" to 7),
        loaded = true
    )

    // ------------------------------------------------------------------ //
    //  1) The tags of a vehicle
    // ------------------------------------------------------------------ //

    @Test
    fun `every field of the vehicle produces a tag with its own filter`() {
        val chips = buildTagChips(vehicle)

        assertEquals("five tags are expected", 5, chips.size)
        assertEquals(
            listOf(
                TagFilter(country = "Germany"),
                TagFilter(brand = "Siku"),
                TagFilter(manufacturer = "Welly"),
                TagFilter(series = "POLICE CARS"),
                TagFilter(service = "Police Car")
            ),
            chips.map { it.filter }
        )
    }

    @Test
    fun `the country tag shows the flag and the other tags only their name`() {
        val chips = buildTagChips(vehicle)

        assertEquals("🇩🇪 Germany", chips[0].label)
        assertEquals("Siku", chips[1].label)
        assertEquals("Welly", chips[2].label)
        assertEquals("POLICE CARS", chips[3].label)
        assertEquals("Police Car", chips[4].label)
    }

    @Test
    fun `a missing value produces no tag`() {
        val bare = Vehicle(id = 1, modelName = "No data")

        assertEquals("an empty vehicle must not produce a tag", emptyList<TagFilter>(), buildTagChips(bare).map { it.filter })
    }

    @Test
    fun `the spoken description names the filter criterion`() {
        val chips = buildTagChips(vehicle)

        assertEquals("Filter by country Germany", chips[0].description)
        assertEquals("Filter by brand Siku", chips[1].description)
        assertEquals("Filter by manufacturer Welly", chips[2].description)
        assertEquals("Filter by series POLICE CARS", chips[3].description)
        assertEquals("Filter by emergency service Police Car", chips[4].description)
    }

    // ------------------------------------------------------------------ //
    //  2) The filter of a tag
    // ------------------------------------------------------------------ //

    @Test
    fun `a tag filter sets exactly one criterion and resets the others`() {
        val current = Filters(
            search = "Land Rover",
            country = "Italy",
            manufacturer = "Welly",
            brand = "Siku",
            series = "POLICE CARS",
            serviceGroup = "Fire",
            service = "Fire Engine",
            status = "Collection"
        )

        val result = current.withTagFilter(TagFilter(country = "Germany"))

        assertEquals("Germany", result.country)
        assertEquals(ALL, result.manufacturer)
        assertEquals(ALL, result.brand)
        assertEquals(ALL, result.series)
        assertEquals(ALL, result.serviceGroup)
        assertEquals(ALL, result.service)
        assertEquals(ALL, result.status)
        assertEquals("the search text is kept (like Clear filters does)", "Land Rover", result.search)
    }

    @Test
    fun `the emergency service tag also sets the group of the service`() {
        val result = Filters().withTagFilter(TagFilter(service = "Police Car"))

        assertEquals("Police Car", result.service)
        assertEquals("the group of the service is needed by the filter panel", "Police", result.serviceGroup)
    }

    @Test
    fun `an empty tag filter resets every criterion`() {
        val result = Filters(country = "Italy", brand = "Siku").withTagFilter(TagFilter())

        assertTrue("the filter must be empty again", result.isEmpty)
    }

    // ------------------------------------------------------------------ //
    //  3) The query of a tag filter
    // ------------------------------------------------------------------ //

    @Test
    fun `the country brand and manufacturer tags are applied on the embedded tables`() {
        val params = buildSearchParams(
            filters = Filters().withTagFilter(TagFilter(country = "Germany")),
            page = 0,
            pageSize = 50,
            options = options
        )

        assertEquals("eq.Germany", params["countries.name"])
    }

    @Test
    fun `the brand tag is applied on the vehicle_brands embed`() {
        val params = buildSearchParams(
            filters = Filters().withTagFilter(TagFilter(brand = "Siku")),
            page = 0,
            pageSize = 50,
            options = options
        )

        assertEquals("eq.Siku", params["vehicle_brands.name"])
        assertEquals("no other criterion may be sent", null, params["countries.name"])
    }

    @Test
    fun `the manufacturer tag is applied on the manufacturers embed`() {
        val params = buildSearchParams(
            filters = Filters().withTagFilter(TagFilter(manufacturer = "Welly")),
            page = 0,
            pageSize = 50,
            options = options
        )

        assertEquals("eq.Welly", params["manufacturers.name"])
    }

    @Test
    fun `the series tag is applied on the series_id column`() {
        val params = buildSearchParams(
            filters = Filters().withTagFilter(TagFilter(series = "POLICE CARS")),
            page = 0,
            pageSize = 50,
            options = options
        )

        // The series embed is a plain join, a filter on series.name would be ignored.
        assertEquals("eq.7", params["series_id"])
        assertEquals(null, params["series.name"])
    }

    @Test
    fun `an unknown series does not produce a broken filter`() {
        val params = buildSearchParams(
            filters = Filters().withTagFilter(TagFilter(series = "UNKNOWN")),
            page = 0,
            pageSize = 50,
            options = options
        )

        assertEquals(null, params["series_id"])
    }

    @Test
    fun `the emergency service tag is applied on the emergency_service column`() {
        val params = buildSearchParams(
            filters = Filters().withTagFilter(TagFilter(service = "Police Car")),
            page = 0,
            pageSize = 50,
            options = options
        )

        assertEquals("eq.Police Car", params["emergency_service"])
    }

    @Test
    fun `the search text is kept when a tag is tapped`() {
        val params = buildSearchParams(
            filters = Filters(search = "Land Rover").withTagFilter(TagFilter(country = "Germany")),
            page = 0,
            pageSize = 50,
            options = options
        )

        assertEquals("ilike.*Land Rover*", params["model_name"])
        assertEquals("eq.Germany", params["countries.name"])
    }
}
