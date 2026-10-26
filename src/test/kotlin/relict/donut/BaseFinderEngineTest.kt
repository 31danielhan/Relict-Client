package relict.donut

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaseFinderEngineTest {
    private fun stash(x: Int, z: Int, hoppers: Int = 20, chests: Int = 10) =
        ChunkSnapshot(x, z, listOf(SectionSnapshot(-64, mapOf("minecraft:hopper" to hoppers, "minecraft:chest" to chests, "minecraft:stone" to 2000))))

    private fun empty(x: Int, z: Int) = ChunkSnapshot(x, z, listOf(SectionSnapshot(-64, mapOf("minecraft:stone" to 4000))))

    private val settings = AnalysisSettings()
    private val noLive = { _: Int, _: Int -> 0 }

    @Test
    fun entriesAreSortedNearestFirstWithDirectionAndDistance() {
        val engine = BaseFinderEngine()
        // The player stands in chunk (0,0) at block 8,8.
        val list = engine.update(listOf(stash(5, 0), stash(0, 1), stash(-2, -2)), noLive, 8.0, 8.0, settings)
        assertEquals(listOf(0 to 1, -2 to -2, 5 to 0), list.map { it.result.chunkX to it.result.chunkZ })
        assertEquals("S", list[0].direction)
        assertEquals(16, list[0].distance)
        assertEquals("NW", list[1].direction)
        assertEquals("E", list[2].direction)
    }

    @Test
    fun liveSignalsRaiseAChunkAboveTheThreshold() {
        val quiet = ChunkSnapshot(0, 0, listOf(SectionSnapshot(-64, mapOf("minecraft:chest" to 3, "minecraft:cobblestone" to 40))))
        val engine = BaseFinderEngine()
        assertTrue(engine.update(listOf(quiet), noLive, 0.0, 0.0, AnalysisSettings(minScore = 30)).isEmpty())
        val live = engine.update(listOf(quiet), { _, _ -> 2 }, 0.0, 0.0, AnalysisSettings(minScore = 30))
        assertEquals(1, live.size)
        assertTrue(live[0].result.evidence.any { "live player signal 2" == it.text })
    }

    @Test
    fun aClearedOutBaseBecomesExcavatedInsteadOfVanishing() {
        val engine = BaseFinderEngine()
        assertEquals(Kind.STASH, engine.update(listOf(stash(3, 3)), noLive, 0.0, 0.0, settings)[0].result.kind)

        val cleared = engine.update(listOf(empty(3, 3)), noLive, 0.0, 0.0, settings)
        assertEquals(1, cleared.size)
        assertEquals(Kind.EXCAVATED, cleared[0].result.kind)
        assertTrue(cleared[0].result.confidence <= 5 + 1)
    }

    @Test
    fun aChunkThatWasNeverABaseIsNotExcavated() {
        val engine = BaseFinderEngine()
        assertTrue(engine.update(listOf(empty(1, 1)), noLive, 0.0, 0.0, settings).isEmpty())
    }

    @Test
    fun resetForgetsHistory() {
        val engine = BaseFinderEngine()
        engine.update(listOf(stash(3, 3)), noLive, 0.0, 0.0, settings)
        engine.reset()
        assertTrue(engine.update(listOf(empty(3, 3)), noLive, 0.0, 0.0, settings).isEmpty())
    }

    @Test
    fun compassDirections() {
        assertEquals("N", Compass.direction(0.0, -10.0))
        assertEquals("E", Compass.direction(10.0, 0.0))
        assertEquals("S", Compass.direction(0.0, 10.0))
        assertEquals("W", Compass.direction(-10.0, 0.0))
        assertEquals("NE", Compass.direction(10.0, -10.0))
        assertEquals("SW", Compass.direction(-10.0, 10.0))
        assertEquals("here", Compass.direction(0.0, 0.0))
    }
}
