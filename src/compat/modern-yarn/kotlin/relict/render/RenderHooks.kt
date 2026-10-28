package relict.render

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import relict.RelictClient

/** Draws the HUDs for Minecraft 1.21.10 and 1.21.11; the world overlays are in [WorldOverlays], which differs between the two. */
object RenderHooks {
    fun install() {
        Render.overlaysSupported = WorldOverlays.install()
        Render.hudSupported = true
        WebHudRenderer.install()
        HudElementRegistry.addLast(Identifier.of(RelictClient.MOD_ID, "donut")) { context, _ -> drawHud(context) }
    }

    private fun drawHud(context: DrawContext) {
        if (MinecraftClient.getInstance().options.hudHidden) return
        // The web layer draws the HUDs when it can; the plain canvas HUDs below are the fallback (browser still starting, or it failed).
        if (WebHudRenderer.draw(context)) return
        if (Render.huds.isEmpty()) return
        val canvas = HudDrawer(context)
        for (provider in Render.huds) {
            try {
                provider.draw(canvas)
            } catch (e: Exception) {
                RelictClient.logger.error("A HUD failed to draw", e)
            }
        }
    }

    private class HudDrawer(private val context: DrawContext) : HudCanvas {
        private val font = MinecraftClient.getInstance().textRenderer
        override val width: Int get() = context.scaledWindowWidth
        override val height: Int get() = context.scaledWindowHeight

        override fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int) = context.fill(x, y, x + w, y + h, argb)

        override fun text(text: String, x: Int, y: Int, argb: Int, scale: Float) {
            if (scale == 1f) {
                context.drawText(font, text, x, y, argb, true)
                return
            }
            val m = context.matrices
            m.pushMatrix()
            m.translate(x.toFloat(), y.toFloat())
            m.scale(scale, scale)
            context.drawText(font, text, 0, 0, argb, true)
            m.popMatrix()
        }

        override fun textWidth(text: String, scale: Float): Int = (font.getWidth(text) * scale).toInt()
    }
}
