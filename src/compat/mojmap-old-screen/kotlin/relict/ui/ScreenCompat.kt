package relict.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.toasts.ToastManager
import net.minecraft.network.chat.Component

/** Where the screen, toasts and action-bar message live differs between 26.x versions (this is 26.1: on Minecraft). */
object ScreenCompat {
    private val client: Minecraft get() = Minecraft.getInstance()

    fun current(): Screen? = client.screen

    fun set(screen: Screen?) = client.setScreen(screen)

    fun toastManager(): ToastManager = client.toastManager

    fun showOverlayMessage(text: Component) = client.gui.setOverlayMessage(text, false)
}
