package relict.modules

import relict.core.Module
import relict.core.Tier
import relict.donut.AnalysisSettings
import relict.donut.BaseFinderEngine
import relict.donut.DonutScanner
import relict.donut.FinderEntry
import relict.donut.Kind
import relict.render.OverlayCanvas
import relict.render.OverlayProvider
import relict.render.Render
import relict.render.Style

/** Outlines each chunk the Base Finder flags, over the height range where it found something. */
class ChunkEspModule : Module(
    "chunk_esp", "Chunk ESP",
    "Outlines the chunks the Base Finder flags, through walls.",
    Tier.YELLOW,
) {
    val radius = settings.int("radius", "Scan radius (chunks)", 12, 2..32, "How far around you to look.")
    val minScore = settings.int("minScore", "Minimum score", 20, 0..300, "Chunks scoring less than this are not outlined.")
    val maxY = settings.int("maxY", "Count blocks below Y", 0, -64..320, "Only blocks below this height count.")
    val style = settings.enum("style", "Style", Style.OUTLINE)

    @Volatile
    private var entries: List<FinderEntry> = emptyList()
    private val engine = BaseFinderEngine()
    private var ticks = 0

    override val unavailableReason: String? get() = if (Render.overlaysSupported) null else "In-world overlays aren't available on this Minecraft version yet."

    private val overlay = OverlayProvider { canvas -> draw(canvas) }

    override fun onEnable() {
        DonutScanner.acquire(this, radius.value)
        Render.overlays += overlay
    }

    override fun onDisable() {
        DonutScanner.release(this)
        Render.overlays -= overlay
        engine.reset()
        entries = emptyList()
    }

    override fun onTick() {
        DonutScanner.setRadius(this, radius.value)
        DonutScanner.tick(this)
        if (++ticks % REFRESH_TICKS != 0) return
        entries = engine.update(
            DonutScanner.snapshots(), DonutScanner::liveSignals,
            DonutScanner.playerX, DonutScanner.playerZ, AnalysisSettings(maxY.value, minScore.value),
        )
    }

    private fun draw(canvas: OverlayCanvas) {
        for (e in entries) {
            val r = e.result
            val rgb = when (r.kind) {
                Kind.STASH -> 0xFFA726
                Kind.MACHINE -> 0xEF5350
                Kind.DUG_SHAFT -> 0x42A5F5
                Kind.EXCAVATED -> 0x9E9E9E
                Kind.UNKNOWN -> 0xFFEE58
            }
            val x0 = r.chunkX * 16.0
            val z0 = r.chunkZ * 16.0
            val y0 = r.minY.toDouble()
            val y1 = r.maxY + 1.0
            if (style.value != Style.OUTLINE) canvas.box(x0, y0, z0, x0 + 16, y1, z0 + 16, (0x30 shl 24) or rgb, filled = true)
            if (style.value != Style.FILLED) canvas.box(x0, y0, z0, x0 + 16, y1, z0 + 16, (0xFF shl 24) or rgb, filled = false)
        }
    }

    private companion object {
        const val REFRESH_TICKS = 20
    }
}
