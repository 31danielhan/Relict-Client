package relict

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import relict.core.KeyHandle

/**
 * In a world with no menu open, Minecraft delivers a bound key as a key-binding press. Menus swallow key
 * bindings, so the open key is also caught on the few menus where that is safe (not ones with text fields,
 * where Right Shift is needed for capital letters). This is the Mojang-named (26.x) version.
 */
object ScreenKeys {
    fun install(key: KeyHandle, onPress: () -> Unit) {
        val mapping = key.native as KeyMapping
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is TitleScreen || screen is PauseScreen || screen is JoinMultiplayerScreen || screen is SelectWorldScreen) {
                ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
                    if (mapping.matches(event)) {
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
