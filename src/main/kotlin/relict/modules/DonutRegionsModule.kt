package relict.modules

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import relict.core.Module
import relict.core.ProfileStore
import relict.donut.DonutRegions
import relict.donut.RegionPings
import relict.game.GameHolder
import relict.render.DonutHuds
import relict.render.HudProvider
import relict.render.Render
import relict.render.WebHud

/**
 * DonutSMP's world is split into regions, each run by its own server behind one Velocity proxy, so your connection
 * changes from area to area. This shows the region map with your ping in every area you have stood in, and the area
 * you are in right now picked out.
 */
class DonutRegionsModule : Module(
    "donut_regions", "Donut Regions",
    "Shows the server's region map with your ping in each area, and which area you are in.",
) {
    enum class Numbers { PING, REGION }

    val numbers = settings.enum("numbers", "Numbers in the map", Numbers.PING, "Show your ping in each area, or the server's region numbers.")

    private val hud = HudProvider { canvas -> DonutHuds.regions(canvas, GameHolder.game.player(), pings, livePing) }

    private var pings = RegionPings()
    private var store: ProfileStore? = null
    private var livePing: Int? = null
    private var ticks = 0

    override fun onEnable() {
        Render.huds += hud
        WebHud.set("regions", webState())
    }

    override fun onDisable() {
        Render.huds -= hud
        WebHud.set("regions", null)
        save()
    }

    override fun onProfileOpen(store: ProfileStore) {
        this.store = store
        pings = store.readBytes(FILE)?.let { runCatching { RegionPings.fromJson(com.google.gson.JsonParser.parseString(String(it)).asJsonObject) }.getOrNull() } ?: RegionPings()
        WebHud.set("regions", webState())
    }

    override fun onProfileClose(store: ProfileStore) {
        save()
        this.store = null
        pings = RegionPings()
        livePing = null
    }

    override fun onTick() {
        if (++ticks % SAMPLE_TICKS != 0) return
        val game = GameHolder.game
        val me = game.player() ?: return
        val cell = DonutRegions.cellAt(me.x, me.z)
        val ping = game.pingMs()
        livePing = ping
        if (cell != null && ping != null) pings.record(cell.column, cell.row, ping)
        WebHud.set("regions", webState())
        if (ticks % SAVE_TICKS == 0) save()
    }

    /** For the in-game test, where singleplayer has no ping: fills in some areas and a live reading, as if you had travelled. */
    fun fakePingsForTest(live: Int) {
        for (row in 0 until DonutRegions.GRID) for (column in 0 until DonutRegions.GRID) {
            if ((row * 3 + column * 5) % 4 != 0) pings.record(column, row, 25 + (row * 37 + column * 53) % 210)
        }
        livePing = live
        DonutRegions.cellAt(GameHolder.game.player()?.x ?: 0.0, GameHolder.game.player()?.z ?: 0.0)?.let { pings.record(it.column, it.row, live) }
        WebHud.set("regions", webState())
    }

    private fun save() {
        val s = store ?: return
        if (!pings.dirty) return
        try {
            s.writeBytes(FILE, pings.toJson().toString().toByteArray())
            pings.markSaved()
        } catch (e: Exception) {
            // Losing a few ping readings is not worth bothering the player about.
        }
    }

    /** What the HUD page needs: the map, the averages, and your ping right now. Where you are comes from the position it is sent every tick. */
    private fun webState(): JsonObject = JsonObject().apply {
        add("grid", info()["grid"])
        addProperty("cellSize", DonutRegions.CELL_SIZE)
        addProperty("half", DonutRegions.CELL_SIZE * DonutRegions.GRID / 2)
        add("pings", pings.toGrid())
        livePing?.let { addProperty("ping", it) }
        addProperty("labels", numbers.value.name.lowercase())
    }

    override fun call(method: String, params: JsonObject): JsonElement = when (method) {
        "info" -> info()
        else -> super.call(method, params)
    }

    private companion object {
        const val FILE = "region-pings.json"
        const val SAMPLE_TICKS = 10
        const val SAVE_TICKS = 600
    }

    /** The whole map plus the player's cell, for the HUD and the GUI. */
    fun info(): JsonObject = JsonObject().apply {
        val me = GameHolder.game.player()
        val cell = me?.let { DonutRegions.cellAt(it.x, it.z) }
        add("grid", JsonArray().also { rows ->
            for (r in 0 until DonutRegions.GRID) rows.add(JsonArray().also { row ->
                for (c in 0 until DonutRegions.GRID) row.add(JsonObject().apply {
                    val cellAt = DonutRegions.cell(c, r)
                    addProperty("number", cellAt.number)
                    cellAt.group?.let { addProperty("group", it) }
                })
            })
        })
        add("pings", pings.toGrid())
        livePing?.let { addProperty("ping", it) }
        me?.let { addProperty("x", it.blockX); addProperty("z", it.blockZ) }
        cell?.let {
            add("current", JsonObject().apply {
                addProperty("number", it.number)
                addProperty("label", it.label)
                addProperty("column", it.column)
                addProperty("row", it.row)
                it.group?.let { g -> addProperty("group", g) }
                addProperty("rangeX", DonutRegions.rangeLabel(it.minX, it.maxX))
                addProperty("rangeZ", DonutRegions.rangeLabel(it.minZ, it.maxZ))
            })
        }
    }
}
