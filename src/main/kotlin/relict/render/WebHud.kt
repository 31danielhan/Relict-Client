package relict.render

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.cef.browser.CefBrowser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One transparent web page drawn over the world through MCEF. [WebHud] has four, each its own small browser sitting where
 * its panel goes: the base finder list (top left), the region map (top right), the card for what you are aiming at (by the
 * crosshair) and the notifications (bottom right).
 *
 * They are separate on purpose. MCEF copies and uploads a browser's whole picture to the GPU every time Chromium paints,
 * however little changed, so a change in the map's coordinates costs the size of the map and not of the whole screen.
 *
 * State is kept here as JSON per key, so a page can be filled in again whenever it (re)loads.
 */
class WebLayer(val name: String, /** The panel keys this layer shows; "config" and "player" go to every layer that lists them in [support]. */ val keys: Set<String>, val support: Set<String> = emptySet()) {
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

    val finder = WebLayer("finder", setOf("finder"), setOf("player", "config"))
    val regions = WebLayer("regions", setOf("regions"), setOf("player", "config"))
    val inspect = WebLayer("inspect", setOf("inspect"), setOf("config"))
    val toasts = WebLayer("toasts", emptySet(), setOf("config"))
    val layers = listOf(finder, regions, inspect, toasts)

    /** Keys that configure the pages rather than show a panel. */
    private val supportKeys = setOf("config", "player")

    /** True once the region map's page has loaded (the tests wait for it). */
    val pageReady: Boolean get() = regions.pageReady

    /** Shows a panel with this data, changes it, or (null) removes it. Cheap to call every tick: unchanged data is not resent. */
    fun set(key: String, value: JsonElement?) {
        for (layer in layers) if (key in layer.keys || key in layer.support) layer.set(key, value)
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
