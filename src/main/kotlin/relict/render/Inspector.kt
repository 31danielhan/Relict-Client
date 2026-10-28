package relict.render

import com.google.gson.JsonObject
import relict.donut.Compass
import relict.game.PlayerState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The card for what the crosshair is on. ESP modules publish the points they mark (a chunk's dot, a stash's centre) with a
 * way to describe each; every tick this works out which one the player is aiming at, if any, and the HUD shows its card
 * next to the crosshair. "Aiming at" is loose on purpose: a lightly pointing crosshair counts, and a little more slack
 * is given with distance, because a dot far away is only a few pixels across.
 */
object Inspector {
    class Item(
        /** Names the thing, so the dot drawn for it can swell while it is being aimed at (see [aimedKey]). */
        val key: String,
        val x: Double, val y: Double, val z: Double,
        /** Builds the card, given the distance in blocks and compass direction from the player, which change as they move. */
        val card: (distance: Int, direction: String) -> JsonObject,
    )

    private val sources = LinkedHashMap<Any, List<Item>>()
    private var current: Item? = null

    /** The key of what the crosshair is on right now, or null. */
    @Volatile var aimedKey: String? = null
        private set

    @Synchronized
    fun publish(owner: Any, items: List<Item>) {
        if (items.isEmpty()) sources.remove(owner) else sources[owner] = items
    }

    /** Where the player is looking from: the camera when the game reports it (so a free camera works), else the player's eyes. */
    private class View(val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float)

    @Synchronized
    fun tick(player: PlayerState?, camera: CameraPose? = null) {
        val items = sources.values.flatten()
        if (player == null || items.isEmpty()) return show(null)
        val view = if (camera != null) View(camera.x, camera.y, camera.z, camera.yaw, camera.pitch)
        else View(player.x, player.y + EYE_HEIGHT, player.z, player.yaw, player.pitch)
        val yaw = Math.toRadians(view.yaw.toDouble())
        val pitch = Math.toRadians(view.pitch.toDouble())
        // Minecraft: yaw 0 looks toward +Z (south), yaw 90 toward -X (west); pitch is positive looking down.
        val lx = -sin(yaw) * cos(pitch)
        val ly = -sin(pitch)
        val lz = cos(yaw) * cos(pitch)
        val ex = view.x
        val ey = view.y
        val ez = view.z

        var best: Item? = null
        var bestRatio = Double.MAX_VALUE
        for (item in items) {
            val vx = item.x - ex; val vy = item.y - ey; val vz = item.z - ez
            val along = vx * lx + vy * ly + vz * lz
            if (along <= 0) continue
            val distanceSq = vx * vx + vy * vy + vz * vz
            val lateral = sqrt(maxOf(0.0, distanceSq - along * along))
            val allowed = (BASE_RADIUS + sqrt(distanceSq) * PER_BLOCK) * (if (item === current) STICKY else 1.0)
            val ratio = lateral / allowed
            if (ratio <= 1.0 && ratio < bestRatio) { best = item; bestRatio = ratio }
        }
        if (best !== current) current = best
        show(best?.let { item ->
            val dx = item.x - view.x
            val dz = item.z - view.z
            item.card(Compass.distance(dx, dz), Compass.direction(dx, dz))
        })
    }

    private fun show(card: JsonObject?) {
        aimedKey = if (card == null) null else current?.key
        if (card == null) current = null
        WebHud.set("inspect", card)
    }

    /** How far off the crosshair may be, in blocks, plus a little more for every block of distance. */
    private const val BASE_RADIUS = 1.1
    private const val PER_BLOCK = 0.02

    /** Once a card is up, it stays until the crosshair is clearly off it. */
    private const val STICKY = 1.5
    private const val EYE_HEIGHT = 1.62
}
