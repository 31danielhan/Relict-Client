package relict.core

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil

object Keys {
    /** Shown as "Relict" in Controls; before 1.21.9 a category is just its translation key (see the lang file). */
    private const val CATEGORY = "key.category.relict.main"

    fun register(translationKey: String, defaultKey: Int = KeyCodes.UNBOUND): KeyHandle {
        val binding = KeyBindingHelper.registerKeyBinding(KeyBinding(translationKey, InputUtil.Type.KEYSYM, defaultKey, CATEGORY))
        return KeyHandle(binding, binding::wasPressed) { binding.boundKeyLocalizedText.string }
    }
}
