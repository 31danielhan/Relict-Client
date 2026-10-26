package relict.core

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import relict.RelictClient

object Keys {
    /** Shown as "Relict" in Controls; the label comes from key.category.relict.main in the lang file. */
    val category: KeyBinding.Category = KeyBinding.Category.create(Identifier.of(RelictClient.MOD_ID, "main"))

    fun register(translationKey: String, defaultKey: Int = KeyCodes.UNBOUND): KeyHandle {
        val binding = KeyBindingHelper.registerKeyBinding(KeyBinding(translationKey, InputUtil.Type.KEYSYM, defaultKey, category))
        return KeyHandle(binding, binding::wasPressed) { binding.boundKeyLocalizedText.string }
    }
}
