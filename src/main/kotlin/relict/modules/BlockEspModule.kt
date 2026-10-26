package relict.modules

import relict.core.Module
import relict.core.Tier
import relict.donut.BlockFinder
import relict.donut.BlockRef
import relict.donut.DonutScanner
import relict.game.GameHolder
import relict.render.OverlayCanvas
import relict.render.OverlayProvider
import relict.render.Render
import relict.render.Style

/** Highlights the blocks you choose, through walls. */
class BlockEspModule : Module(
    "block_esp", "Block ESP",
    "Highlights the blocks you pick (for example chests or spawners), through walls.",
    Tier.YELLOW,
) {
    val blocks = settings.string(
        "blocks", "Blocks", "minecraft:chest, minecraft:ender_chest, minecraft:spawner", 400,
        "Block ids separated by commas. \"minecraft:\" is added if you leave it out.",
    )
    val radius = settings.int("radius", "Radius (chunks)", 4, 1..16, "How far around you to look.")
    val maxCount = settings.int("maxCount", "Maximum highlights", 400, 10..3000, "Stops the screen filling with boxes.")
    val style = settings.enum("style", "Style", Style.BOTH)

    @Volatile
    private var found: List<BlockRef> = emptyList()
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
        found = emptyList()
        finder.clear()
    }

    override fun onTick() {
        DonutScanner.setRadius(this, radius.value)
        DonutScanner.tick(this)
        if (++ticks % REFRESH_TICKS != 0) return
        val game = GameHolder.game
        val me = game.player() ?: return
        val ids = parseIds(blocks.value)
        if (ids.isEmpty()) { found = emptyList(); return }
        val pcx = me.blockX shr 4
        val pcz = me.blockZ shr 4

        val nearby = DonutScanner.snapshots().filter { sq(it.chunkX - pcx) + sq(it.chunkZ - pcz) <= radius.value * radius.value }
        finder.retain(nearby.map { it.chunkX to it.chunkZ }.toSet())
        found = nearby.flatMap { finder.find(game, it, ids, Int.MIN_VALUE / 2, Int.MAX_VALUE / 2) }
            .sortedBy { it.pos.distanceSq(me.x, me.y, me.z) }
            .take(maxCount.value)
    }

    private fun sq(v: Int) = v.toLong() * v

    private fun draw(canvas: OverlayCanvas) {
        for (b in found) {
            val p = b.pos
            if (style.value != Style.OUTLINE) canvas.box(p.x - 0.01, p.y - 0.01, p.z - 0.01, p.x + 1.01, p.y + 1.01, p.z + 1.01, 0x4042A5F5, filled = true)
            if (style.value != Style.FILLED) canvas.box(p.x - 0.01, p.y - 0.01, p.z - 0.01, p.x + 1.01, p.y + 1.01, p.z + 1.01, 0xFF42A5F5.toInt(), filled = false)
        }
    }

    companion object {
        private const val REFRESH_TICKS = 20

        /** "chest, minecraft:barrel" -> {"minecraft:chest", "minecraft:barrel"}. */
        fun parseIds(text: String): Set<String> = text.split(',', ' ', ';', '\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .map { if (':' in it) it else "minecraft:$it" }
            .toSet()
    }
}
