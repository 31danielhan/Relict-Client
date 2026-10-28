package relict.modules

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import relict.core.Module
import relict.donut.BaseAnalyzer
import relict.donut.BlockClusters
import relict.donut.BlockRef
import relict.donut.ChunkSnapshot
import relict.donut.Growth
import relict.donut.BlockFinder
import relict.donut.BlockCategories
import relict.donut.Category
import relict.donut.Cluster
import relict.donut.DonutScanner
import relict.donut.Kind
import relict.game.GameHolder
import relict.render.ConfidenceColor
import relict.render.EspLayer
import relict.render.Inspector
import relict.render.OverlayProvider
import relict.render.Render
import relict.render.Style

/**
 * Draws a plain white outline around each stash, machine and farm: the clusters of storage and redstone parts below a height
 * limit, visible through walls. A dot in the middle of each shows its details when you aim at it. (The module is still
 * called base_esp inside, so saved settings keep working.)
 */
class BaseEspModule : Module(
    "base_esp", "Stash/Farm ESP",
    "Outlines stashes, machines and farms in white, through walls. Aim at the dot in the middle for the details.",
) {
    val radius = settings.int("radius", "Radius (chunks)", 8, 1..16, "How far around you to highlight.")
    val maxY = settings.int("maxY", "Highlight below Y", 320, -64..320, "Only blocks below this height are highlighted. At 320 it works above and below Y 0.")
    val minScore = settings.int("minScore", "Minimum cluster score", 20, 0..500, "Smaller clusters are ignored.")
    val style = settings.enum("style", "Style", Style.OUTLINE)

    @Volatile
    var clusters: List<Cluster> = emptyList()
        private set

    private val finder = BlockFinder()
    private var ticks = 0

    /** What was found in each chunk the last time its underground was loaded. */
    private val remembered = HashMap<Pair<Int, Int>, List<BlockRef>>()
    private val wantedIds = HashMap<Pair<Int, Int>, Pair<ChunkSnapshot, Set<String>>>()

    override val unavailableReason: String? get() = if (Render.overlaysSupported) null else "In-world overlays aren't available on this Minecraft version yet."

    private val layer = EspLayer()
    /** Stays registered after the module is switched off until the boxes have faded out. */
    private val overlay: OverlayProvider = OverlayProvider { canvas ->
        val cam = Render.freshCamera()
        val me = GameHolder.game.player()
        layer.draw(canvas, style.value, 1f, cam?.let { EspLayer.Viewer(it.x, it.y, it.z) } ?: me?.let { EspLayer.Viewer(it.x, it.y + 1.62, it.z) })
        if (!enabled && layer.isEmpty) Render.overlays -= overlay
    }

    override fun onEnable() {
        DonutScanner.acquire(this, radius.value)
        if (overlay !in Render.overlays) Render.overlays += overlay
    }

    override fun onDisable() {
        DonutScanner.release(this)
        clusters = emptyList()
        layer.update(emptyList())
        Inspector.publish(this, emptyList())
        finder.clear()
        remembered.clear()
        wantedIds.clear()
    }

    override fun onTick() {
        DonutScanner.setRadius(this, radius.value)
        DonutScanner.tick(this)
        if (++ticks % REFRESH_TICKS != 0) return
        val game = GameHolder.game
        val me = game.player() ?: return
        val pcx = me.blockX shr 4
        val pcz = me.blockZ shr 4

        finder.computeBudget = 3
        val nearby = DonutScanner.snapshots().filter { sq(it.chunkX - pcx) + sq(it.chunkZ - pcz) <= radius.value * radius.value }
        finder.retain(nearby.map { it.chunkX to it.chunkZ }.toSet())
        val blocks = nearby.flatMap { snap ->
            val key = snap.chunkX to snap.chunkZ
            // Which wanted blocks the chunk has only changes when the chunk does, so it is worked out once per snapshot.
            val cached = wantedIds[key]
            val ids = if (cached != null && cached.first === snap) cached.second else snap.sections.flatMapTo(HashSet()) { it.counts.keys }.filter { BlockCategories.of(it) in WANTED }.toSet().also { wantedIds[key] = snap to it }
            val found = if (ids.isEmpty()) emptyList() else finder.find(game, snap, ids, Int.MIN_VALUE / 2, maxY.value - 1)
            // Above Y 0 the server sends no underground: show what was found there when it was loaded, next to what is in view now.
            if (snap.sections.any { it.minY < 0 }) {
                remembered[key] = found.filter { it.pos.y < 0 }
                found
            } else found + remembered[key].orEmpty().filter { it.pos.y < maxY.value }
        }
        clusters = BlockClusters.build(blocks).filter { it.score >= minScore.value && looksMade(it) }
        val targets = ArrayList<EspLayer.Target>()
        val items = ArrayList<Inspector.Item>()
        for (c in clusters) {
            val kind = kindOf(c, nearby)
            val key = "${kind.label}@${((c.minX + c.maxX) / 2) shr 4},${((c.minY + c.maxY) / 2) shr 4},${((c.minZ + c.maxZ) / 2) shr 4}"
            targets += EspLayer.Target(key, c.minX - 0.02, c.minY - 0.02, c.minZ - 0.02, c.maxX + 1.02, c.maxY + 1.02, c.maxZ + 1.02, WHITE, fill = 0x22)
            val cx = (c.minX + c.maxX + 1) / 2.0
            val cy = (c.minY + c.maxY + 1) / 2.0
            val cz = (c.minZ + c.maxZ + 1) / 2.0
            targets += EspLayer.Target("dot:$key", cx - 0.1, cy - 0.1, cz - 0.1, cx + 0.1, cy + 0.1, cz + 0.1, WHITE, fill = 0, dot = true)
            items += Inspector.Item("dot:$key", cx, cy, cz) { distance, direction -> card(c, kind, distance, direction) }
        }
        layer.update(targets)
        Inspector.publish(this, items)
    }

    /** Above ground a few chests and furnaces are a village or a house; a cluster needs a hopper, a machine, redstone or a big pile of storage to count there. */
    private fun looksMade(c: Cluster): Boolean {
        if (c.minY < 0) return true
        val parts = (c.counts[Category.HOPPER] ?: 0) + (c.counts[Category.MACHINE] ?: 0) + (c.counts[Category.REDSTONE] ?: 0)
        return parts >= 3 || (c.counts[Category.CONTAINER] ?: 0) >= SURFACE_STASH_MIN
    }

    /** A cluster is a farm when the chunk it is in has plants growing with machines round them (and no real stash in it). */
    private fun kindOf(c: Cluster, nearby: List<ChunkSnapshot>): Kind {
        if (c.kind == Kind.STASH) return Kind.STASH
        val snap = nearby.firstOrNull { it.chunkX == ((c.minX + c.maxX) / 2) shr 4 && it.chunkZ == ((c.minZ + c.maxZ) / 2) shr 4 } ?: return c.kind
        var growing = 0
        var parts = 0
        for (section in snap.sections) for ((id, n) in section.counts) {
            if (Growth.isGrowth(id)) growing += n
            if (Growth.isFarmMachine(id)) parts += n
        }
        return if (growing >= 12 && parts >= 4) Kind.FARM else c.kind
    }

    private fun card(c: Cluster, kind: Kind, distance: Int, direction: String): JsonObject = JsonObject().apply {
        val percent = BaseAnalyzer.confidence(c.score)
        addProperty("title", kind.label)
        addProperty("percent", percent)
        addProperty("direction", direction)
        addProperty("distance", distance)
        addProperty("minY", c.minY)
        addProperty("maxY", c.maxY)
        addProperty("sections", 0)
        addProperty("totalSections", 0)
        add("evidence", JsonArray().also { arr ->
            c.counts.entries.sortedByDescending { it.value }.forEach { (cat, n) ->
                arr.add(JsonObject().apply { addProperty("points", n); addProperty("text", "${cat.label} (blocks)") })
            }
        })
        addProperty("points", c.score)
        addProperty("level", ConfidenceColor.level(percent))
        addProperty("note", "${c.maxX - c.minX + 1} x ${c.maxY - c.minY + 1} x ${c.maxZ - c.minZ + 1} blocks")
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

    private companion object {
        const val WHITE = 0xFFFFFF
        const val REFRESH_TICKS = 5
        const val SURFACE_STASH_MIN = 12
        val WANTED = setOf(Category.CONTAINER, Category.HOPPER, Category.MACHINE, Category.REDSTONE)
    }
}
