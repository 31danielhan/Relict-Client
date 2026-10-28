package relict.modules

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import relict.core.Module
import relict.donut.Compass
import relict.donut.DonutScanner
import relict.game.GameHolder

/**
 * Lists the other players your client can see, and the chunks where something was recently built or dug (a "live
 * signal"). Both come from data the server has already sent you.
 */
class LivePlayersModule : Module(
    "live_players", "Live Player Search",
    "Lists players the game has loaded near you, and chunks where blocks changed lately (a player may be there). Turn on the Notifier to be told about new ones.",
) {
    val radius = settings.int("radius", "Scan radius (chunks)", 12, 2..32, "How far around you to watch for changes.")

    data class Entry(val type: String, val label: String, val direction: String, val distance: Int, val y: Int?, val detail: String)

    @Volatile
    var entries: List<Entry> = emptyList()
        private set

    private var ticks = 0

    override fun onEnable() = DonutScanner.acquire(this, radius.value)

    override fun onDisable() {
        DonutScanner.release(this)
        entries = emptyList()
    }

    override fun onTick() {
        DonutScanner.setRadius(this, radius.value)
        DonutScanner.tick(this)
        if (++ticks % REFRESH_TICKS != 0) return
        val game = GameHolder.game
        val me = game.player() ?: return

        val found = ArrayList<Entry>()
        for (p in game.otherPlayers()) {
            val dx = p.x - me.x
            val dz = p.z - me.z
            found += Entry("player", p.name, Compass.direction(dx, dz), Compass.distance(dx, dz), p.y.toInt(), "player")
        }
        for ((cx, cz, signals) in DonutScanner.activeChunks()) {
            val dx = cx * 16 + 8 - me.x
            val dz = cz * 16 + 8 - me.z
            found += Entry("activity", "Possible player ($cx, $cz)", Compass.direction(dx, dz), Compass.distance(dx, dz), null, "$signals sign(s) of building or machines")
        }
        entries = found.sortedBy { it.distance }
    }

    override fun call(method: String, params: JsonObject): JsonElement = when (method) {
        "list" -> JsonArray().also { arr ->
            entries.forEach { e ->
                arr.add(JsonObject().apply {
                    addProperty("type", e.type)
                    addProperty("label", e.label)
                    addProperty("direction", e.direction)
                    addProperty("distance", e.distance)
                    e.y?.let { addProperty("y", it) }
                    addProperty("detail", e.detail)
                })
            }
        }
        else -> super.call(method, params)
    }

    private companion object {
        const val REFRESH_TICKS = 20
    }
}
