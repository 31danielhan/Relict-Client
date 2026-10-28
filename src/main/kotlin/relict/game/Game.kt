package relict.game

import relict.donut.BlockRef
import relict.donut.ChunkSnapshot
import kotlin.math.floor

/** Where the local player is. */
data class PlayerState(val x: Double, val y: Double, val z: Double, val yaw: Float, val dimension: String, val pitch: Float = 0f) {
    val blockX: Int get() = floor(x).toInt()
    val blockY: Int get() = floor(y).toInt()
    val blockZ: Int get() = floor(z).toInt()
}

/** Another player the client currently has loaded (so within the server's tracking range). */
data class SeenPlayer(val name: String, val x: Double, val y: Double, val z: Double)

/** What the player is in, so per-server data goes in the right folder. */
sealed interface ProfileSource {
    data class Server(val address: String) : ProfileSource

    /** [folder] is the save directory name, which is unique per world (the display name is not). */
    data class SingleplayerWorld(val folder: String) : ProfileSource
}

/**
 * Everything the mod asks of Minecraft. Shared code only talks to this interface, so it never names a Minecraft
 * class and compiles unchanged whichever naming scheme (Yarn or Mojang) the target version uses.
 * Each scheme has its own implementation in src/compat.
 */
interface Game {
    /** The local player, or null outside a world. */
    fun player(): PlayerState?

    fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean

    fun loadedChunkCount(): Int

    /** Block counts per section of a loaded chunk, read from the section palettes (cheap), or null if it isn't loaded. */
    fun snapshotChunk(chunkX: Int, chunkZ: Int): ChunkSnapshot?

    /** Where the blocks with these ids are in a loaded chunk, between two heights. Only looks in sections that contain one. */
    fun findBlocks(chunkX: Int, chunkZ: Int, minY: Int, maxY: Int, ids: Set<String>): List<BlockRef>

    /** Your connection's round-trip time in milliseconds as the server reports it in the player list, or null if unknown (singleplayer, not joined yet). */
    fun pingMs(): Int?

    /** The other players the client currently knows about. */
    fun otherPlayers(): List<SeenPlayer>

    fun currentProfile(): ProfileSource?

    fun showOverlayMessage(text: String)

    fun showToast(title: String, message: String)

    /** A notification of a kind (info, alert, base, farm, player): in the bottom right where the HUD layer exists, else a game toast. */
    fun showNotification(title: String, message: String, tone: String) = showToast(title, message)

    /** Runs [task] on the game's main thread (module state is not thread-safe). */
    fun runOnGameThread(task: Runnable)

    fun isGuiOpen(): Boolean

    /** Opens the web GUI over whatever is showing; closing it returns there. */
    fun openGui(url: String)
}

/** Set once at startup to the implementation for this Minecraft version. */
object GameHolder {
    lateinit var game: Game
}
