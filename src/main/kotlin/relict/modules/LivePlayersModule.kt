package relict.modules

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import relict.core.Module
import relict.core.Tier
import relict.donut.Compass
import relict.donut.DonutScanner
import relict.game.GameHolder

/**
 * Lists the other players your client can see, and the chunks where something was recently built or dug (a "live
 * signal"). Both come from data the server has already sent you.
 */
class LivePlayersModule : Module(
    "live_players", "Live Player Search",
    "Lists nearby players and chunks with fresh building or digging activity, and can alert you to new ones.",
    Tier.YELLOW,
) {
    enum class Alerts { OFF, TOAST }

    val radius = settings.int("radius", "Scan radius (chunks)", 12, 2..32, "How far around you to watch for activity.")
    val alerts = settings.enum("alerts", "Alerts", Alerts.TOAST, "Show a pop-up when a new player or activity appears.")
    val cooldown = settings.int("cooldown", "Alert cooldown (seconds)", 5, 1..120, "Wait this long before alerting about the same thing again.")

    data class Entry(val type: String, val label: String, val direction: String, val distance: Int, val y: Int?, val detail: String)

    @Volatile
    var entries: List<Entry> = emptyList()
        private set

    private var ticks = 0
    private val lastAlert = HashMap<String, Int>()

    override fun onEnable() = DonutScanner.acquire(this, radius.value)

    override fun onDisable() {
        DonutScanner.release(this)
        entries = emptyList()
        lastAlert.clear()
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
            found += Entry("activity", "Activity ($cx, $cz)", Compass.direction(dx, dz), Compass.distance(dx, dz), null, "$signals recent change(s)")
        }
        entries = found.sortedBy { it.distance }

        if (alerts.value == Alerts.TOAST) {
            for (e in entries) {
                val last = lastAlert[e.label]
                if (last == null || ticks - last >= cooldown.value * 20) {
                    lastAlert[e.label] = ticks
                    game.showToast("Live ${e.type}", "${e.label} ${e.direction} ${e.distance}m")
                }
            }
        }
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
