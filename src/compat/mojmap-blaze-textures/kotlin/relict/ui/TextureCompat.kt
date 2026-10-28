package relict.ui

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.gui.render.TextureSetup

/** Where the texture classes live differs between 26.x versions (this is 26.1 and 26.2: com.mojang.blaze3d). */
/** The browser's texture is drawn exactly 1:1 with the screen's pixels, so nearest-neighbour sampling keeps it perfectly sharp. */
object TextureCompat {
    fun setup(view: GpuTextureView): TextureSetup =
        TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
}
