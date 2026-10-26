package relict.render

import relict.donut.AnalysisSettings
import relict.donut.DonutRegions
import relict.donut.BaseFinderEngine
import relict.donut.ChunkSnapshot
import relict.donut.SectionSnapshot
import relict.game.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Records what a HUD draws, so layouts can be checked without a game. */
private class RecordingCanvas(override val width: Int = 427, override val height: Int = 240) : HudCanvas {
    data class Fill(val x: Int, val y: Int, val w: Int, val h: Int, val argb: Int)
    data class Text(val text: String, val x: Int, val y: Int, val argb: Int, val scale: Float)

    val fills = ArrayList<Fill>()
    val texts = ArrayList<Text>()

    override fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int) { fills += Fill(x, y, w, h, argb) }
    override fun text(text: String, x: Int, y: Int, argb: Int, scale: Float) { texts += Text(text, x, y, argb, scale) }
    override fun textWidth(text: String, scale: Float): Int = (text.length * 6 * scale).toInt()
}

class DonutHudsTest {
    private fun stash(x: Int, z: Int) =
        ChunkSnapshot(x, z, listOf(SectionSnapshot(-64, mapOf("minecraft:hopper" to 20, "minecraft:chest" to 10))))

    @Test
    fun regionHudShowsTheCurrentRegionAndEveryCell() {
        val c = RecordingCanvas()
        DonutHuds.regions(c, PlayerState(114712.0, 70.0, 91680.0, 0f, "minecraft:overworld"))
        val labels = c.texts.map { it.text }
        assertTrue("22" in labels)
        val numbers = (0 until 9).flatMap { r -> (0 until 9).map { col -> DonutRegions.cell(col, r).number.toString() } }
        assertTrue(numbers.all { it in labels }, "every region's number is drawn")
        assertTrue("X: 114712  Z: 91680" in labels)
        assertTrue("EU Central" in labels)
        assertTrue(c.fills.all { it.x >= 0 && it.x + it.w <= c.width && it.y >= 0 && it.y + it.h <= c.height }, "everything stays on screen")
    }

    @Test
    fun regionHudWorksWithoutAPlayer() {
        val c = RecordingCanvas()
        DonutHuds.regions(c, null)
        assertTrue(c.texts.any { it.text == "DONUT REGIONS" })
        assertTrue(c.texts.none { it.text.startsWith("X:") })
    }

    @Test
    fun baseFinderHudListsTheNearestAndCountsTheRest() {
        val entries = BaseFinderEngine().update((0..7).map { stash(it, 0) }, { _, _ -> 0 }, 0.0, 0.0, AnalysisSettings())
        val c = RecordingCanvas()
        DonutHuds.baseFinder(c, entries)
        val labels = c.texts.map { it.text }
        assertTrue("8 nearby" in labels)
        assertEquals(5, labels.count { it == "Stash" })
        assertTrue("+3 more nearby" in labels)
        assertTrue(labels.any { it.contains("sections") })
    }

    @Test
    fun baseFinderHudSaysSoWhenNothingIsFlagged() {
        val c = RecordingCanvas()
        DonutHuds.baseFinder(c, emptyList())
        assertTrue(c.texts.any { it.text == "Nothing flagged yet" })
        assertTrue(c.texts.any { it.text == "0 nearby" })
    }
}
