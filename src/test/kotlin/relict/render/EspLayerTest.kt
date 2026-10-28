package relict.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class Recorder : OverlayCanvas {
    class Box(val minX: Double, val maxX: Double, val argb: Int, val filled: Boolean)

    val boxes = ArrayList<Box>()
    override fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, argb: Int, filled: Boolean) {
        boxes += Box(minX, maxX, argb, filled)
    }
    override fun line(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, argb: Int) = Unit
    fun outlineAlpha() = boxes.filter { !it.filled }.map { it.argb ushr 24 }
}

class EspLayerTest {
    private val sec = 1_000_000_000L

    private fun target(key: String, x: Double = 0.0, width: Double = 4.0) =
        EspLayer.Target(key, x, 0.0, 0.0, x + width, 4.0, 4.0, 0xFFA726, fill = 0x50)

    /** Draws [seconds] of frames at 60 per second and returns what the last frame drew. */
    private fun run(layer: EspLayer, from: Long, seconds: Double): Recorder {
        var last = Recorder()
        var t = from
        repeat((seconds * 60).toInt()) {
            t += sec / 60
            last = Recorder()
            layer.draw(last, Style.BOTH, 1f, null, t)
        }
        return last
    }

    @Test
    fun aNewBoxFadesInInsteadOfAppearing() {
        val layer = EspLayer()
        layer.update(listOf(target("a")))
        val first = Recorder().also { layer.draw(it, Style.OUTLINE, 1f, null, sec) }
        assertTrue(first.boxes.isEmpty(), "starts invisible")
        val soon = run(layer, sec, 0.1).outlineAlpha().single()
        val later = run(layer, sec * 2, 1.0).outlineAlpha().single()
        assertTrue(soon in 1..254, "partway after a moment, was $soon")
        assertTrue(later >= 250, "fully there after a second, was $later")
    }

    @Test
    fun aBoxThatIsGoneFadesOutAndThenIsForgotten() {
        val layer = EspLayer()
        layer.update(listOf(target("a")))
        run(layer, 0, 1.5)
        layer.update(emptyList())
        val fading = run(layer, 2 * sec, 0.15).outlineAlpha().single()
        assertTrue(fading in 1..254, "fading, was $fading")
        run(layer, 3 * sec, 2.0)
        assertTrue(layer.isEmpty, "removed once invisible")
    }

    @Test
    fun aBoxThatChangesSizeGlidesToTheNewOne() {
        val layer = EspLayer()
        layer.update(listOf(target("a", width = 4.0)))
        run(layer, 0, 1.5)
        layer.update(listOf(target("a", width = 14.0)))
        val partway = run(layer, 2 * sec, 0.1).boxes.first { !it.filled }
        assertTrue(partway.maxX > 4.0 && partway.maxX < 14.0, "between the sizes, was ${partway.maxX}")
        val done = run(layer, 3 * sec, 2.0).boxes.first { !it.filled }
        assertTrue(done.maxX > 13.9, "arrives, was ${done.maxX}")
    }

    @Test
    fun theStyleDecidesWhichPartsAreDrawn() {
        val layer = EspLayer()
        layer.update(listOf(target("a")))
        run(layer, 0, 1.0)
        val outline = Recorder().also { layer.draw(it, Style.OUTLINE, 1f, null, 2 * sec) }
        val filled = Recorder().also { layer.draw(it, Style.FILLED, 1f, null, 3 * sec) }
        assertTrue(outline.boxes.all { !it.filled })
        assertTrue(filled.boxes.all { it.filled })
    }

    @Test
    fun aLongPauseBetweenFramesDoesNotJump() {
        val layer = EspLayer()
        layer.update(listOf(target("a")))
        layer.draw(Recorder(), Style.BOTH, 1f, null, sec)
        val after = Recorder().also { layer.draw(it, Style.OUTLINE, 1f, null, 60 * sec) } // a minute later (game was paused)
        assertTrue(after.outlineAlpha().single() < 255)
    }

    @Test
    fun strengthDimsTheWholeLayer() {
        val layer = EspLayer()
        layer.update(listOf(target("a")))
        run(layer, 0, 1.5)
        val full = Recorder().also { layer.draw(it, Style.OUTLINE, 1f, null, 3 * sec) }.outlineAlpha().single()
        val dim = Recorder().also { layer.draw(it, Style.OUTLINE, 0.25f, null, 4 * sec) }.outlineAlpha().single()
        assertTrue(full >= 250)
        assertTrue(dim in 55..70, "a quarter of full, was $dim")
    }

    @Test
    fun aFrameIsFourSlabsThatGetWiderWithDistance() {
        val layer = EspLayer()
        layer.update(listOf(EspLayer.Target("f", 0.0, -64.0, 0.0, 16.0, -63.95, 16.0, 0xAB47BC, fill = 0x30, frame = true)))
        run(layer, 0, 1.5)
        fun slabWidth(distance: Double): Double {
            val r = Recorder()
            layer.draw(r, Style.OUTLINE, 1f, EspLayer.Viewer(8.0, -64.0 + distance, 8.0), 10 * sec)
            assertEquals(4, r.boxes.size, "one slab per side")
            assertTrue(r.boxes.all { it.filled }, "slabs, not lines")
            return r.boxes.minOf { it.maxX - it.minX } // the west and east slabs are exactly one slab wide
        }
        assertTrue(slabWidth(200.0) > slabWidth(5.0), "wider when far away")
    }
}
