package relict.render

import net.dimaskama.mcef.api.MCEFBrowser
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState
import org.joml.Matrix3x2f
import relict.RelictClient
import relict.core.ClientSettings
import relict.ui.Mcef
import relict.ui.TextureCompat
import relict.ui.UiAssets
import kotlin.math.ln

/**
 * Draws [WebHud]'s two transparent pages over the game: the panels across the top and the notifications in the bottom
 * right corner. Each browser is created the first time something is wanted on its layer, is only as big as that layer
 * needs, closes after a while with nothing to show, and takes no input.
 */
object WebHudRenderer {
    private class Layer(val layer: WebLayer, val hash: String, val corner: Corner, val cssWidth: Double, val cssHeight: Double) {
        var browser: MCEFBrowser? = null
        var lastWanted = 0L
        var zoomLevel = 0.0
        var zoomFrames = 0
        var x = 0; var y = 0; var guiWidth = 0; var guiHeight = 0
        var sizedFor = ""
    }

    private enum class Corner { TOP_FULL, BOTTOM_RIGHT }

    /** The panels need the full width and the top of the screen; the notifications a small square at the bottom right. */
    private val panels = Layer(WebHud.panels, "hud", Corner.TOP_FULL, 0.0, 560.0)
    private val toasts = Layer(WebHud.toasts, "toasts", Corner.BOTTOM_RIGHT, 400.0, 340.0)
    private val all = listOf(panels, toasts)

    fun install() {
        WebHud.supported = true
        WebHud.browserAvailable = { Mcef.start(); Mcef.ready }
        for (l in all) l.layer.runScript = { js -> l.browser?.cefBrowser?.let { it.executeJavaScript(js, it.url, 0) } }
    }

    /** Draws what is wanted. Returns true when the web HUD owns the screen's HUD, so the canvas HUDs must not draw too. */
    fun draw(context: DrawContext): Boolean {
        if (!WebHud.supported || WebHud.failed) return false
        var owns = false
        for (l in all) owns = drawLayer(l, context) || owns
        return owns
    }

    private fun drawLayer(l: Layer, context: DrawContext): Boolean {
        val now = System.currentTimeMillis()
        val wanted = WebHud.wanted(l.layer, now)
        if (l.browser == null) {
            if (!wanted) return false
            Mcef.start() // Chromium starts the first time something needs it
            if (!Mcef.ready) return false // still starting: the canvas HUDs cover the gap
            create(l) ?: return false
        }
        val b = l.browser ?: return false
        // Until the page has loaded there is nothing to show yet; staying quiet avoids flashing the canvas HUD first.
        if (!l.layer.pageReady) return true
        if (wanted) l.lastWanted = now
        else if (now - l.lastWanted > IDLE_CLOSE_MS) {
            close(l) // nothing to show for a while: give the memory back until something needs it again
            return false
        } else if (now - l.lastWanted > WebHud.EXIT_MS) return true

        fit(l, b, context.scaledWindowWidth, context.scaledWindowHeight, MinecraftClient.getInstance().window.scaleFactor.toDouble())
        // A zoom set before the browser exists natively is lost, so it is set again for the first moments.
        if (l.zoomFrames++ < 40) b.cefBrowser.zoomLevel = l.zoomLevel
        val view = b.textureView ?: return true
        context.state.addSimpleElement(
            TexturedQuadGuiElementRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureCompat.setup(view),
                Matrix3x2f(context.matrices),
                l.x, l.y, l.x + l.guiWidth, l.y + l.guiHeight,
                0f, 1f, 0f, 1f,
                -1,
                context.scissorStack.peekLast()
            )
        )
        return true
    }

    private fun create(l: Layer): MCEFBrowser? = try {
        val b = Mcef.api().createBrowser(UiAssets.url("index.html") + "#" + l.hash, true)
        Mcef.smooth(b)
        l.browser = b
        l.layer.cef = b.cefBrowser
        l.sizedFor = ""
        val client = MinecraftClient.getInstance()
        fit(l, b, client.window.scaledWidth, client.window.scaledHeight, client.window.scaleFactor.toDouble())
        b
    } catch (e: Exception) {
        RelictClient.logger.error("The HUD layer '{}' could not start; using the plain HUDs", l.hash, e)
        WebHud.failed = true
        null
    }

    private fun close(l: Layer) {
        l.browser?.let { runCatching { it.close() } }
        l.browser = null
        l.layer.reset()
    }

    /**
     * Sizes the page's browser and places it. Its size follows the window, not the GUI scale setting (on "Auto" that can be
     * 4 or more and would make everything huge), times the player's "HUD size" setting. The browser is a whole number of GUI
     * pixels so it lines up with the screen's pixels one to one and stays sharp.
     */
    private fun fit(l: Layer, b: MCEFBrowser, screenW: Int, screenH: Int, scale: Double) {
        val hudScale = ClientSettings.hudScale.value
        val key = "$screenW,$screenH,$scale,$hudScale"
        if (key == l.sizedFor) return
        l.sizedFor = key
        val factor = (screenW * scale / TARGET_PAGE_WIDTH * hudScale / 100.0).coerceIn(0.5, MAX_ZOOM)
        val wantW = if (l.corner == Corner.TOP_FULL) screenW else minOf(screenW, Math.ceil(l.cssWidth * factor / scale).toInt())
        val wantH = minOf(screenH, Math.ceil(l.cssHeight * factor / scale).toInt())
        l.guiWidth = wantW
        l.guiHeight = wantH
        l.x = if (l.corner == Corner.BOTTOM_RIGHT) screenW - wantW else 0
        l.y = if (l.corner == Corner.BOTTOM_RIGHT) screenH - wantH else 0
        b.resize((wantW * scale).toInt(), (wantH * scale).toInt())
        l.zoomLevel = ln(factor) / ln(ZOOM_STEP)
        l.zoomFrames = 0
        b.cefBrowser.zoomLevel = l.zoomLevel
    }

    /** The page is about this many CSS pixels wide, whatever the window size. */
    private const val TARGET_PAGE_WIDTH = 1200.0

    /** Chromium zooms up to 500%; stay well inside that. */
    private const val MAX_ZOOM = 4.0

    /** How long nothing is shown before a browser is closed to free its memory. */
    private const val IDLE_CLOSE_MS = 30_000L

    // Chromium's zoom level is log base 1.2 of the zoom factor.
    private const val ZOOM_STEP = 1.2
}
