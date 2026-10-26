package relict

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import java.awt.Window
import kotlin.concurrent.thread

/**
 * Lets the game quit cleanly even though an embedded Chromium is running.
 *
 * Newer Minecraft versions end the process by waiting for every non-daemon thread to finish and kill the game with a
 * crash report if that takes more than 15 seconds. Chromium (via AWT) can leave such threads behind on some systems.
 * Once the game has really finished (the JVM's main thread has returned), this disposes any AWT windows still open and,
 * if only AWT's idle threads are keeping the process alive, exits it.
 */
object ExitGuard {
    private const val POLL_MS = 250L
    private const val SETTLE_MS = 2_000L

    fun install() {
        ClientLifecycleEvents.CLIENT_STOPPING.register { start() }
    }

    private fun start() {
        thread(name = "Relict exit guard", isDaemon = true) {
            // The game is stopping; wait until it has completely finished, so nothing is ever cut short.
            while (!isAlive(MAIN_FINISHED)) Thread.sleep(POLL_MS)
            Thread.sleep(SETTLE_MS)

            val windows = Window.getWindows()
            if (windows.isNotEmpty()) {
                System.err.println("[Relict] Disposing ${windows.size} leftover AWT window(s) so the game can exit")
                windows.forEach { it.dispose() }
                Thread.sleep(SETTLE_MS)
            }
            if (onlyAwtThreadsLeft()) {
                System.err.println("[Relict] Only AWT threads are keeping the process alive after shutdown; exiting")
                System.exit(0)
            }
        }
    }

    /** The JVM starts this thread once main() has returned. */
    private const val MAIN_FINISHED = "DestroyJavaVM"

    private fun isAlive(name: String) = Thread.getAllStackTraces().keys.any { it.name == name && it.isAlive }

    private fun onlyAwtThreadsLeft(): Boolean = Thread.getAllStackTraces().keys.none {
        it.isAlive && !it.isDaemon && it.name != MAIN_FINISHED && !it.name.startsWith("AWT-") && it !== Thread.currentThread()
    }
}
