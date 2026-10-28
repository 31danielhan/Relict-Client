package relict.ui

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.texture.TextureSetup

/** The one place where drawing a browser texture differs between Minecraft versions (this is the 1.21.11 version). */
/** The browser's texture is drawn exactly 1:1 with the screen's pixels, so nearest-neighbour sampling keeps it perfectly sharp. */
object TextureCompat {
    fun setup(view: GpuTextureView): TextureSetup =
        TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.NEAREST))
}
