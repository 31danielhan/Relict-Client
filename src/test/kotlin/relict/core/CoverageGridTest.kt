package relict.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverageGridTest {
    @Test
    fun markCountsEachChunkOnce() {
        val g = CoverageGrid()
        assertTrue(g.mark(3, 4))
        assertFalse(g.mark(3, 4))
        assertEquals(1, g.count)
        assertTrue(g.isSearched(3, 4))
        assertFalse(g.isSearched(4, 3))
    }

    @Test
    fun negativeAndRegionBoundaryCoordinatesAreDistinct() {
        val g = CoverageGrid()
        for ((x, z) in listOf(-1 to -1, 0 to 0, 31 to 31, 32 to 32, -32 to -33, -33 to -32, (Int.MAX_VALUE shr 4) to (Int.MIN_VALUE shr 4))) {
            assertTrue(g.mark(x, z), "($x,$z) should be new")
        }
        assertEquals(7, g.count)
        assertTrue(g.isSearched(-1, -1))
        assertFalse(g.isSearched(-1, 0))
        assertFalse(g.isSearched(0, -1))
    }

    @Test
    fun discMarksOnlyWithinRadius() {
        val g = CoverageGrid()
        val added = g.markDisc(100, -100, 3)
        assertEquals(g.count, added)
        assertTrue(g.isSearched(100, -100))
        assertTrue(g.isSearched(103, -100))
        assertFalse(g.isSearched(103, -97)) // corner, outside the circle
        assertEquals(0, g.markDisc(100, -100, 3)) // nothing new the second time
    }

    @Test
    fun discOnlyCountsLoadedChunks() {
        val g = CoverageGrid()
        g.markDisc(0, 0, 5) { x, _ -> x <= 0 }
        assertTrue(g.isSearched(-5, 0))
        assertFalse(g.isSearched(1, 0))
    }

    @Test
    fun serialisationRoundTrips() {
        val g = CoverageGrid()
        g.markDisc(-50, 70, 6)
        g.markDisc(1000, 1000, 2)
        val copy = CoverageGrid.fromBytes(g.toBytes())
        assertEquals(g.count, copy.count)
        for (dz in -8..8) for (dx in -8..8) assertEquals(g.isSearched(-50 + dx, 70 + dz), copy.isSearched(-50 + dx, 70 + dz))
        assertTrue(copy.isSearched(1000, 1000))
    }

    @Test
    fun corruptFilesAreRejected() {
        assertFailsWith<IllegalArgumentException> { CoverageGrid.fromBytes(byteArrayOf(1, 2, 3)) }
        assertFailsWith<IllegalArgumentException> { CoverageGrid.fromBytes(ByteArray(64)) }
        val truncated = CoverageGrid().also { it.mark(1, 1) }.toBytes().copyOf(30)
        assertFailsWith<IllegalArgumentException> { CoverageGrid.fromBytes(truncated) }
    }

    @Test
    fun rasterAtOneChunkPerCell() {
        val g = CoverageGrid()
        g.mark(10, 20)
        g.mark(12, 21)
        val r = g.raster(9, 19, 5, 4, 1)
        assertEquals(20, r.size)
        // (10,20) -> column 1, row 1 ; (12,21) -> column 3, row 2
        assertEquals(255.toByte(), r[1 * 5 + 1])
        assertEquals(255.toByte(), r[2 * 5 + 3])
        assertEquals(2, r.count { it != 0.toByte() })
    }

    @Test
    fun rasterAggregatesWhenZoomedOut() {
        val g = CoverageGrid()
        for (x in 0 until 4) for (z in 0 until 2) g.mark(x, z) // half of a 4x4 cell
        val r = g.raster(0, 0, 2, 2, 4)
        assertEquals(127, r[0].toInt() and 0xFF)
        assertEquals(0, r[1].toInt())
        g.mark(-1, 5) // a single chunk still shows up, however small
        assertTrue(g.raster(-4, 4, 1, 1, 4)[0].toInt() and 0xFF >= 1)
    }

    @Test
    fun rasterIgnoresChunksOutsideTheWindowAcrossRegions() {
        val g = CoverageGrid()
        g.mark(-100, -100)
        g.mark(500, 500)
        g.mark(31, 31)
        g.mark(32, 32)
        val r = g.raster(30, 30, 4, 4, 1)
        assertEquals(2, r.count { it != 0.toByte() })
    }

    @Test
    fun clearEmptiesTheGrid() {
        val g = CoverageGrid()
        g.markDisc(0, 0, 4)
        g.clear()
        assertTrue(g.isEmpty)
        assertFalse(g.isSearched(0, 0))
    }
}
