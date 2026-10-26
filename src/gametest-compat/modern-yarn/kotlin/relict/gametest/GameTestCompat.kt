package relict.gametest

import net.minecraft.client.MinecraftClient
import net.minecraft.client.input.KeyInput
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil

/** Test helpers whose Minecraft API differs between versions (1.21.9 - 1.21.11, Yarn names). */
object GameTestCompat {
    /** Rebinds the key binding with this translation key to a GLFW key. */
    fun bind(client: MinecraftClient, translationKey: String, glfwKey: Int) {
        val binding = client.options.allKeys.first { it.id == translationKey }
        binding.setBoundKey(InputUtil.fromKeyCode(KeyInput(glfwKey, 0, 0)))
        KeyBinding.updateKeysByCode()
    }

    fun setRenderDistance(client: MinecraftClient, chunks: Int) {
        client.options.viewDistance.value = chunks
    }

    /** The simple class name of the open screen (e.g. "TitleScreen"), or null when none is open. */
    fun screenName(client: MinecraftClient): String? = client.currentScreen?.javaClass?.simpleName

}
