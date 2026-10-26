package relict.gametest

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext

/** Fabric's game-test API names this differently per version (1.21.x (Fabric API 0.1xx for 1.21.4 - 1.21.11)). */
object ChunkWait {
    /** Waits until the client has received the chunks around the player. */
    fun download(world: TestSingleplayerContext) {
        world.clientWorld.waitForChunksDownload()
    }
}
