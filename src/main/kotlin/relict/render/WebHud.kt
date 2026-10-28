package relict.render

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.cef.browser.CefBrowser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One transparent web page drawn over the world through MCEF. [WebHud] has two: the panels (region map, base finder
 * list, the card for what you are aiming at) across the top of the screen, and the notifications in the bottom right
 * corner. They are separate browsers so each can be only as big as it needs to be.
 *
 * State is kept here as JSON per key, so a page can be filled in again whenever it (re)loads.
 */
class WebLayer(val name: String) {
    private val gson = Gson()

    /** The HUD browser, so the shared load handler can tell when its page is ready. */
    @Volatile var cef: CefBrowser? = null

    /** Runs JavaScript in this layer's page. */
    @Volatile var runScript: ((String) -> Unit)? = null

    @Volatile var pageReady = false
        private set

    internal val state = ConcurrentHashMap<String, String>()
    private val pendingToasts = CopyOnWriteArrayList<String>()

    @Volatile var toastsUntil = 0L

    fun set(key: String, value: JsonElement?) {
        val json = value?.toString()
        if (json == null) {
            if (state.remove(key) != null) send("hud.set", payload(key, null))
            return
        }
        if (state.put(key, json) != json) send("hud.set", payload(key, json))
    }

    fun toast(json: String, ms: Int) {
        toastsUntil = maxOf(toastsUntil, System.currentTimeMillis() + ms + WebHud.EXIT_MS)
        if (pageReady) send("hud.toast", json) else pendingToasts += json
    }

    fun pageLoaded() {
        pageReady = true
        state.forEach { (key, json) -> send("hud.set", payload(key, json)) }
        pendingToasts.forEach { send("hud.toast", it) }
        pendingToasts.clear()
    }

    /** The browser went away (or is about to be replaced): the next one starts empty. */
    fun reset() {
        pageReady = false
        cef = null
    }

    /** True while something should be on screen: any panel's data, or a notification that has not finished. */
    fun wanted(now: Long, supportKeys: Set<String>): Boolean = now < toastsUntil || state.keys.any { it !in supportKeys }

    private fun payload(key: String, json: String?) = """{"key":${gson.toJson(key)},"value":${json ?: "null"}}"""

    private fun send(event: String, payloadJson: String) {
        val run = runScript ?: return
        if (!pageReady) return
        run("window.relict && window.relict.emit(${gson.toJson(event)}, $payloadJson)")
    }
}

object WebHud {
    /** A version's render hook can draw the layers. */
    @Volatile var supported = false

    /** Set if a browser couldn't be created, so everything falls back to the canvas HUDs and the game's own toasts. */
    @Volatile var failed = false

    /** MCEF has finished starting. Set by the render hook, which knows its version's MCEF. */
    @Volatile var browserAvailable: () -> Boolean = { false }

    val panels = WebLayer("panels")
    val toasts = WebLayer("toasts")
    val layers = listOf(panels, toasts)

    /** Keys that configure the pages rather than show a panel. */
    private val supportKeys = setOf("config", "player")

    /** True once any layer's page has loaded. */
    val pageReady: Boolean get() = panels.pageReady

    /** Shows a panel with this data, changes it, or (null) removes it. Cheap to call every tick: unchanged data is not resent. */
    fun set(key: String, value: JsonElement?) {
        panels.set(key, value)
        if (key == "config") toasts.set(key, value) // the notifications follow the colour and motion settings too
    }

    /** Shows a notification in the bottom right. Returns false if the page can't be used (the caller then shows the game's own toast). */
    fun toast(title: String, message: String, tone: String = "info", ms: Int = 4500): Boolean {
        if (!supported || failed || !browserAvailable()) return false
        val json = JsonObject().apply {
            addProperty("title", title); addProperty("message", message); addProperty("tone", tone); addProperty("ms", ms)
        }.toString()
        toasts.toast(json, ms)
        return true
    }

    fun wanted(layer: WebLayer, now: Long = System.currentTimeMillis()): Boolean = layer.wanted(now, supportKeys)

    /** The layer a freshly loaded browser belongs to, if it is one of ours. */
    fun onLoaded(browser: CefBrowser) {
        layers.firstOrNull { it.cef === browser }?.pageLoaded()
    }

    /** How long after the last pop-up's time is up the layer keeps drawing, so its exit animation finishes. */
    const val EXIT_MS = 600L
}
