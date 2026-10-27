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
        val b = browser ?: Mcef.api().createBrowser(url, true).also {
            browser = it
            Bridge.active = object : UiBrowser {
                override val cef = it.cefBrowser
            }
            it.setFocus(true)
        }
        b.resize((width * scale).toInt(), (height * scale).toInt())
        // The browser has a device scale of 1.0 but renders at framebuffer resolution,
        // so zoom the page by the GUI scale to keep CSS pixels equal to GUI pixels.
        b.cefBrowser.zoomLevel = ln(scale) / ln(ZOOM_STEP)
    }

    override fun isPauseScreen() = false

    /** Goes back to the menu the GUI was opened from (or the game, if it was opened in-world). */
    override fun onClose() {
        ScreenCompat.set(parent)
    }

    override fun removed() {
        Bridge.active = null
        browser?.close()
        browser = null
        super.removed()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val b = browser ?: return
        val view = b.textureView ?: return
        graphics.guiRenderState.addGuiElement(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureCompat.setup(view),
                Matrix3x2f(graphics.pose()),
                0, 0, width, height,
                0f, 1f, 0f, 1f,
                -1,
                graphics.scissorStack.peek()
            )
        )
        graphics.requestCursor(b.cursorType)
    }

    private fun scaled(event: MouseButtonEvent) = MouseButtonEvent(event.x() * scale, event.y() * scale, event.buttonInfo())

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        browser?.onMouseClicked(scaled(event), doubled)
        return super.mouseClicked(event, doubled)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        browser?.onMouseReleased(scaled(event))
        return super.mouseReleased(event)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        browser?.onMouseMoved((mouseX * scale).toInt(), (mouseY * scale).toInt())
        super.mouseMoved(mouseX, mouseY)
    }

    // Minecraft reports moves with a button held as drags; MCEF tracks the held button itself.
    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        browser?.onMouseMoved((event.x() * scale).toInt(), (event.y() * scale).toInt())
        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        browser?.onMouseScrolled((mouseX * scale).toInt(), (mouseY * scale).toInt(), scrollY)
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
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
        // Chromium's zoom level is log base 1.2 of the zoom factor.
        const val ZOOM_STEP = 1.2
    }
}
