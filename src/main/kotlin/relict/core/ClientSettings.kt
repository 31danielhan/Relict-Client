package relict.core

/** The client's own settings (as opposed to a module's), shown on the UI's Settings page. */
object ClientSettings {
    enum class Accent { PURPLE, BLUE, GREEN, ORANGE }

    const val ID = "client"

    val group = SettingGroup()
    val accent = group.enum("accent", "Accent colour", Accent.PURPLE)
    val hudScale = group.int("hudScale", "HUD size (%)", 100, 50..200, "How large the region map, base finder and pop-ups are.")
    val reducedMotion = group.bool("reducedMotion", "Reduce motion", false, "Turns off UI animations.")
}
