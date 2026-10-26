package relict

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.gui.screen.GameMenuScreen
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen
import net.minecraft.client.gui.screen.world.SelectWorldScreen
import net.minecraft.client.option.KeyBinding
import relict.core.KeyHandle

/**
 * In a world with no menu open, Minecraft delivers a bound key as a key-binding press. Menus swallow key
 * bindings, so the open key is also caught on the few menus where that is safe (not ones with text fields,
 * where Right Shift is needed for capital letters). This is the pre-1.21.9 version (keys arrive as ints).
 */
object ScreenKeys {
    fun install(key: KeyHandle, onPress: () -> Unit) {
        val binding = key.native as KeyBinding
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is TitleScreen || screen is GameMenuScreen || screen is MultiplayerScreen || screen is SelectWorldScreen) {
                ScreenKeyboardEvents.allowKeyPress(screen).register { _, key, scancode, _ ->
                    if (binding.matchesKey(key, scancode)) {
                        onPress()
                        false
                    } else {
                        true
                    }
                }
            }
        }
    }
}
