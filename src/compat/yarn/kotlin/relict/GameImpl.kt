package relict

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.ItemEntity
import net.minecraft.item.Item
import net.minecraft.state.property.Properties
import net.minecraft.client.MinecraftClient
import net.minecraft.client.toast.SystemToast
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket
import net.minecraft.util.Util
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.WorldSavePath
import net.minecraft.world.chunk.ChunkStatus
import relict.render.WebHud
import relict.donut.BlockCategories
import relict.donut.BlockPos3
import relict.donut.BlockRef
import relict.donut.ChunkSnapshot
import relict.donut.SectionSnapshot
import relict.game.DroppedItem
import relict.game.Game
import relict.game.PlayerState
import relict.game.SeenPlayer
import relict.game.ProfileSource
import relict.ui.BrowserScreen

/** [Game] for Minecraft versions that use Yarn names (1.21.11 and older). */
object GameImpl : Game {
    private val client: MinecraftClient get() = MinecraftClient.getInstance()

    override fun player(): PlayerState? {
        val world = client.world ?: return null
        val player = client.player ?: return null
        return PlayerState(player.x, player.y, player.z, player.yaw, world.registryKey.value.toString(), player.pitch)
    }

    override fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean =
        client.world?.chunkManager?.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null

    override fun loadedChunkCount(): Int = client.world?.chunkManager?.loadedChunkCount ?: 0

    private val blockIds = HashMap<Block, String>()

    private fun idOf(block: Block): String = blockIds.getOrPut(block) { Registries.BLOCK.getId(block).toString() }

    private val switchedOn = HashMap<BlockState, Boolean>()

    /** A redstone part that is powered, lit, extended or triggered right now. Looked at once per kind of state, not per block. */
    private fun isSwitchedOn(state: BlockState): Boolean = switchedOn.getOrPut(state) {
        BlockCategories.isRedstonePart(idOf(state.block)) && (
            (state.contains(Properties.POWERED) && state.get(Properties.POWERED)) ||
                (state.contains(Properties.LIT) && state.get(Properties.LIT)) ||
                (state.contains(Properties.EXTENDED) && state.get(Properties.EXTENDED)) ||
                (state.contains(Properties.TRIGGERED) && state.get(Properties.TRIGGERED)) ||
                (state.contains(Properties.POWER) && state.get(Properties.POWER) > 0)
            )
    }

    private val itemIds = HashMap<Item, String>()

    override fun droppedItems(): List<DroppedItem> {
        val world = client.world ?: return emptyList()
        val found = ArrayList<DroppedItem>()
        for (entity in world.entities) {
            if (entity !is ItemEntity) continue
            val stack = entity.stack
            found += DroppedItem(itemIds.getOrPut(stack.item) { Registries.ITEM.getId(stack.item).toString() }, stack.count, entity.x, entity.z)
        }
        return found
    }

    override fun snapshotChunk(chunkX: Int, chunkZ: Int): ChunkSnapshot? {
        val world = client.world ?: return null
        val chunk = world.chunkManager.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: return null
        val bottom = world.bottomSectionCoord
        val sections = ArrayList<SectionSnapshot>()
        chunk.sectionArray.forEachIndexed { i, section ->
            if (section.isEmpty) return@forEachIndexed
            val counts = HashMap<String, Int>()
            var active = 0
            section.blockStateContainer.count { state, n ->
                counts.merge(idOf(state.block), n, Int::plus)
                if (isSwitchedOn(state)) active += n
            }
            sections += SectionSnapshot((bottom + i) * 16, counts, active)
        }
        return ChunkSnapshot(chunkX, chunkZ, sections)
    }

    override fun findBlocks(chunkX: Int, chunkZ: Int, minY: Int, maxY: Int, ids: Set<String>): List<BlockRef> {
        val world = client.world ?: return emptyList()
        val chunk = world.chunkManager.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: return emptyList()
        val bottom = world.bottomSectionCoord
        val found = ArrayList<BlockRef>()
        chunk.sectionArray.forEachIndexed { i, section ->
            val baseY = (bottom + i) * 16
            if (section.isEmpty || baseY + 15 < minY || baseY > maxY || !section.hasAny { idOf(it.block) in ids }) return@forEachIndexed
            for (y in 0..15) for (z in 0..15) for (x in 0..15) {
                val id = idOf(section.getBlockState(x, y, z).block)
                if (id in ids && baseY + y in minY..maxY) found += BlockRef(id, BlockPos3(chunkX * 16 + x, baseY + y, chunkZ * 16 + z))
            }
        }
        return found
    }

    override fun otherPlayers(): List<SeenPlayer> {
        val world = client.world ?: return emptyList()
        return world.players.filter { it !== client.player }.map { SeenPlayer(it.name.string, it.x, it.y, it.z) }
    }

    override fun pingMs(): Int? {
        val handler = client.networkHandler ?: return null
        val id = client.player?.uuid ?: return null
        handler.getPlayerListEntry(id)?.latency?.takeIf { it > 0 }?.let { return it }
        // Some servers hide the tab list, and with it the latency. The game's own ping chart asks the server for an echo and times
        // it; do the same, and read the newest value it recorded (the answer lands a moment after the question).
        handler.sendPacket(QueryPingC2SPacket(Util.getMeasuringTimeMs()))
        val log = client.inGameHud.debugHud.pingLog
        return if (log.length > 0) log.get(log.length - 1).toInt().takeIf { it > 0 } else null
    }

    override fun currentProfile(): ProfileSource? {
        client.currentServerEntry?.address?.let { return ProfileSource.Server(it) }
        client.server?.let { return ProfileSource.SingleplayerWorld(it.getSavePath(WorldSavePath.ROOT).normalize().fileName.toString()) }
        return null
    }

    override fun showOverlayMessage(text: String) {
        client.inGameHud.setOverlayMessage(Text.literal(text), false)
    }

    override fun showNotification(title: String, message: String, tone: String) {
        if (WebHud.toast(title, message, tone)) return
        showToast(title, message)
    }

    override fun showToast(title: String, message: String) {
        if (WebHud.toast(title, message)) return
        SystemToast.add(client.toastManager, SystemToast.Type.PERIODIC_NOTIFICATION, Text.literal(title), Text.literal(message))
    }

    override fun runOnGameThread(task: Runnable) {
        client.execute(task)
    }

    override fun isGuiOpen(): Boolean = client.currentScreen is BrowserScreen

    override fun openGui(url: String) {
        client.setScreen(BrowserScreen(url, client.currentScreen))
    }
}
