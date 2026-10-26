package relict.gametest

import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil

/** Test helpers whose Minecraft API differs between versions (1.21.4 and older, Yarn names). */
object GameTestCompat {
    /** Rebinds the key binding with this translation key to a GLFW key. */
    fun bind(client: MinecraftClient, translationKey: String, glfwKey: Int) {
        val binding = client.options.allKeys.first { it.translationKey == translationKey }
        binding.setBoundKey(InputUtil.fromKeyCode(glfwKey, 0))
        KeyBinding.updateKeysByCode()
    }

    fun setRenderDistance(client: MinecraftClient, chunks: Int) {
        client.options.viewDistance.value = chunks
    }

    /** The simple class name of the open screen (e.g. "TitleScreen"), or null when none is open. */
    fun screenName(client: MinecraftClient): String? = client.currentScreen?.javaClass?.simpleName

}
