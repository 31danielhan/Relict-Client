package relict

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.storage.LevelResource
import relict.donut.BlockPos3
import relict.donut.BlockRef
import relict.donut.ChunkSnapshot
import relict.donut.SectionSnapshot
import relict.game.Game
import relict.game.PlayerState
import relict.game.SeenPlayer
import relict.game.ProfileSource
import relict.ui.BrowserScreen
import relict.ui.ScreenCompat

/** [Game] for Minecraft 26.x, which uses Mojang's names. */
object GameImpl : Game {
    private val client: Minecraft get() = Minecraft.getInstance()

    override fun player(): PlayerState? {
        val level = client.level ?: return null
        val player = client.player ?: return null
        return PlayerState(player.x, player.y, player.z, player.yRot, level.dimension().identifier().toString())
    }

    override fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean =
        client.level?.chunkSource?.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null

    override fun loadedChunkCount(): Int = client.level?.chunkSource?.loadedChunksCount ?: 0

    private val blockIds = HashMap<Block, String>()

    private fun idOf(block: Block): String = blockIds.getOrPut(block) { BuiltInRegistries.BLOCK.getKey(block).toString() }

    override fun snapshotChunk(chunkX: Int, chunkZ: Int): ChunkSnapshot? {
        val level = client.level ?: return null
        val chunk = level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: return null
        val bottom = level.minSectionY
        val sections = ArrayList<SectionSnapshot>()
        chunk.sections.forEachIndexed { i, section ->
            if (section.hasOnlyAir()) return@forEachIndexed
            val counts = HashMap<String, Int>()
            section.states.count { state, n -> counts.merge(idOf(state.block), n, Int::plus) }
            sections += SectionSnapshot((bottom + i) * 16, counts)
        }
        return ChunkSnapshot(chunkX, chunkZ, sections)
    }

    override fun findBlocks(chunkX: Int, chunkZ: Int, minY: Int, maxY: Int, ids: Set<String>): List<BlockRef> {
        val level = client.level ?: return emptyList()
        val chunk = level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: return emptyList()
        val bottom = level.minSectionY
        val found = ArrayList<BlockRef>()
        chunk.sections.forEachIndexed { i, section ->
            val baseY = (bottom + i) * 16
            if (section.hasOnlyAir() || baseY + 15 < minY || baseY > maxY || !section.maybeHas { idOf(it.block) in ids }) return@forEachIndexed
            for (y in 0..15) for (z in 0..15) for (x in 0..15) {
                val id = idOf(section.getBlockState(x, y, z).block)
                if (id in ids && baseY + y in minY..maxY) found += BlockRef(id, BlockPos3(chunkX * 16 + x, baseY + y, chunkZ * 16 + z))
            }
        }
        return found
    }

    override fun otherPlayers(): List<SeenPlayer> {
        val level = client.level ?: return emptyList()
        return level.players().filter { it !== client.player }.map { SeenPlayer(it.name.string, it.x, it.y, it.z) }
    }

    override fun currentProfile(): ProfileSource? {
        client.currentServer?.ip?.let { return ProfileSource.Server(it) }
        client.singleplayerServer?.let { return ProfileSource.SingleplayerWorld(it.getWorldPath(LevelResource.ROOT).normalize().fileName.toString()) }
        return null
    }

    override fun showOverlayMessage(text: String) {
        ScreenCompat.showOverlayMessage(Component.literal(text))
    }

    override fun showToast(title: String, message: String) {
        SystemToast.add(ScreenCompat.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal(title), Component.literal(message))
    }

    override fun runOnGameThread(task: Runnable) {
        client.execute(task)
    }

    override fun isGuiOpen(): Boolean = ScreenCompat.current() is BrowserScreen

    override fun openGui(url: String) {
        ScreenCompat.set(BrowserScreen(url, ScreenCompat.current()))
    }
}
