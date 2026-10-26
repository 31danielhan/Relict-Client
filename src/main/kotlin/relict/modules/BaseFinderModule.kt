package relict.modules

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import relict.core.Module
import relict.core.Tier
import relict.donut.AnalysisSettings
import relict.donut.BaseFinderEngine
import relict.donut.DonutScanner
import relict.donut.FinderEntry
import relict.render.DonutHuds
import relict.render.HudProvider
import relict.render.Render

/**
 * Flags chunks that show signs of player activity: stashes, machines, dug shafts. It reads the block counts of the
 * chunks your client has already loaded; it never asks the server for anything extra.
 */
class BaseFinderModule : Module(
    "base_finder", "Base Finder",
    "Marks loaded chunks with signs of player activity (stashes, machines, dug shafts) and lists the nearest.",
    Tier.YELLOW,
) {
    val radius = settings.int("radius", "Scan radius (chunks)", 12, 2..32, "How far around you to look. Only chunks your game has loaded can be scanned.")
    val minScore = settings.int("minScore", "Minimum score", 20, 0..300, "Chunks scoring less than this are not listed.")
    val maxY = settings.int("maxY", "Count blocks below Y", 0, -64..320, "Only blocks below this height count, so surface builds don't flag a chunk.")

    val showHud = settings.bool("showHud", "Show the HUD list", true, "Draws the nearest flagged chunks in the top left of the screen.")

    private val engine = BaseFinderEngine()
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
        )
        val shown = entries.hashCode()
        if (shown != lastShown) {
            lastShown = shown
            emit("changed", list())
        }
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
        const val REFRESH_TICKS = 20
    }
}
