package relict.ui

import net.dimaskama.mcef.api.MCEFApi

/** Thin wrapper over MCEF Modern's asynchronous initialisation (Minecraft 1.21.10 and newer). */
object Mcef {
    private val init: MCEFApi.Initialization by lazy { MCEFApi.initialize() }

    /** Starts the (possibly downloading) initialisation in the background. */
    fun start() {
        init
    }

    val failed: Boolean get() = init.future.isCompletedExceptionally

    // The stage reaches DONE slightly before the future completes, so the future is the source of truth.
    val ready: Boolean get() = init.future.isDone && !failed

    /** Human-readable status for the player, e.g. while Chromium is downloading. */
    val status: String
        get() = when {
            failed -> "MCEF failed to start, see the log"
            ready -> "ready"
            init.percentage >= 0 -> "${init.stage.name.lowercase()} ${init.percentage.toInt()}%"
            else -> init.stage.name.lowercase()
        }

    private var prepared = false

    /**
     * The message router must be attached to the shared client before any browser is created.
     * MCEF Modern doesn't expose its client, so a throwaway browser is used to reach it.
     */
    fun api(): MCEFApi {
        val api = init.future.getNow(null) ?: error("MCEF is not ready: $status")
        if (!prepared) {
            prepared = true
            api.createBrowser("about:blank", false).also {
                CefHandlers.install(it.cefBrowser.client)
                it.close()
            }
        }
        return api
    }
}
