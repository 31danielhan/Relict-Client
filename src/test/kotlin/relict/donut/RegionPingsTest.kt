package relict.donut

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegionPingsTest {
    @Test
    fun aCellYouHaveNotBeenInHasNoPing() {
        assertNull(RegionPings().get(3, 4))
    }

    @Test
    fun theFirstReadingIsTakenAsIsAndLaterOnesAreAveraged() {
        val p = RegionPings()
        p.record(2, 2, 40)
        assertEquals(40, p.get(2, 2))
        p.record(2, 2, 80)
        assertEquals(50, p.get(2, 2)) // 40 + (80 - 40) * 0.25
        assertEquals(1, p.count)
    }

    @Test
    fun readingsThatMeanNothingAreIgnored() {
        val p = RegionPings()
        p.record(0, 0, 0)
        p.record(0, 0, -5)
        assertEquals(0, p.count)
        assertFalse(p.dirty)
    }

    @Test
    fun survivesBeingSavedAndLoaded() {
        val p = RegionPings()
        p.record(0, 0, 33)
        p.record(8, 8, 190)
        val back = RegionPings.fromJson(p.toJson())
        assertEquals(33, back.get(0, 0))
        assertEquals(190, back.get(8, 8))
        assertNull(back.get(1, 1))
    }

    @Test
    fun loadingIgnoresGarbage() {
        val json = com.google.gson.JsonParser.parseString("""{"5": 70, "x": 1, "999": 5, "7": "no", "8": -3}""").asJsonObject
        val p = RegionPings.fromJson(json)
        assertEquals(1, p.count)
        assertEquals(70, p.get(5, 0))
    }

    @Test
    fun theHudGetsPingsByColumnAndRow() {
        val p = RegionPings().also { it.record(4, 2, 61) }
        assertTrue(p.toGrid().has("4,2"))
        assertEquals(61, p.toGrid()["4,2"].asInt)
    }
}
