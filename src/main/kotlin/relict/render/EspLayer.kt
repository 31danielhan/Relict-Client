package relict.render

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.sin

/**
 * Highlight boxes that ease in, out and between sizes instead of popping. A module gives [update] what should be
 * highlighted whenever it has a new answer (about once a second); [draw] runs every frame and moves what is on
 * screen toward it, so a stash fades in as it is found, grows as more of it is found, and fades out when it is gone.
 * Speeds are per second, not per frame, so it looks the same at any frame rate.
 */
class EspLayer {
    class Target(
        val key: String,
        val minX: Double, val minY: Double, val minZ: Double,
        val maxX: Double, val maxY: Double, val maxZ: Double,
        val rgb: Int,
        /** Opacity of the fill, 0..255. */
        val fill: Int,
        /**
         * Draw the outline as flat slabs whose width grows with distance, instead of lines. The game's line shader fades far
         * lines into fog; slabs stay visible from high above (used for the chunk outlines on the world floor).
         */
        val frame: Boolean = false,
        /** A small marker cube instead of a box, sized by distance so it stays a dot on screen. Always drawn, whatever the style. */
        val dot: Boolean = false,
    )

    /** Where the viewer is, for things that depend on distance. */
    class Viewer(val x: Double, val y: Double, val z: Double)

    private class Shown(t: Target) {
        var minX = t.minX; var minY = t.minY; var minZ = t.minZ
        var maxX = t.maxX; var maxY = t.maxY; var maxZ = t.maxZ
        var rgb = t.rgb
        var fill = t.fill
        var frame = t.frame
        var dot = t.dot
        var alpha = 0f
        var goal = 1f

        /** How much this dot is being aimed at, 0..1, eased. */
        var focus = 0f

        // The colour eases to a new one instead of snapping, so a chance changing slides the outline from one colour to the next.
        var cr = ((t.rgb shr 16) and 0xFF).toFloat()
        var cg = ((t.rgb shr 8) and 0xFF).toFloat()
        var cb = (t.rgb and 0xFF).toFloat()
    }

    private val shown = LinkedHashMap<String, Shown>()
    private var last = 0L

    private val targetsByKey = HashMap<String, Target>()

    /** What should be highlighted now. Anything that is no longer in the list starts fading out. */
    fun update(targets: List<Target>) {
        val seen = HashSet<String>(targets.size * 2)
        for (t in targets) {
            seen += t.key
            targetsByKey[t.key] = t
            val s = shown[t.key]
            if (s == null) shown[t.key] = Shown(t) else s.goal = 1f // a new box starts where it is and fades in; an old one glides (see draw)
        }
        for ((k, s) in shown) if (k !in seen) s.goal = 0f
    }

    fun clear() {
        shown.clear()
        targetsByKey.clear()
    }

    val isEmpty: Boolean get() = shown.isEmpty()

    /** [strength] scales every box's opacity (0..1), for things like dimming near the player. */
    fun draw(canvas: OverlayCanvas, style: Style, strength: Float = 1f, viewer: Viewer? = null, nowNanos: Long = System.nanoTime()) {
        if (shown.isEmpty()) return
        val dt = if (last == 0L) 0.0 else ((nowNanos - last) / 1e9).coerceIn(0.0, 0.1)
        last = nowNanos
        val move = 1.0 - exp(-dt * MOVE_SPEED)
        val colour = (1.0 - exp(-dt * COLOUR_SPEED)).toFloat()
        val aimed = Inspector.aimedKey
        val breathe = 1f + PULSE * sin((nowNanos / 1e9) * 2 * PI / PULSE_SECONDS).toFloat()

        val it = shown.entries.iterator()
        while (it.hasNext()) {
            val (key, s) = it.next()
            val t = targetsByKey[key]
            if (t != null && s.goal > 0f) {
                s.fill = t.fill
                s.cr += (((t.rgb shr 16) and 0xFF) - s.cr) * colour
                s.cg += (((t.rgb shr 8) and 0xFF) - s.cg) * colour
                s.cb += ((t.rgb and 0xFF) - s.cb) * colour
                s.rgb = (s.cr.toInt() shl 16) or (s.cg.toInt() shl 8) or s.cb.toInt()
                s.frame = t.frame
                s.dot = t.dot
                s.minX += (t.minX - s.minX) * move; s.minY += (t.minY - s.minY) * move; s.minZ += (t.minZ - s.minZ) * move
                s.maxX += (t.maxX - s.maxX) * move; s.maxY += (t.maxY - s.maxY) * move; s.maxZ += (t.maxZ - s.maxZ) * move
            }
            val speed = if (s.goal > s.alpha) FADE_IN else FADE_OUT
            s.alpha += ((s.goal - s.alpha) * (1.0 - exp(-dt * speed))).toFloat()
            if (s.goal == 0f && s.alpha < 0.02f) {
                it.remove()
                targetsByKey.remove(key)
                continue
            }
            val a = (s.alpha * strength).coerceIn(0f, 1f)
            if (s.dot) {
                // Aimed at, a dot swells and brightens, easing in and out.
                s.focus += (((if (key == aimed) 1f else 0f) - s.focus) * (1.0 - exp(-dt * FOCUS_SPEED))).toFloat()
                // Not dimmed with the outlines (see [strength]): you aim at the dot, so it stays clear even right beside the base.
                val dotAlpha = (255 * s.alpha).toInt().coerceIn(0, 255)
                if (dotAlpha > 0) drawDot(canvas, s, (dotAlpha shl 24) or s.rgb, viewer)
                continue
            }
            if (style != Style.OUTLINE) {
                val fillAlpha = (s.fill * a * breathe).toInt().coerceIn(0, 255)
                if (fillAlpha > 0) canvas.box(s.minX, s.minY, s.minZ, s.maxX, s.maxY, s.maxZ, (fillAlpha shl 24) or s.rgb, filled = true)
            }
            if (style != Style.FILLED) {
                val lineAlpha = (255 * a).toInt().coerceIn(0, 255)
                if (lineAlpha > 0) {
                    val argb = (lineAlpha shl 24) or s.rgb
                    if (s.frame) drawFrame(canvas, s, argb, viewer) else canvas.box(s.minX, s.minY, s.minZ, s.maxX, s.maxY, s.maxZ, argb, filled = false)
                }
            }
        }
    }

