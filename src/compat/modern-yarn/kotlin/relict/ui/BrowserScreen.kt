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
        val b = browser ?: Mcef.api().createBrowser(url, false).also {
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

    override fun shouldPause() = false

    /** Goes back to the menu the GUI was opened from (or the game, if it was opened in-world). */
    override fun close() {
        client?.setScreen(parent)
    }

    override fun removed() {
        Bridge.active = null
        browser?.close()
        browser = null
        super.removed()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val b = browser ?: return
        val view = b.textureView ?: return
        context.state.addSimpleElement(
            TexturedQuadGuiElementRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureCompat.setup(view),
                Matrix3x2f(context.matrices),
                0, 0, width, height,
                0f, 1f, 0f, 1f,
                -1,
                context.scissorStack.peekLast()
            )
        )
        context.setCursor(b.cursorType)
    }

    private fun scaled(click: Click) = Click(click.x * scale, click.y * scale, click.buttonInfo())

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        browser?.onMouseClicked(scaled(click), doubled)
        return super.mouseClicked(click, doubled)
    }

    override fun mouseReleased(click: Click): Boolean {
        browser?.onMouseReleased(scaled(click))
        return super.mouseReleased(click)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        browser?.onMouseMoved((mouseX * scale).toInt(), (mouseY * scale).toInt())
        super.mouseMoved(mouseX, mouseY)
    }

    // Minecraft reports moves with a button held as drags; MCEF tracks the held button itself.
    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
        browser?.onMouseMoved((click.x * scale).toInt(), (click.y * scale).toInt())
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        browser?.onMouseScrolled((mouseX * scale).toInt(), (mouseY * scale).toInt(), verticalAmount)
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyInput): Boolean {
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
        // Chromium's zoom level is log base 1.2 of the zoom factor.
        const val ZOOM_STEP = 1.2
    }
}
