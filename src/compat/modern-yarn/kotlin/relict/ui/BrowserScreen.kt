package relict.ui

import net.dimaskama.mcef.api.MCEFBrowser
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import relict.RelictClient
import relict.ScreenKeys
import org.joml.Matrix3x2f
import kotlin.math.ln

/**
 * Hosts one MCEF browser covering the whole screen and forwards input to it.
 *
 * The browser renders at framebuffer resolution (GUI size × GUI scale) so text stays sharp;
 * mouse coordinates are scaled to match. The browser lives only while the screen is open.
 */
class BrowserScreen(private val url: String, private val parent: Screen? = null) : Screen(Text.literal("Relict")) {
    private var browser: MCEFBrowser? = null

    private val scale: Double get() = MinecraftClient.getInstance().window.scaleFactor.toDouble()

    override fun init() {
        // The browser (and its loaded page) was created in the background when Chromium started, so this is instant.
        val b = browser ?: GuiHost.take().also {
            browser = it
            Bridge.active = object : UiBrowser {
                override val cef = it.cefBrowser
            }
            it.setFocus(true)
            closeAt = 0L
        }
        // The browser is just the window, centred (see Mcef.guiLayout), and the page is zoomed so that its 980 x 640 CSS pixels
        // fill it. A zoom set before the browser exists is lost, so it is set again for the first moments in render().
        layout = Mcef.guiLayout(width, height, scale)
        b.resize((layout.width * scale).toInt(), (layout.height * scale).toInt())
        zoomFrames = 0
        applyZoom(b)
        if (!announced) {
            announced = true
            // Tells the page to refresh what it shows and play its opening animation.
            Bridge.emit("gui.show", com.google.gson.JsonObject())
        }
    }

    private var announced = false

    /** When the closing animation is over and the screen can really go away (0 = not closing). */
    private var closeAt = 0L

    /** Asks the page to play its closing animation, then closes. */
    private fun requestClose() {
        if (closeAt != 0L) return
        Bridge.emit("gui.hide", com.google.gson.JsonObject())
        closeAt = System.currentTimeMillis() + CLOSE_MS
    }

    private var zoomFrames = 0

    private var layout = Mcef.GuiLayout(0, 0, 1, 1, 0.0)

    private fun applyZoom(b: net.dimaskama.mcef.api.MCEFBrowser) {
        b.cefBrowser.zoomLevel = layout.zoomLevel
    }

    /** A position on the screen, in GUI pixels, as the page's own pixel position (the page starts where the window does). */
    private fun px(screen: Double, origin: Int): Int = ((screen - origin) * scale).toInt()

    override fun shouldPause() = false

    /** Goes back to the menu the GUI was opened from (or the game, if it was opened in-world). */
    override fun close() = requestClose()

    override fun removed() {
        Bridge.active = null
        UiState.editing = false
        browser?.let { GuiHost.park(it) } // kept alive in the background, not closed
        browser = null
        super.removed()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val b = browser ?: return
        if (closeAt != 0L && System.currentTimeMillis() >= closeAt) {
            closeAt = 0L
            client?.setScreen(parent)
            return
        }
        if (zoomFrames++ < ZOOM_FRAMES) applyZoom(b)
        val view = b.textureView ?: return
        context.state.addSimpleElement(
            TexturedQuadGuiElementRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureCompat.setup(view),
                Matrix3x2f(context.matrices),
                layout.x, layout.y, layout.x + layout.width, layout.y + layout.height,
                0f, 1f, 0f, 1f,
                -1,
                context.scissorStack.peekLast()
            )
        )
        context.setCursor(b.cursorType)
    }

    private fun scaled(click: Click) = Click(px(click.x, layout.x).toDouble(), px(click.y, layout.y).toDouble(), click.buttonInfo())

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        browser?.onMouseClicked(scaled(click), doubled)
        return super.mouseClicked(click, doubled)
    }

    override fun mouseReleased(click: Click): Boolean {
        browser?.onMouseReleased(scaled(click))
        return super.mouseReleased(click)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        browser?.onMouseMoved(px(mouseX, layout.x), px(mouseY, layout.y))
        super.mouseMoved(mouseX, mouseY)
    }

    // Minecraft reports moves with a button held as drags; MCEF tracks the held button itself.
    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
        browser?.onMouseMoved(px(click.x, layout.x), px(click.y, layout.y))
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        browser?.onMouseScrolled(px(mouseX, layout.x), px(mouseY, layout.y), verticalAmount)
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        // The key that opens the window closes it too, unless a text field is being typed in (Right Shift makes capitals there).
        if (!UiState.editing && ScreenKeys.matches(RelictClient.openKey, input)) {
            requestClose()
            return true
        }
        browser?.onKeyPressed(input)
        return super.keyPressed(input)
    }

    override fun keyReleased(input: KeyInput): Boolean {
        browser?.onKeyReleased(input)
        return super.keyReleased(input)
    }

    override fun charTyped(input: CharInput): Boolean {
        browser?.onCharTyped(input)
        return super.charTyped(input)
    }

    private companion object {
        const val ZOOM_FRAMES = 40

        /** How long the page's closing animation gets before the screen goes. */
        const val CLOSE_MS = 170L
    }
}
