package relict.ui

import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.texture.TextureSetup

/** The one place where drawing a browser texture differs between Minecraft versions (this is the 1.21.10 version). */
object TextureCompat {
    // 1.21.10 has no separate sampler object; filtering belongs to the texture itself.
    fun setup(view: GpuTextureView): TextureSetup = TextureSetup.of(view)
}
