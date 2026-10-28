package relict.donut

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DonutRegionsTest {
    // The three positions below are readouts from the server's own region HUD.
    @Test
    fun matchesTheKnownReadouts() {
        assertEquals(107, DonutRegions.cellAt(-136115.0, 177412.0)!!.number)
        assertEquals(22, DonutRegions.cellAt(114712.0, 91680.0)!!.number)
        assertEquals(5, DonutRegions.cellAt(659.0, -973.0)!!.number)
        assertEquals(107, DonutRegions.cellAt(-136079.0, 177428.0)!!.number)
        assertEquals(22, DonutRegions.cellAt(117606.0, 99668.0)!!.number)
    }

    @Test
    fun knownRegionsHaveTheirGroup() {
        assertEquals("EU West", DonutRegions.cellAt(-136115.0, 177412.0)!!.group)
        assertEquals("EU Central", DonutRegions.cellAt(114712.0, 91680.0)!!.group)
        assertEquals("NA East", DonutRegions.cellAt(659.0, -973.0)!!.group)
        assertEquals("Oceania", DonutRegions.cellAt(-200_000.0, -200_000.0)!!.group) // region 82
    }

    @Test
    fun cellBoundsAndLabelsMatchTheMap() {
        val c = DonutRegions.cellAt(114712.0, 91680.0)!!
        assertEquals("G7", c.label)
        assertEquals("75k..125k", DonutRegions.rangeLabel(c.minX, c.maxX))
        assertEquals("75k..125k", DonutRegions.rangeLabel(c.minZ, c.maxZ))
        val b = DonutRegions.cellAt(-136115.0, 177412.0)!!
        assertEquals("B9", b.label)
        assertEquals("-175k..-125k", DonutRegions.rangeLabel(b.minX, b.maxX))
        assertEquals("175k..225k", DonutRegions.rangeLabel(b.minZ, b.maxZ))
    }

    @Test
    fun edgesAndOutsideOfTheMap() {
        assertEquals("A1", DonutRegions.cellAt(-225_000.0, -225_000.0)!!.label)
        assertEquals("I9", DonutRegions.cellAt(224_999.9, 224_999.9)!!.label)
        assertNull(DonutRegions.cellAt(225_000.0, 0.0))
        assertNull(DonutRegions.cellAt(0.0, -225_000.1))
        assertEquals("E5", DonutRegions.cellAt(0.0, 0.0)!!.label)
        assertEquals("D4", DonutRegions.cellAt(-25_001.0, -25_001.0)!!.label)
        assertEquals("E5", DonutRegions.cellAt(-24_999.0, -24_999.0)!!.label)
    }

    @Test
    fun everyRegionNumberAppearsExactlyOnce() {
        val numbers = (0 until 9).flatMap { r -> (0 until 9).map { c -> DonutRegions.cell(c, r).number } }
        assertEquals(81, numbers.toSet().size)
        assertEquals(113, numbers.max()) // the server numbers regions up to 113
    }

    @Test
    fun everyCellKnowsItsGroup() {
        for (row in 0 until DonutRegions.GRID) for (column in 0 until DonutRegions.GRID) {
            assertNotNull(DonutRegions.cell(column, row).group, "cell ${DonutRegions.cell(column, row).label} has no group")
        }
    }

    @Test
    fun groupsMatchTheServersOwnMap() {
        fun group(number: Int) = DonutRegions.groupNames[number]
        assertEquals("EU Central", group(22))
        assertEquals("EU West", group(107))
        assertEquals("NA East", group(5))
        assertEquals("Oceania", group(82))
        assertEquals("NA West", group(100))
        assertEquals("Asia", group(86))
        assertEquals(6, DonutRegions.groupNames.values.toSet().size)
    }
}
