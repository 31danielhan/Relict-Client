package relict.modules

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import relict.core.Module
import relict.donut.AnalysisSettings
import relict.donut.BaseFinderEngine
import relict.donut.DonutScanner
import relict.donut.FinderEntry
import relict.game.GameHolder
import relict.render.DonutHuds
import relict.render.HudProvider
import relict.render.Render
import relict.render.WebHud

/**
 * Flags chunks that show signs of player activity: stashes, machines, dug shafts. It reads the block counts of the
 * chunks your client has already loaded; it never asks the server for anything extra.
 */
class BaseFinderModule : Module(
    "base_finder", "Base Finder",
    "Marks loaded chunks with signs of player activity (stashes, machines, dug shafts) and lists the nearest.",
) {
    val radius = settings.int("radius", "Scan radius (chunks)", 12, 2..32, "How far around you to look. Only chunks your game has loaded can be scanned.")
    val minScore = settings.int("minScore", "Minimum score", 20, 0..300, "Chunks scoring less than this are not listed.")
    val maxY = settings.int("topY", "Count blocks below Y", 320, -64..320, "Only blocks below this height count. At 320 everything counts, above Y 0 as well as below it.")

    val showHud = settings.bool("showHud", "Show the HUD list", true, "Draws the nearest flagged chunks in the top left of the screen.")

    private val engine = BaseFinderEngine().also { it.maxNewPerUpdate = 4 }
    private val hud = HudProvider { canvas -> if (showHud.value) DonutHuds.baseFinder(canvas, entries) }
    private var ticks = 0
    private var lastShown = 0

    /** The current list, nearest first. Other modules (the HUD, the overlays) read this. */
    @Volatile
    var entries: List<FinderEntry> = emptyList()
        private set

    override fun onEnable() {
        DonutScanner.acquire(this, radius.value)
        Render.huds += hud
    }

    override fun onDisable() {
        DonutScanner.release(this)
        Render.huds -= hud
        WebHud.set("finder", null)
        engine.reset()
        entries = emptyList()
    }

    override fun onTick() {
        DonutScanner.setRadius(this, radius.value)
        DonutScanner.tick(this)
        if (++ticks % REFRESH_TICKS != 0) return
        entries = engine.update(
            DonutScanner.snapshots(), DonutScanner::liveSignals,
            DonutScanner.playerX, DonutScanner.playerZ,
            AnalysisSettings(maxY.value, minScore.value),
            GameHolder.game.player()?.dimension ?: "minecraft:overworld",
            DonutScanner::redstoneSignals, DonutScanner::farmDrops,
        )
        WebHud.set("finder", if (showHud.value) webState() else null)
        val shown = entries.hashCode()
        if (shown != lastShown) {
            lastShown = shown
            emit("changed", list())
        }
    }

    /** The list for the HUD page. It works out distance and direction itself from the player position, so they stay current between scans. */
    private fun webState() = JsonObject().apply {
        add("entries", JsonArray().also { arr ->
            entries.forEach { e ->
                arr.add(JsonObject().apply {
                    addProperty("id", "${e.result.chunkX},${e.result.chunkZ}")
                    addProperty("kind", e.result.kind.label)
                    addProperty("x", e.centerX)
                    addProperty("z", e.centerZ)
                    addProperty("minY", e.result.minY)
                    addProperty("maxY", e.result.maxY)
                    addProperty("sections", e.result.evidenceSections)
                    addProperty("totalSections", e.result.totalSections)
                    addProperty("confidence", e.result.confidence)
                })
            }
        })
    }

    private fun list(): JsonArray = JsonArray().also { arr -> entries.forEach { arr.add(it.toJson()) } }

    override fun call(method: String, params: JsonObject): JsonElement = when (method) {
        "list" -> list()
        else -> super.call(method, params)
    }

    private fun FinderEntry.toJson() = JsonObject().apply {
        addProperty("kind", result.kind.label)
        addProperty("chunkX", result.chunkX)
        addProperty("chunkZ", result.chunkZ)
        addProperty("x", centerX)
        addProperty("z", centerZ)
        addProperty("direction", direction)
        addProperty("distance", distance)
        addProperty("minY", result.minY)
        addProperty("maxY", result.maxY)
        addProperty("sections", result.evidenceSections)
        addProperty("totalSections", result.totalSections)
        addProperty("confidence", result.confidence)
        addProperty("score", result.score)
        add("evidence", JsonArray().also { arr ->
            result.evidence.forEach { e -> arr.add(JsonObject().apply { addProperty("points", e.points); addProperty("text", e.text) }) }
        })
    }

    private companion object {
        const val REFRESH_TICKS = 5
    }
}
