package relict.ui

import net.dimaskama.mcef.api.MCEFBrowser
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.network.chat.Component
import org.joml.Matrix3x2f
import kotlin.math.ln

/**
 * Hosts one MCEF browser covering the whole screen and forwards input to it (Minecraft 26.x, Mojang names).
 *
 * The browser renders at framebuffer resolution (GUI size × GUI scale) so text stays sharp;
 * mouse coordinates are scaled to match. The browser lives only while the screen is open.
 */
class BrowserScreen(private val url: String, private val parent: Screen? = null) : Screen(Component.literal("Relict")) {
    private var browser: MCEFBrowser? = null

    private val scale: Double get() = Minecraft.getInstance().window.guiScale.toDouble()

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

    override fun isPauseScreen() = false

    /** Goes back to the menu the GUI was opened from (or the game, if it was opened in-world). */
    override fun onClose() = requestClose()

    override fun removed() {
        Bridge.active = null
        UiState.editing = false
        browser?.let { GuiHost.park(it) } // kept alive in the background, not closed
        browser = null
        super.removed()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val b = browser ?: return
        if (closeAt != 0L && System.currentTimeMillis() >= closeAt) {
            closeAt = 0L
            ScreenCompat.set(parent)
            return
        }
        if (zoomFrames++ < ZOOM_FRAMES) applyZoom(b)
        val view = b.textureView ?: return
        graphics.guiRenderState.addGuiElement(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureCompat.setup(view),
                Matrix3x2f(graphics.pose()),
                layout.x, layout.y, layout.x + layout.width, layout.y + layout.height,
                0f, 1f, 0f, 1f,
                -1,
                graphics.scissorStack.peek()
            )
        )
        graphics.requestCursor(b.cursorType)
    }

    private fun scaled(event: MouseButtonEvent) = MouseButtonEvent(px(event.x(), layout.x).toDouble(), px(event.y(), layout.y).toDouble(), event.buttonInfo())

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        browser?.onMouseClicked(scaled(event), doubled)
        return super.mouseClicked(event, doubled)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        browser?.onMouseReleased(scaled(event))
        return super.mouseReleased(event)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        browser?.onMouseMoved(px(mouseX, layout.x), px(mouseY, layout.y))
        super.mouseMoved(mouseX, mouseY)
    }

    // Minecraft reports moves with a button held as drags; MCEF tracks the held button itself.
    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        browser?.onMouseMoved(px(event.x(), layout.x), px(event.y(), layout.y))
        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        browser?.onMouseScrolled(px(mouseX, layout.x), px(mouseY, layout.y), scrollY)
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        // The key that opens the window closes it too, unless a text field is being typed in (Right Shift makes capitals there).
        if (!UiState.editing && relict.ScreenKeys.matches(relict.RelictClient.openKey, event)) {
            requestClose()
            return true
        }
        browser?.onKeyPressed(event)
        return super.keyPressed(event)
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        browser?.onKeyReleased(event)
        return super.keyReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        browser?.onCharTyped(event)
        return super.charTyped(event)
    }

    private companion object {
        const val ZOOM_FRAMES = 40

        /** How long the page's closing animation gets before the screen goes. */
        const val CLOSE_MS = 170L
    }
}
