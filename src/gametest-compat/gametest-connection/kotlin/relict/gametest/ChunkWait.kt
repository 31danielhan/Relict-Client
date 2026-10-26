package relict.gametest

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext

/** Fabric's game-test API names this differently per version (26.2 and newer). */
object ChunkWait {
    /** Waits until the client has received the chunks around the player. */
    fun download(world: TestSingleplayerContext) {
        world.connection.waitForChunksDownload()
    }
}
