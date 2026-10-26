package relict.render

/** Drawing in the world and on the HUD isn't implemented for this Minecraft version yet: the modules report it and still list their findings in the GUI. */
object RenderHooks {
    fun install() {
        Render.overlaysSupported = false
        Render.hudSupported = false
    }
}
