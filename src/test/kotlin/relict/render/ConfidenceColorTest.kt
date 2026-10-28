package relict.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfidenceColorTest {
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    @Test
    fun unlikelyIsOrangeRed() {
        val c = ConfidenceColor.rgb(5)
        assertTrue(r(c) > 240 && g(c) < 120 && b(c) < 80, Integer.toHexString(c))
    }

    @Test
    fun kindOfLikelyIsYellow() {
        val c = ConfidenceColor.rgb(48)
        assertTrue(r(c) > 230 && g(c) > 190 && b(c) < 100, Integer.toHexString(c))
    }

    @Test
    fun likelyIsGreen() {
        val c = ConfidenceColor.rgb(86)
        assertTrue(g(c) > r(c) && g(c) > b(c), Integer.toHexString(c))
    }

    @Test
    fun ninetyNineIsBlue() {
        assertEquals(0x42A5F5, ConfidenceColor.rgb(99))
        assertEquals(0x42A5F5, ConfidenceColor.rgb(100))
    }

    @Test
    fun theScaleNeverGoesOutOfRange() {
        assertEquals(ConfidenceColor.rgb(0), ConfidenceColor.rgb(-20))
        assertEquals(0xFF5A36, ConfidenceColor.rgb(0))
    }

    @Test
    fun levelsHaveWords() {
        assertEquals("LOW", ConfidenceColor.level(20))
        assertEquals("MEDIUM", ConfidenceColor.level(52))
        assertEquals("HIGH", ConfidenceColor.level(99))
    }
}
