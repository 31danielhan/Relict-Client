package relict.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderSetup
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.VertexFormats
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import relict.RelictClient
import kotlin.math.sqrt

/** Draws the world overlays on Minecraft 1.21.11. They use their own pipelines with the depth test off, so they show through walls. */
object WorldOverlays {
    private val quadPipeline: RenderPipeline by lazy {
        RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET, RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation(Identifier.of(RelictClient.MOD_ID, "pipeline/esp_quads"))
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                .withCull(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .build()
        )
    }

    private val linePipeline: RenderPipeline by lazy {
        RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                .withLocation(Identifier.of(RelictClient.MOD_ID, "pipeline/esp_lines"))
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .build()
        )
    }

    private val quadLayer: RenderLayer by lazy { RenderLayer.of("relict_esp_quads", RenderSetup.builder(quadPipeline).build()) }
    private val lineLayer: RenderLayer by lazy { RenderLayer.of("relict_esp_lines", RenderSetup.builder(linePipeline).build()) }

    /** Returns whether overlays are available. */
    fun install(): Boolean {
        WorldRenderEvents.END_MAIN.register { context -> drawWorld(context) }
        return true
    }

    private fun drawWorld(context: WorldRenderContext) {
        if (Render.overlays.isEmpty()) return
        val consumers = context.consumers() as? VertexConsumerProvider.Immediate ?: return
        val camera = MinecraftClient.getInstance().gameRenderer.camera.cameraPos
        val canvas = Canvas(context.matrices(), consumers, camera.x, camera.y, camera.z)
        for (provider in Render.overlays) {
            try {
                provider.draw(canvas)
            } catch (e: Exception) {
                RelictClient.logger.error("An overlay failed to draw", e)
            }
        }
        canvas.flush()
    }

    /**
     * Collects what the overlays want drawn, then writes it one layer at a time. Minecraft's shared buffer provider
     * ends the previous layer's buffer as soon as another layer is requested, so two buffers can't be open together.
     */
    private class Canvas(
        private val matrices: MatrixStack,
        private val consumers: VertexConsumerProvider.Immediate,
        private val camX: Double, private val camY: Double, private val camZ: Double,
    ) : OverlayCanvas {
        private class Box(val x0: Float, val y0: Float, val z0: Float, val x1: Float, val y1: Float, val z1: Float, val argb: Int)
        private class Edge(val x1: Float, val y1: Float, val z1: Float, val x2: Float, val y2: Float, val z2: Float, val argb: Int)

        private val solids = ArrayList<Box>()
        private val edges = ArrayList<Edge>()

        override fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, argb: Int, filled: Boolean) {
            val x0 = (minX - camX).toFloat(); val y0 = (minY - camY).toFloat(); val z0 = (minZ - camZ).toFloat()
            val x1 = (maxX - camX).toFloat(); val y1 = (maxY - camY).toFloat(); val z1 = (maxZ - camZ).toFloat()
            if (filled) {
                solids += Box(x0, y0, z0, x1, y1, z1, argb)
            } else {
                edges += listOf(
                    Edge(x0, y0, z0, x1, y0, z0, argb), Edge(x1, y0, z0, x1, y0, z1, argb), Edge(x1, y0, z1, x0, y0, z1, argb), Edge(x0, y0, z1, x0, y0, z0, argb),
                    Edge(x0, y1, z0, x1, y1, z0, argb), Edge(x1, y1, z0, x1, y1, z1, argb), Edge(x1, y1, z1, x0, y1, z1, argb), Edge(x0, y1, z1, x0, y1, z0, argb),
                    Edge(x0, y0, z0, x0, y1, z0, argb), Edge(x1, y0, z0, x1, y1, z0, argb), Edge(x1, y0, z1, x1, y1, z1, argb), Edge(x0, y0, z1, x0, y1, z1, argb),
                )
            }
        }

        override fun line(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, argb: Int) {
            edges += Edge((x1 - camX).toFloat(), (y1 - camY).toFloat(), (z1 - camZ).toFloat(), (x2 - camX).toFloat(), (y2 - camY).toFloat(), (z2 - camZ).toFloat(), argb)
        }

        fun flush() {
            val e = matrices.peek()
            if (solids.isNotEmpty()) {
                val q = consumers.getBuffer(quadLayer)
                for (b in solids) {
                    fun v(x: Float, y: Float, z: Float) { q.vertex(e, x, y, z).color(b.argb) }
                    v(b.x0, b.y0, b.z0); v(b.x1, b.y0, b.z0); v(b.x1, b.y0, b.z1); v(b.x0, b.y0, b.z1) // bottom
                    v(b.x0, b.y1, b.z0); v(b.x0, b.y1, b.z1); v(b.x1, b.y1, b.z1); v(b.x1, b.y1, b.z0) // top
                    v(b.x0, b.y0, b.z0); v(b.x0, b.y1, b.z0); v(b.x1, b.y1, b.z0); v(b.x1, b.y0, b.z0) // north
                    v(b.x0, b.y0, b.z1); v(b.x1, b.y0, b.z1); v(b.x1, b.y1, b.z1); v(b.x0, b.y1, b.z1) // south
                    v(b.x0, b.y0, b.z0); v(b.x0, b.y0, b.z1); v(b.x0, b.y1, b.z1); v(b.x0, b.y1, b.z0) // west
                    v(b.x1, b.y0, b.z0); v(b.x1, b.y1, b.z0); v(b.x1, b.y1, b.z1); v(b.x1, b.y0, b.z1) // east
                }
                consumers.draw(quadLayer)
            }
            if (edges.isNotEmpty()) {
                val l = consumers.getBuffer(lineLayer)
                for (d in edges) {
                    var nx = d.x2 - d.x1; var ny = d.y2 - d.y1; var nz = d.z2 - d.z1
                    val len = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 0f } ?: 1f
                    nx /= len; ny /= len; nz /= len
                    l.vertex(e, d.x1, d.y1, d.z1).color(d.argb).normal(e, nx, ny, nz).lineWidth(LINE_WIDTH)
                    l.vertex(e, d.x2, d.y2, d.z2).color(d.argb).normal(e, nx, ny, nz).lineWidth(LINE_WIDTH)
                }
                consumers.draw(lineLayer)
            }
        }
    }

    private const val LINE_WIDTH = 2.5f
}
