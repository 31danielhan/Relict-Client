package relict.render

import java.util.concurrent.CopyOnWriteArrayList

/** Draws in the 3D world, seen through walls. Coordinates are world coordinates. Colours are 0xAARRGGBB. */
interface OverlayCanvas {
    fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, argb: Int, filled: Boolean)
    fun line(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, argb: Int)

    /**
     * A round dot facing the camera, [diameter] blocks across, seen through walls. Versions that can draw a textured quad
     * make it a smooth anti-aliased circle; the others fall back to a small cube.
     */
    fun dot(x: Double, y: Double, z: Double, diameter: Double, argb: Int) {
        val h = diameter / 2
        box(x - h, y - h, z - h, x + h, y + h, z + h, argb, filled = true)
    }
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
/** Where the view is and which way it looks. In free cam this is the camera, not the player. */
class CameraPose(val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float) {
    val at: Long = System.nanoTime()
}

object Render {
    /** The camera as of the last frame (set by a version's world render hook), or null if it can't be read on this version. */
    @Volatile var camera: CameraPose? = null

    /** The camera if it was updated a moment ago, else null (the game is paused, in a menu, or this version doesn't report it). */
    fun freshCamera(): CameraPose? = camera?.takeIf { System.nanoTime() - it.at < 300_000_000L }

    val overlays = CopyOnWriteArrayList<OverlayProvider>()
    val huds = CopyOnWriteArrayList<HudProvider>()

    var overlaysSupported = false
    var hudSupported = false
}
