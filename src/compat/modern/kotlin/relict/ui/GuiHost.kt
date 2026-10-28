package relict.ui

import net.dimaskama.mcef.api.MCEFBrowser

/**
 * Keeps the GUI's browser alive between openings. It is created once Chromium is ready and loads the page in the
 * background, so opening the window is instant instead of waiting for a page load. While the window is closed the page
 * is invisible and idle, so Chromium has nothing to paint and it costs next to nothing; it is not slowed down on purpose,
 * because a browser that is told to run slowly takes a moment to speed up again and the opening animation would stutter.
 */
object GuiHost {
    private var browser: MCEFBrowser? = null

    /** Called every tick once Chromium is ready; does nothing after the first time. */
    fun preload() {
        if (browser != null || !Mcef.ready) return
        browser = create()
    }

    /** The browser for the window that is opening: the preloaded one, or a new one if it was not ready yet. */
    fun take(): MCEFBrowser {
        return browser ?: create().also { browser = it }
    }

    /** The window closed: keep the browser and the page for next time. */
    fun park(b: MCEFBrowser) {
        if (b !== browser) runCatching { b.close() }
    }

    private fun create(): MCEFBrowser {
        val b = Mcef.api().createBrowser(UiAssets.url("index.html"), true)
        b.resize(PRELOAD_WIDTH, PRELOAD_HEIGHT) // resized to the real window when it opens
        Mcef.smooth(b)
        return b
    }

    private const val PRELOAD_WIDTH = 1280
    private const val PRELOAD_HEIGHT = 720
}
