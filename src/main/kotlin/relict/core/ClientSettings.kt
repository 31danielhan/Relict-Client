package relict.core

/** The client's own settings (as opposed to a module's), shown on the UI's Settings page. */
object ClientSettings {
    enum class Accent { BLUE, GREEN, PURPLE, ORANGE }

    const val ID = "client"

    val group = SettingGroup()
    val accent = group.enum("accent", "Accent colour", Accent.BLUE)
    val reducedMotion = group.bool("reducedMotion", "Reduce motion", false, "Turns off UI animations.")
}
