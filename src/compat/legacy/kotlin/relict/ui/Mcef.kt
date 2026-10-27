package relict.ui

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser

/**
 * Wrapper over CinemaMod's MCEF (Minecraft 1.21.2 - 1.21.4). MCEF initialises itself at game start,
 * downloading Chromium first if needed, and shows its own download screen while it does.
 */
object Mcef {
    fun start() {
        // Nothing to do: MCEF's own mod entrypoint starts the download and initialisation.
    }

    val failed: Boolean get() = false

    val ready: Boolean get() = MCEF.isInitialized()

    val status: String get() = if (ready) "ready" else "Chromium is still downloading or starting"

    private var prepared = false

    /** The message router must be attached to the shared client before the first browser is created. */
    fun createBrowser(url: String): MCEFBrowser {
        check(ready) { "MCEF is not ready: $status" }
        if (!prepared) {
            prepared = true
            CefHandlers.install(MCEF.getClient().handle)
        }
        return MCEF.createBrowser(url, true)
    }
}
