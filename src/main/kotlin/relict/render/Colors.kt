package relict.render

object Colors {
    fun argb(alpha: Int, rgb: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    const val WHITE = 0xFFFFFFFF.toInt()
    const val MUTED = 0xFF8B92A7.toInt()
    const val PANEL = 0xC00E1016.toInt()
    const val PANEL_EDGE = 0xFF272C3B.toInt()
    const val ACCENT = 0xFF4C7DFF.toInt()
    const val GOOD = 0xFF3ECF8E.toInt()
    const val WARN = 0xFFF5B73D.toInt()
    const val BAD = 0xFFFF6B6B.toInt()

    /** Region group colours, as in the server's own map legend. */
    fun group(name: String?): Int = when (name) {
        "EU Central" -> 0xFF9CCC65.toInt()
        "EU West" -> 0xFF2EAA60.toInt()
        "NA East" -> 0xFF40C4FF.toInt()
        "NA West" -> 0xFF316AE2.toInt()
        "Asia" -> 0xFFFFC107.toInt()
        "Oceania" -> 0xFFFF9800.toInt()
        else -> 0xFF3A3F52.toInt() // not known yet
    }
}
