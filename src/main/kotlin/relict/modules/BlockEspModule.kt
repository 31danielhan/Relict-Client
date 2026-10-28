package relict.modules

import relict.core.Module
import relict.donut.BlockFinder
import relict.donut.BlockColors
import relict.donut.BlockRef
import relict.donut.ChunkSnapshot
import relict.donut.DonutScanner
import relict.game.GameHolder
import relict.render.EspLayer
import relict.render.OverlayProvider
import relict.render.Render
import relict.render.Style

/** Highlights the blocks you choose, through walls. */
class BlockEspModule : Module(
    "block_esp", "Block ESP",
    "Outlines the blocks you pick (chests, spawners, shulker boxes...) in a colour for each kind, through walls. Every colour of a dyed block counts as one.",
) {
    val blocks = settings.string(
        "blocks", "Blocks", "chest, ender_chest, spawner, shulker_box", 400,
        "Block names separated by commas. \"minecraft:\" is not needed. Dyed blocks (shulker_box, bed, banner, wool...) match every colour.",
    )
    val radius = settings.int("radius", "Radius (chunks)", 4, 1..16, "How far around you to look.")
    val maxCount = settings.int("maxCount", "Maximum highlights", 400, 10..3000, "Stops the screen filling with boxes.")
    val style = settings.enum("style", "Style", Style.BOTH)

    @Volatile
    private var found: List<BlockRef> = emptyList()
    private val finder = BlockFinder()
    private var ticks = 0

    /** What was seen below Y 0 in each chunk the last time its underground was loaded (the server sends none while you are above Y 0). */
    private val remembered = HashMap<Pair<Int, Int>, List<BlockRef>>()
    private val matchedIds = HashMap<Pair<Int, Int>, Triple<ChunkSnapshot, String, Set<String>>>()

    override val unavailableReason: String? get() = if (Render.overlaysSupported) null else "In-world overlays aren't available on this Minecraft version yet."

    private val layer = EspLayer()
    /** Stays registered after the module is switched off until the boxes have faded out. */
    private val overlay: OverlayProvider = OverlayProvider { canvas ->
        layer.draw(canvas, style.value)
        if (!enabled && layer.isEmpty) Render.overlays -= overlay
    }

    override fun onEnable() {
        DonutScanner.acquire(this, radius.value)
        if (overlay !in Render.overlays) Render.overlays += overlay
    }

    override fun onDisable() {
        DonutScanner.release(this)
        found = emptyList()
        layer.update(emptyList())
        finder.clear()
        remembered.clear()
        matchedIds.clear()
    }

    override fun onTick() {
        DonutScanner.setRadius(this, radius.value)
        DonutScanner.tick(this)
        if (++ticks % REFRESH_TICKS != 0) return
        val game = GameHolder.game
        val me = game.player() ?: return
        val matcher = BlockColors.Matcher(blocks.value)
        if (matcher.isEmpty) { found = emptyList(); layer.update(emptyList()); return }
        val pcx = me.blockX shr 4
        val pcz = me.blockZ shr 4

        finder.computeBudget = 3
        val nearby = DonutScanner.snapshots().filter { sq(it.chunkX - pcx) + sq(it.chunkZ - pcz) <= radius.value * radius.value }
        finder.retain(nearby.map { it.chunkX to it.chunkZ }.toSet())
        found = nearby.flatMap { snap ->
            val key = snap.chunkX to snap.chunkZ
            // The blocks this chunk really has that the player asked for (a family expands to its colours).
            val cached = matchedIds[key]
            val ids = if (cached != null && cached.first === snap && cached.second == blocks.value) cached.third else snap.sections.flatMapTo(HashSet()) { it.counts.keys }.filter(matcher::matches).toSet().also { matchedIds[key] = Triple(snap, blocks.value, it) }
            // Whatever is loaded is searched, above Y 0 or below. The server sends no underground while you are above Y 0, so what was
            // seen down there the last time it was loaded is kept and shown in its place.
            val here = if (ids.isEmpty()) emptyList() else finder.find(game, snap, ids, Int.MIN_VALUE / 2, Int.MAX_VALUE / 2)
            if (snap.sections.any { it.minY < 0 }) {
                remembered[key] = here.filter { it.pos.y < 0 }
                here
            } else here + remembered[key].orEmpty().filter { matcher.matches(it.id) }
        }
            .sortedBy { it.pos.distanceSq(me.x, me.y, me.z) }
            .take(maxCount.value)
        layer.update(found.map { b ->
            val p = b.pos
            // One colour for the whole family, whatever the dye.
            EspLayer.Target("${p.x},${p.y},${p.z}", p.x - 0.01, p.y - 0.01, p.z - 0.01, p.x + 1.01, p.y + 1.01, p.z + 1.01, BlockColors.rgb(b.id), fill = 0x40)
        })
    }

    private fun sq(v: Int) = v.toLong() * v

    companion object {
        private const val REFRESH_TICKS = 5
    }
}
