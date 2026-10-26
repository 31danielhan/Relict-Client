package relict.ui

import com.google.gson.JsonElement
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import relict.RelictClient
import relict.core.ApiException
import relict.core.Json
import relict.game.GameHolder

/**
 * Connects the web UI to [Api]. The page calls `window.cefQuery`, which arrives here on a CEF thread;
 * the call is run on the game's client thread (module state is not thread-safe) and answered asynchronously.
 */
object Bridge {
    lateinit var api: Api

    /** The browser of the screen that is currently open, if any. Events are pushed to it. */
    @Volatile
    var active: UiBrowser? = null

    val handler = object : CefMessageRouterHandlerAdapter() {
        override fun onQuery(
            browser: CefBrowser, frame: CefFrame, queryId: Long,
            request: String, persistent: Boolean, callback: CefQueryCallback,
        ): Boolean {
            // Only our own bundled UI may call into the game.
            if (!browser.url.startsWith(UiAssets.baseUrl)) {
                callback.failure(403, "Forbidden")
                return true
            }
            GameHolder.game.runOnGameThread {
                try {
                    callback.success(api.handle(request))
                } catch (e: ApiException) {
                    callback.failure(e.code, e.message ?: "Error")
                } catch (e: Exception) {
                    RelictClient.logger.error("Bridge call failed: {}", request, e)
                    callback.failure(500, "Internal error")
                }
            }
            return true
        }
    }

    /** Pushes an event to the open UI, delivered to listeners registered with `on(name, fn)` in the page. */
    fun emit(name: String, payload: JsonElement) {
        val browser = active?.cef ?: return
        val script = "window.relict && window.relict.emit(${Json.compact.toJson(name)}, ${Json.compact.toJson(payload)})"
        browser.executeJavaScript(script, browser.url, 0)
    }
}
