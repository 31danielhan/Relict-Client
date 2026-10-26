package relict.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.toasts.ToastManager
import net.minecraft.network.chat.Component

/** Where the screen, toasts and action-bar message live differs between 26.x versions (this is 26.2 and newer: on Gui and Hud). */
object ScreenCompat {
    private val client: Minecraft get() = Minecraft.getInstance()

    fun current(): Screen? = client.gui.screen()

    fun set(screen: Screen?) = client.gui.setScreen(screen)

    fun toastManager(): ToastManager = client.gui.toastManager()

    fun showOverlayMessage(text: Component) = client.gui.hud.setOverlayMessage(text, false)
}