    /** A round dot at the box's centre that stays about the same size on screen, and swells when it is aimed at. */
    private fun drawDot(canvas: OverlayCanvas, s: Shown, argb: Int, viewer: Viewer?) {
        val cx = (s.minX + s.maxX) / 2; val cy = (s.minY + s.maxY) / 2; val cz = (s.minZ + s.maxZ) / 2
        val distance = if (viewer == null) 0.0 else sqrt((cx - viewer.x).let { it * it } + (cy - viewer.y).let { it * it } + (cz - viewer.z).let { it * it })
        val diameter = (distance * DOT_PER_BLOCK).coerceIn(DOT_MIN, DOT_MAX) * (1.0 + DOT_SWELL * s.focus)
        canvas.dot(cx, cy, cz, diameter, argb)
    }

    /** Four slabs round the box's edge, as wide as the viewer's distance calls for so they keep about the same width on screen. */
    private fun drawFrame(canvas: OverlayCanvas, s: Shown, argb: Int, viewer: Viewer?) {
        val cx = (s.minX + s.maxX) / 2; val cy = (s.minY + s.maxY) / 2; val cz = (s.minZ + s.maxZ) / 2
        val distance = if (viewer == null) 0.0 else sqrt((cx - viewer.x).let { it * it } + (cy - viewer.y).let { it * it } + (cz - viewer.z).let { it * it })
        val t = (distance * FRAME_PER_BLOCK).coerceIn(FRAME_MIN, FRAME_MAX)
        val h = t * 0.6
        val y1 = s.minY + h
        canvas.box(s.minX, s.minY, s.minZ, s.maxX, y1, s.minZ + t, argb, filled = true)
        canvas.box(s.minX, s.minY, s.maxZ - t, s.maxX, y1, s.maxZ, argb, filled = true)
        canvas.box(s.minX, s.minY, s.minZ + t, s.minX + t, y1, s.maxZ - t, argb, filled = true)
        canvas.box(s.maxX - t, s.minY, s.minZ + t, s.maxX, y1, s.maxZ - t, argb, filled = true)
    }

    private companion object {
        /** Slab width per block of distance, and its limits, in blocks. */
        const val FRAME_PER_BLOCK = 0.016
        const val FRAME_MIN = 0.12
        const val DOT_PER_BLOCK = 0.016
        const val DOT_MIN = 0.3
        const val DOT_MAX = 3.0
        const val DOT_SWELL = 0.9
        const val COLOUR_SPEED = 5.0
        const val FOCUS_SPEED = 14.0
        const val FRAME_MAX = 3.0

        /** How quickly a box glides to a new size, per second. */
        const val MOVE_SPEED = 9.0
        const val FADE_IN = 7.0
        const val FADE_OUT = 5.0

        /** The fill breathes by this fraction, once every [PULSE_SECONDS]. */
        const val PULSE = 0.12f
        const val PULSE_SECONDS = 2.6
    }
}
