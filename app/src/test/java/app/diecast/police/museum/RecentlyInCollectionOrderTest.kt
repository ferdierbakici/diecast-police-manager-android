package app.diecast.police.museum

import app.diecast.police.museum.data.model.StatusCandidate
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.domain.Timestamps
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Koleksiyona son katilanlar" bolumunun SIRA garantisi.
 *
 * Bu bolum 2 adimda calisir:
 *   1) hafif aday listesi cekilir ve istemcide zamana gore siralanir -> topIds
 *   2) topIds icin detayli kayitlar cekilir
 *
 * 2. adimda PostgREST sonuclari id sirasinda dondurur (order=id.desc eklenirse
 * tamamen farkli bir sira gelir). Bu yuzden 1. adimda hesaplanan sira
 * 2. adimdan sonra GERI KURULMALIDIR. Bu test o geri kurma mantigini dogrular.
 *
 * Regresyon: `order=id.desc` eklenmesi bu bolumu 10/10 kayitta yanlis siraya
 * sokuyordu.
 */
class RecentlyInCollectionOrderTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Web uygulamasinin canli referansi (birim testinde de dogrulanmistir). */
    private val expectedOrder = listOf(3846, 5684, 5683, 5714, 5695, 5688, 2192, 5496, 5485, 2195)

    /** 1. adim: adaylari zamana gore sirala, ilk N id'yi al. */
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
    fun `1 adim aday siralamasi web referansi ile birebir ayni`() {
        assertEquals(expectedOrder, topIds(10))
    }

    @Test
    fun `2 adimda PostgREST id sirasinda dondurur - bu yuzden geri kurma sart`() {
        // Detay yaniti id'ye gore artan sirada gelir (order YOK)...
        val raw: List<Vehicle> = json.decodeFromString(fixture("recent_detail.json"))
        val apiOrder = raw.map { it.id }
        assertEquals(
            "detay yanitinin ham sirasi id'ye gore artan olmali",
            apiOrder.sorted(),
            apiOrder
        )
        // ...ve bu sira beklenen siradan FARKLIDIR.
        assertEquals(
            "ham API sirasi beklenen siradan farkli olmali (aksi halde test anlamsiz)",
            false,
            apiOrder == expectedOrder
        )
    }

    @Test
    fun `geri kurma mantigi dogru sirayi uretir`() {
        // Repository'nin yaptigi: topIds sirasina gore esle ve mapNotNull ile topla.
        val byId = json.decodeFromString<List<Vehicle>>(fixture("recent_detail.json"))
            .associateBy { it.id }

        val result = topIds(10).mapNotNull { byId[it] }

        assertEquals("10 kayit da eslesmeli", 10, result.size)
        assertEquals(
            "ekranda gorunen sira web referansiyla ayni olmali",
            expectedOrder,
            result.map { it.id }
        )
    }

    @Test
    fun `geri kurma sonrasi her kayit dogru araca karsilik gelir`() {
        val byId = json.decodeFromString<List<Vehicle>>(fixture("recent_detail.json"))
            .associateBy { it.id }
        val result = topIds(10).mapNotNull { byId[it] }

        // Ilk 3 kaydin beklenen adlari (canli veriden dogrulandi).
        assertEquals(3846, result[0].id)
        assertEquals("Fiat 1100E", result[0].modelName)
        assertEquals(5684, result[1].id)
        assertEquals("Citroën DS19", result[1].modelName)
        assertEquals(5683, result[2].id)
        assertEquals("Chevrolet Corvette", result[2].modelName)
    }

    @Test
    fun `siralamanin TERSI olsaydi (order=id desc) 10 kaydin 10u yanlis olurdu`() {
        // Regresyonun kaniti: eski kod `order=id.desc` kullaniyordu.
        val byId = json.decodeFromString<List<Vehicle>>(fixture("recent_detail.json"))
            .associateBy { it.id }
        val top = topIds(10)
        val oldBuggyOrder = top.sortedDescending()          // order=id.desc'in etkisi
        val correctOrder = top

        assertEquals(
            "eski davranis ile dogru davranis 10 kaydin TAMAMINDA farkli olmali",
            10,
            oldBuggyOrder.zip(correctOrder).count { (a, b) -> a != b }
        )
        // byId eslesmesi hala calisiyor; sorun sadece siradaydi.
        assertEquals(10, oldBuggyOrder.count { byId.containsKey(it) })
    }
}
