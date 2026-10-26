package relict.modules

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import relict.core.Module
import relict.core.Tier
import relict.donut.BlockClusters
import relict.donut.BlockFinder
import relict.donut.BlockCategories
import relict.donut.Category
import relict.donut.Cluster
import relict.donut.DonutScanner
import relict.donut.Kind
import relict.game.GameHolder
import relict.render.OverlayCanvas
import relict.render.OverlayProvider
import relict.render.Render
import relict.render.Style

/** Draws a box around each cluster of storage and redstone below the height limit, visible through walls. */
class BaseEspModule : Module(
    "base_esp", "Base ESP",
    "Highlights clusters of storage and redstone below a height limit, through walls.",
    Tier.YELLOW,
) {
    val radius = settings.int("radius", "Radius (chunks)", 8, 1..16, "How far around you to highlight.")
    val maxY = settings.int("maxY", "Highlight below Y", 0, -64..320, "Only blocks below this height are highlighted.")
    val minScore = settings.int("minScore", "Minimum cluster score", 20, 0..500, "Smaller clusters are ignored.")
    val style = settings.enum("style", "Style", Style.BOTH)

    @Volatile
    var clusters: List<Cluster> = emptyList()
        private set

    private val finder = BlockFinder()
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
        clusters = emptyList()
        finder.clear()
    }

    override fun onTick() {
        DonutScanner.setRadius(this, radius.value)
        DonutScanner.tick(this)
        if (++ticks % REFRESH_TICKS != 0) return
        val game = GameHolder.game
        val me = game.player() ?: return
        val pcx = me.blockX shr 4
        val pcz = me.blockZ shr 4

        val nearby = DonutScanner.snapshots().filter { sq(it.chunkX - pcx) + sq(it.chunkZ - pcz) <= radius.value * radius.value }
        finder.retain(nearby.map { it.chunkX to it.chunkZ }.toSet())
        val blocks = nearby.flatMap { snap ->
            val ids = snap.sections.flatMap { it.counts.keys }.filter { BlockCategories.of(it) in WANTED }.toSet()
            if (ids.isEmpty()) emptyList() else finder.find(game, snap, ids, Int.MIN_VALUE / 2, maxY.value - 1)
        }
        clusters = BlockClusters.build(blocks).filter { it.score >= minScore.value }
    }

    private fun sq(v: Int) = v.toLong() * v

    override fun call(method: String, params: JsonObject): JsonElement = when (method) {
        "list" -> JsonArray().also { arr ->
            clusters.forEach { c ->
                arr.add(JsonObject().apply {
                    addProperty("kind", c.kind.label)
                    addProperty("score", c.score)
                    addProperty("summary", c.summary())
                    addProperty("minX", c.minX); addProperty("minY", c.minY); addProperty("minZ", c.minZ)
                    addProperty("maxX", c.maxX); addProperty("maxY", c.maxY); addProperty("maxZ", c.maxZ)
                })
            }
        }
        else -> super.call(method, params)
    }

    private fun draw(canvas: OverlayCanvas) {
        val me = GameHolder.game.player() ?: return
        for (c in clusters) {
            val rgb = when (c.kind) {
                Kind.STASH -> 0xFFA726
                Kind.MACHINE -> 0xEF5350
                else -> 0xFFEE58
            }
            val x0 = c.minX - 0.02; val y0 = c.minY - 0.02; val z0 = c.minZ - 0.02
            val x1 = c.maxX + 1.02; val y1 = c.maxY + 1.02; val z1 = c.maxZ + 1.02
            if (style.value != Style.OUTLINE) canvas.box(x0, y0, z0, x1, y1, z1, (0x50 shl 24) or rgb, filled = true)
            if (style.value != Style.FILLED) canvas.box(x0, y0, z0, x1, y1, z1, (0xFF shl 24) or rgb, filled = false)
        }
    }

    private companion object {
        const val REFRESH_TICKS = 20
        val WANTED = setOf(Category.CONTAINER, Category.HOPPER, Category.MACHINE, Category.REDSTONE)
    }
}
