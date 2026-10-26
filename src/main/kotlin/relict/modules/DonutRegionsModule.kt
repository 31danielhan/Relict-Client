package relict.modules

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import relict.core.Module
import relict.donut.DonutRegions
import relict.game.GameHolder
import relict.render.DonutHuds
import relict.render.HudProvider
import relict.render.Render

/** Shows which of DonutSMP's regions you are standing in, on the server's own 9 x 9 map. */
class DonutRegionsModule : Module(
    "donut_regions", "Donut Regions",
    "Shows the server's region map and which region you are in.",
) {
    private val hud = HudProvider { canvas -> DonutHuds.regions(canvas, GameHolder.game.player()) }

    override fun onEnable() {
        Render.huds += hud
    }

    override fun onDisable() {
        Render.huds -= hud
    }

    override fun call(method: String, params: JsonObject): JsonElement = when (method) {
        "info" -> info()
        else -> super.call(method, params)
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
