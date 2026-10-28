package relict.ui

import net.dimaskama.mcef.api.MCEFApi
import net.dimaskama.mcef.api.MCEFBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CustomCefBrowserOsr

/** Thin wrapper over MCEF Modern's asynchronous initialisation (Minecraft 1.21.10 and newer). */
object Mcef {
    private val init: MCEFApi.Initialization by lazy { MCEFApi.initialize() }

    /** Starts the (possibly downloading) initialisation in the background. */
    fun start() {
        init
    }

    /** Chromium paints an offscreen browser at 30 frames per second unless told otherwise; animations need more. */
    fun smooth(browser: MCEFBrowser) = smooth(browser.cefBrowser)

    /** This only takes effect once the browser exists natively, so it is also called when a page has loaded. */
    fun smooth(browser: CefBrowser) = setFrameRate(browser, FRAME_RATE)

    fun setFrameRate(browser: CefBrowser, fps: Int) {
        runCatching { (browser as? CustomCefBrowserOsr)?.setWindowlessFrameRate(fps) }
    }

    /** Creates the GUI in the background so it opens instantly (modern MCEF versions). */
    fun preloadGui() = GuiHost.preload()


    private const val FRAME_RATE = 60

    /**
     * Where the GUI window goes on the screen. The page is laid out 980 x 640 CSS pixels and shown as large as fits in about
     * three quarters of the screen, whatever Minecraft's GUI scale is (on "Auto" that is often 4 or more, which would leave the
     * page only a few hundred pixels wide). The browser is exactly the window's size, not the whole screen, so far fewer pixels
     * are painted and uploaded each frame, and its size is a whole number of GUI pixels so it lines up with the screen's pixels
     * one to one and stays sharp.
     */
    class GuiLayout(val x: Int, val y: Int, val width: Int, val height: Int, val zoomLevel: Double)

    fun guiLayout(screenWidth: Int, screenHeight: Int, scale: Double): GuiLayout {
        val fromWidth = screenWidth * WIDTH_SHARE
        val fromHeight = screenHeight * HEIGHT_SHARE * PAGE_WIDTH / PAGE_HEIGHT
        val w = minOf(fromWidth, fromHeight).toInt().coerceAtLeast(1)
        val h = (w * PAGE_HEIGHT / PAGE_WIDTH).toInt().coerceAtLeast(1)
        val zoom = (w * scale / PAGE_WIDTH).coerceIn(0.3, 4.5)
        return GuiLayout((screenWidth - w) / 2, (screenHeight - h) / 2, w, h, kotlin.math.ln(zoom) / kotlin.math.ln(1.2))
    }

    private const val PAGE_WIDTH = 980.0
    private const val PAGE_HEIGHT = 640.0
    private const val WIDTH_SHARE = 0.72
    private const val HEIGHT_SHARE = 0.82

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
