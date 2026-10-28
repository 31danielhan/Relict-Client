package relict.ui

import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import relict.RelictClient
import relict.render.WebHud

/** Wires the shared CEF client to the bridge and the log. Identical for every MCEF version. */
object CefHandlers {
    private var installed = false

    /**
     * Must run before the first real browser is created, or pages never get `window.cefQuery`.
     * The client is shared by all browsers, so this happens once.
     */
    @Synchronized
    fun install(client: CefClient) {
        if (installed) return
        installed = true
        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(browser: CefBrowser, level: CefSettings.LogSeverity, message: String, source: String, line: Int): Boolean {
                RelictClient.logger.info("[ui] {}", message)
                return false
            }
        })
        val router = CefMessageRouter.create()
        router.addHandler(Bridge.handler, true)
        client.addMessageRouter(router)
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (!frame.isMain) return
                RelictClient.logger.info("[ui] loaded {} (HTTP {})", browser.url, httpStatusCode)
                Mcef.smooth(browser)
                WebHud.onLoaded(browser)
            }
        })
    }
}
