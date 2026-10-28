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
 * Draws [WebHud]'s transparent pages over the game. Each is a small browser of its own, sitting where its panel goes (see
 * [WebHud] for why they are separate). A browser is created the first time its panel is wanted, is only as big as the panel
 * needs, closes after a while with nothing to show, and takes no input.
 */
object WebHudRenderer {
    private enum class Anchor { TOP_LEFT, TOP_RIGHT, BESIDE_CROSSHAIR, BOTTOM_RIGHT }

    private class Layer(val layer: WebLayer, val anchor: Anchor, val cssWidth: Double, val cssHeight: Double) {
        var browser: MCEFBrowser? = null
        var lastWanted = 0L
        var zoomLevel = 0.0
        var zoomFrames = 0
        var x = 0; var y = 0; var guiWidth = 0; var guiHeight = 0

        // What the browser was last sized for, compared as numbers so nothing is allocated every frame.
        var sw = -1; var sh = -1; var sScale = -1.0; var sHud = -1
    }

    /**
     * The sizes are in CSS pixels, a little bigger than the panel inside (which has its own margin and shadow). The aim card
     * sits just right of and above the crosshair, so its page starts at the crosshair.
     */
    private val layers = listOf(
        Layer(WebHud.finder, Anchor.TOP_LEFT, 236.0, 340.0),
        Layer(WebHud.regions, Anchor.TOP_RIGHT, 290.0, 440.0),
        Layer(WebHud.inspect, Anchor.BESIDE_CROSSHAIR, 330.0, 340.0),
        Layer(WebHud.toasts, Anchor.BOTTOM_RIGHT, 400.0, 340.0),
    )

    fun install() {
        WebHud.supported = true
        WebHud.browserAvailable = { Mcef.start(); Mcef.ready }
        for (l in layers) l.layer.runScript = { js -> l.browser?.cefBrowser?.let { it.executeJavaScript(js, it.url, 0) } }
    }

    /** Draws what is wanted. Returns true when the web HUD owns the screen's HUD, so the canvas HUDs must not draw too. */
    fun draw(context: DrawContext): Boolean {
        if (!WebHud.supported || WebHud.failed) return false
        var owns = false
        for (l in layers) owns = drawLayer(l, context) || owns
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
        val b = Mcef.api().createBrowser(UiAssets.url("index.html") + "#" + l.layer.name, true)
        Mcef.smooth(b)
        l.browser = b
        l.layer.cef = b.cefBrowser
        l.sw = -1
        val client = MinecraftClient.getInstance()
        fit(l, b, client.window.scaledWidth, client.window.scaledHeight, client.window.scaleFactor.toDouble())
        b
    } catch (e: Exception) {
        RelictClient.logger.error("The HUD layer '{}' could not start; using the plain HUDs", l.layer.name, e)
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
        if (l.sw == screenW && l.sh == screenH && l.sScale == scale && l.sHud == hudScale) return
        l.sw = screenW; l.sh = screenH; l.sScale = scale; l.sHud = hudScale
        val factor = (screenW * scale / TARGET_PAGE_WIDTH * hudScale / 100.0).coerceIn(0.5, MAX_ZOOM)
        val wantW = minOf(screenW, Math.ceil(l.cssWidth * factor / scale).toInt())
        val wantH = minOf(screenH, Math.ceil(l.cssHeight * factor / scale).toInt())
        l.guiWidth = wantW
        l.guiHeight = wantH
        l.x = when (l.anchor) {
            Anchor.TOP_LEFT -> 0
            Anchor.TOP_RIGHT, Anchor.BOTTOM_RIGHT -> screenW - wantW
            Anchor.BESIDE_CROSSHAIR -> screenW / 2
        }
        l.y = when (l.anchor) {
            Anchor.TOP_LEFT, Anchor.TOP_RIGHT -> 0
            Anchor.BOTTOM_RIGHT -> screenH - wantH
            Anchor.BESIDE_CROSSHAIR -> (screenH / 2 - Math.ceil(CARD_ABOVE_CROSSHAIR * factor / scale).toInt()).coerceAtLeast(0)
        }
        b.resize((wantW * scale).toInt(), (wantH * scale).toInt())
        l.zoomLevel = ln(factor) / ln(ZOOM_STEP)
        l.zoomFrames = 0
        b.cefBrowser.zoomLevel = l.zoomLevel
    }

    /** The page is about this many CSS pixels wide, whatever the window size. */
    private const val TARGET_PAGE_WIDTH = 1200.0

    /** Chromium zooms up to 500%; stay well inside that. */
    private const val MAX_ZOOM = 4.0

    /** How far above the crosshair the aim card's page starts, in CSS pixels (the card sits a little above the crosshair). */
    private const val CARD_ABOVE_CROSSHAIR = 90.0

    /** How long nothing is shown before a browser is closed to free its memory. */
    private const val IDLE_CLOSE_MS = 30_000L

    // Chromium's zoom level is log base 1.2 of the zoom factor.
    private const val ZOOM_STEP = 1.2
}
