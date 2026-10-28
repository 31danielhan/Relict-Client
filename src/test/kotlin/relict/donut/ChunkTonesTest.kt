package relict.donut

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunkTonesTest {
    @Test
    fun theOutlineSitsOnTheFloorOfEachDimension() {
        assertEquals(-64.0, ChunkTones.floorY("minecraft:overworld"))
        assertEquals(0.0, ChunkTones.floorY("minecraft:the_nether"))
    }

    @Test
    fun outlinesAreFullFromHighUpAndFadeTowardsYMinus30() {
        assertEquals(1f, ChunkTones.strength(64.0))
        assertEquals(1f, ChunkTones.strength(10.0))
        val mid = ChunkTones.strength(-10.0)
        assertTrue(mid in 0.4f..0.7f, "halfway, was $mid")
        assertEquals(0.15f, ChunkTones.strength(-30.0))
        assertEquals(0.15f, ChunkTones.strength(-60.0))
        assertTrue(ChunkTones.strength(0.0) > ChunkTones.strength(-20.0))
    }
}
