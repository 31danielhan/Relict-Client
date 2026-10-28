package relict.modules

import relict.core.Module
import relict.donut.AnalysisSettings
import relict.donut.ChunkTones
import relict.donut.ChunkResult
import relict.donut.BaseFinderEngine
import relict.donut.DonutScanner
import relict.donut.FinderEntry
import relict.game.GameHolder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import relict.render.ConfidenceColor
import relict.render.EspLayer
import relict.render.Inspector
import relict.render.OverlayProvider
import relict.render.Render
import relict.render.Style

/**
 * Outlines each chunk the Base Finder flags, drawn flat on the floor of the world (Y -64) so it can be seen from anywhere
 * and fades as you get close to Y -30. Colours say what is there: see [ChunkTones].
 */
class ChunkEspModule : Module(
    "chunk_esp", "Chunk ESP",
    "Outlines suspicious chunks on the world floor: purple for players, blue for bases, orange and yellow for lesser signs.",
) {
    val radius = settings.int("radius", "Scan radius (chunks)", 12, 2..32, "How far around you to look.")
    val minScore = settings.int("minScore", "Minimum score", 20, 0..300, "Chunks scoring less than this are not outlined.")
    val maxY = settings.int("topY", "Count blocks below Y", 320, -64..320, "Only blocks below this height count. At 320 everything counts, above Y 0 as well as below it.")
    val style = settings.enum("style", "Style", Style.BOTH)

    @Volatile
    private var entries: List<FinderEntry> = emptyList()
    private val engine = BaseFinderEngine().also { it.maxNewPerUpdate = 4 }
    private var ticks = 0

    override val unavailableReason: String? get() = if (Render.overlaysSupported) null else "In-world overlays aren't available on this Minecraft version yet."

    private val layer = EspLayer()
    /** Stays registered after the module is switched off until the boxes have faded out. */
    private val overlay: OverlayProvider = OverlayProvider { canvas ->
        // The view's height and place come from the camera when it is reported, so a free camera fades and sizes things right too.
        val cam = Render.freshCamera()
        val me = GameHolder.game.player()
        val y = cam?.y ?: me?.y ?: 64.0
        val viewer = cam?.let { EspLayer.Viewer(it.x, it.y, it.z) } ?: me?.let { EspLayer.Viewer(it.x, it.y + 1.62, it.z) }
        layer.draw(canvas, style.value, ChunkTones.strength(y), viewer)
        if (!enabled && layer.isEmpty) Render.overlays -= overlay
    }

    override fun onEnable() {
        DonutScanner.acquire(this, radius.value)
        if (overlay !in Render.overlays) Render.overlays += overlay
    }

    override fun onDisable() {
        DonutScanner.release(this)
        engine.reset()
        layer.update(emptyList())
        Inspector.publish(this, emptyList())
        entries = emptyList()
    }

    override fun onTick() {
        DonutScanner.setRadius(this, radius.value)
        DonutScanner.tick(this)
        if (++ticks % REFRESH_TICKS != 0) return
        entries = engine.update(
            DonutScanner.snapshots(), DonutScanner::liveSignals,
            DonutScanner.playerX, DonutScanner.playerZ, AnalysisSettings(maxY.value, minScore.value),
            GameHolder.game.player()?.dimension ?: "minecraft:overworld",
            DonutScanner::redstoneSignals, DonutScanner::farmDrops,
        )
        layer.update(targets())
    }

    private fun targets(): List<EspLayer.Target> {
        val game = GameHolder.game
        val floor = ChunkTones.floorY(game.player()?.dimension ?: "minecraft:overworld")
        val byKey = LinkedHashMap<String, EspLayer.Target>()
        val items = ArrayList<Inspector.Item>()

        fun frame(cx: Int, cz: Int, rgb: Int) {
            val x0 = cx * 16.0
            val z0 = cz * 16.0
            byKey["$cx,$cz"] = EspLayer.Target("$cx,$cz", x0, floor, z0, x0 + 16, floor + 0.05, z0 + 16, rgb, fill = 0x48, frame = true)
        }

        // The outline's colour is the chance, as in the Base Finder list: orange-red unlikely, yellow kind of likely, green likely, blue at 99%.
        // Purple (a player may be here) takes over, since that matters more than what the chunk looks like.
        for (e in entries) {
            val r = e.result
            val live = DonutScanner.liveSignals(r.chunkX, r.chunkZ) > 0
            val rgb = if (live) ChunkTones.PURPLE else ConfidenceColor.rgb(r.confidence)
            frame(r.chunkX, r.chunkZ, rgb)
            // A dot in the middle of the chunk, on the floor with the outline; aim at it for the details.
            val dotY = floor + DOT_LIFT
            val cx = r.chunkX * 16.0 + 8
            val cz = r.chunkZ * 16.0 + 8
            byKey["dot:${r.chunkX},${r.chunkZ}"] = EspLayer.Target("dot:${r.chunkX},${r.chunkZ}", cx - 0.1, dotY - 0.1, cz - 0.1, cx + 0.1, dotY + 0.1, cz + 0.1, rgb, fill = 0, dot = true)
            items += Inspector.Item("dot:${r.chunkX},${r.chunkZ}", cx, dotY, cz) { distance, direction -> card(r, distance, direction) }
        }
        // Chunks where something changed lately, and where other players are, are outlined purple even when they are not bases.
        for ((cx, cz, _) in DonutScanner.activeChunks()) if ("$cx,$cz" !in byKey) frame(cx, cz, ChunkTones.PURPLE)
        for (p in game.otherPlayers()) {
            val cx = Math.floorDiv(Math.floor(p.x).toInt(), 16)
            val cz = Math.floorDiv(Math.floor(p.z).toInt(), 16)
            if ("$cx,$cz" !in byKey) frame(cx, cz, ChunkTones.PURPLE)
        }
        Inspector.publish(this, items)
        return byKey.values.toList()
    }

    /** What the aim card says about a chunk. */
    private fun card(r: ChunkResult, distance: Int, direction: String): JsonObject = JsonObject().apply {
        addProperty("title", r.kind.label)
        addProperty("percent", r.confidence)
        addProperty("direction", direction)
        addProperty("distance", distance)
        addProperty("minY", r.minY)
        addProperty("maxY", r.maxY)
        addProperty("sections", r.evidenceSections)
        addProperty("totalSections", r.totalSections)
        add("evidence", JsonArray().also { arr ->
            r.evidence.forEach { e -> arr.add(JsonObject().apply { addProperty("points", e.points); addProperty("text", e.text) }) }
        })
        addProperty("points", r.score)
        addProperty("level", ConfidenceColor.level(r.confidence))
        addProperty("note", if (r.fromMemory) "remembered from when you were last below Y 0" else "scanned ${r.totalSections} sections")
    }

    private companion object {
        /** The dot sits this far above the floor outline, level with it for all purposes. */
        const val DOT_LIFT = 0.2
        const val REFRESH_TICKS = 5
    }
}
