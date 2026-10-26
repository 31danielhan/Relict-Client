package relict.ui

import com.cinemamod.mcef.MCEFBrowser
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.ShaderProgramKeys
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.render.BufferRenderer
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats
import net.minecraft.text.Text
import kotlin.math.ln

/**
 * Hosts one MCEF browser covering the whole screen and forwards input to it (Minecraft 1.21.2 - 1.21.4).
 *
 * The browser renders at framebuffer resolution (GUI size × GUI scale) so text stays sharp;
 * mouse coordinates are scaled to match. The browser lives only while the screen is open.
 */
class BrowserScreen(private val url: String, private val parent: Screen? = null) : Screen(Text.literal("Relict")) {
    private var browser: MCEFBrowser? = null

    private val scale: Double get() = MinecraftClient.getInstance().window.scaleFactor

    private fun px(coord: Double) = (coord * scale).toInt()

    override fun init() {
        val b = browser ?: Mcef.createBrowser(url).also {
            browser = it
            Bridge.active = object : UiBrowser {
                override val cef = it
            }
            it.setFocus(true)
        }
        b.resize(px(width.toDouble()), px(height.toDouble()))
        // The browser has a device scale of 1.0 but renders at framebuffer resolution,
        // so zoom the page by the GUI scale to keep CSS pixels equal to GUI pixels.
        b.zoomLevel = ln(scale) / ln(ZOOM_STEP)
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
        RenderSystem.disableDepthTest()
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR)
        RenderSystem.setShaderTexture(0, b.renderer.textureID)
        val right = width.toFloat()
        val bottom = height.toFloat()
        val buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR)
        buffer.vertex(0f, bottom, 0f).texture(0f, 1f).color(255, 255, 255, 255)
        buffer.vertex(right, bottom, 0f).texture(1f, 1f).color(255, 255, 255, 255)
        buffer.vertex(right, 0f, 0f).texture(1f, 0f).color(255, 255, 255, 255)
        buffer.vertex(0f, 0f, 0f).texture(0f, 0f).color(255, 255, 255, 255)
        BufferRenderer.drawWithGlobalProgram(buffer.end())
        RenderSystem.setShaderTexture(0, 0)
        RenderSystem.enableDepthTest()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        browser?.run {
            sendMousePress(px(mouseX), px(mouseY), button)
            setFocus(true)
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        browser?.run {
            sendMouseRelease(px(mouseX), px(mouseY), button)
            setFocus(true)
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        browser?.sendMouseMove(px(mouseX), px(mouseY))
        super.mouseMoved(mouseX, mouseY)
    }

    // Minecraft reports moves with a button held as drags, not as mouseMoved.
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        browser?.sendMouseMove(px(mouseX), px(mouseY))
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        browser?.sendMouseWheel(px(mouseX), px(mouseY), verticalAmount, 0)
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        browser?.run {
            sendKeyPress(keyCode, scanCode.toLong(), modifiers)
            setFocus(true)
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        browser?.run {
            sendKeyRelease(keyCode, scanCode.toLong(), modifiers)
            setFocus(true)
        }
        return super.keyReleased(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (chr == 0.toChar()) return false
        browser?.run {
            sendKeyTyped(chr, modifiers)
            setFocus(true)
        }
        return super.charTyped(chr, modifiers)
    }

    private companion object {
        // Chromium's zoom level is log base 1.2 of the zoom factor.
        const val ZOOM_STEP = 1.2
    }
}
