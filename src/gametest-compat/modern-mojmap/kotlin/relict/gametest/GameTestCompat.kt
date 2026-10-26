package relict.gametest

import net.minecraft.client.Minecraft
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.input.KeyEvent
import relict.ui.ScreenCompat

/** Test helpers whose Minecraft API differs between versions (26.x, Mojang names). */
object GameTestCompat {
    /** Rebinds the key binding with this translation key to a GLFW key. */
    fun bind(client: Minecraft, translationKey: String, glfwKey: Int) {
        val binding = client.options.keyMappings.first { it.name == translationKey }
        binding.setKey(InputConstants.getKey(KeyEvent(glfwKey, 0, 0)))
        KeyMapping.resetMapping()
    }

    fun setRenderDistance(client: Minecraft, chunks: Int) {
        client.options.renderDistance().set(chunks)
    }

    /** The simple class name of the open screen (e.g. "TitleScreen"), or null when none is open. */
    fun screenName(client: Minecraft): String? = ScreenCompat.current()?.javaClass?.simpleName

}
