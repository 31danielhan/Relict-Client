package relict.render

import java.util.concurrent.CopyOnWriteArrayList

/** Draws in the 3D world, seen through walls. Coordinates are world coordinates. Colours are 0xAARRGGBB. */
interface OverlayCanvas {
    fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, argb: Int, filled: Boolean)
    fun line(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, argb: Int)
}

/** Draws on the 2D screen, in GUI pixels. Colours are 0xAARRGGBB. */
interface HudCanvas {
    val width: Int
    val height: Int
    fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int)
    fun text(text: String, x: Int, y: Int, argb: Int, scale: Float = 1f)
    fun textWidth(text: String, scale: Float = 1f): Int
}

fun interface OverlayProvider {
    fun draw(canvas: OverlayCanvas)
}

fun interface HudProvider {
    fun draw(canvas: HudCanvas)
}

/**
 * What the modules draw with. A module adds a provider while it is enabled; the version-specific render hook calls
 * every provider each frame. [overlaysSupported] / [hudSupported] say whether this Minecraft version has a hook yet.
 */
object Render {
    val overlays = CopyOnWriteArrayList<OverlayProvider>()
    val huds = CopyOnWriteArrayList<HudProvider>()

    var overlaysSupported = false
    var hudSupported = false
}
