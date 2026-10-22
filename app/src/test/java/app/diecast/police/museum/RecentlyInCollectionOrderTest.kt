package app.diecast.police.museum

import app.diecast.police.museum.data.model.StatusCandidate
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.domain.Timestamps
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guarantees the ORDER of the "Recently in Collection" section.
 *
 * This section runs in two steps:
 *   1) a lightweight candidate list is fetched and sorted by time on the client -> topIds
 *   2) the detailed records for topIds are fetched
 *
 * In step 2 PostgREST returns the rows in id order (adding order=id.desc
 * produces a completely different order). Therefore the order computed in
 * step 1 has to be RESTORED after step 2. This test verifies that restore logic.
 *
 * Regression: adding `order=id.desc` put this section in the wrong order
 * for 10/10 records.
 */
class RecentlyInCollectionOrderTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** The live reference of the web app (also verified in a unit test). */
    private val expectedOrder = listOf(3846, 5684, 5683, 5714, 5695, 5688, 2192, 5496, 5485, 2195)

    /** Step 1: sort the candidates by time and take the first N ids. */
    private fun topIds(limit: Int): List<Int> =
        json.decodeFromString<List<StatusCandidate>>(fixture("recent_candidates.json"))
            .sortedWith(
                compareByDescending<StatusCandidate> {
                    Timestamps.resolvedMillis(it.statusChangedAt, it.createdAt, it.updatedAt)
                }
            )
            .take(limit)
            .map { it.id }

    @Test
    fun `step 1 the candidate order is exactly the same as the web reference`() {
        assertEquals(expectedOrder, topIds(10))
    }

    @Test
    fun `in step 2 PostgREST returns the rows in id order - that is why the restore is required`() {
        // The detail response arrives in ascending id order (NO order)...
        val raw: List<Vehicle> = json.decodeFromString(fixture("recent_detail.json"))
        val apiOrder = raw.map { it.id }
        assertEquals(
            "the raw order of the detail response must be ascending by id",
            apiOrder.sorted(),
            apiOrder
        )
        // ...and this order differs from the expected one.
        assertEquals(
            "the raw API order must differ from the expected order (otherwise the test is meaningless)",
            false,
            apiOrder == expectedOrder
        )
    }

    @Test
    fun `the restore logic produces the correct order`() {
        // What the repository does: match by the order of topIds and collect with mapNotNull.
        val byId = json.decodeFromString<List<Vehicle>>(fixture("recent_detail.json"))
            .associateBy { it.id }

        val result = topIds(10).mapNotNull { byId[it] }

        assertEquals("all 10 records must match", 10, result.size)
        assertEquals(
            "the order shown on the screen must be the same as the web reference",
            expectedOrder,
            result.map { it.id }
        )
    }

    @Test
    fun `after the restore every record maps to the right vehicle`() {
        val byId = json.decodeFromString<List<Vehicle>>(fixture("recent_detail.json"))
            .associateBy { it.id }
        val result = topIds(10).mapNotNull { byId[it] }

        // The expected names of the first 3 records (verified against live data).
        assertEquals(3846, result[0].id)
        assertEquals("Fiat 1100E", result[0].modelName)
        assertEquals(5684, result[1].id)
        assertEquals("Citroën DS19", result[1].modelName)
        assertEquals(5683, result[2].id)
        assertEquals("Chevrolet Corvette", result[2].modelName)
    }

    @Test
    fun `if the order were REVERSED (order=id desc) all 10 records would be wrong`() {
        // Proof of the regression: the old code used `order=id.desc`.
        val byId = json.decodeFromString<List<Vehicle>>(fixture("recent_detail.json"))
            .associateBy { it.id }
        val top = topIds(10)
        val oldBuggyOrder = top.sortedDescending()          // The effect of order=id.desc
        val correctOrder = top

        assertEquals(
            "the old behaviour and the correct behaviour must differ for ALL 10 records",
            10,
            oldBuggyOrder.zip(correctOrder).count { (a, b) -> a != b }
        )
        // The byId matching still works; the problem was only the order.
        assertEquals(10, oldBuggyOrder.count { byId.containsKey(it) })
    }
}
