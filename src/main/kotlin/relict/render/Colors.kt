package relict.render

object Colors {
    fun argb(alpha: Int, rgb: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    const val WHITE = 0xFFFFFFFF.toInt()
    const val MUTED = 0xFF8E93BD.toInt()
    const val PANEL = 0xD0090B2C.toInt()
    const val PANEL_EDGE = 0xFF2A2D7A.toInt()
    const val ACCENT = 0xFF8777FF.toInt()
    const val GOOD = 0xFF3ECF8E.toInt()
    const val WARN = 0xFFF5B73D.toInt()
    const val BAD = 0xFFFF6B6B.toInt()

    /** The area you are standing in. */
    const val HERE = 0xFFE8F4FF.toInt()

    /** Green for a good connection through yellow to red for a bad one. */
    fun ping(ms: Int): Int {
        val t = ((ms - 20) / 180f).coerceIn(0f, 1f)
        return 0xFF000000.toInt() or (java.awt.Color.HSBtoRGB((140f - 140f * t) / 360f, 0.62f, 0.78f) and 0xFFFFFF)
    }

    /** A muted colour of its own for each region number, so neighbouring regions can be told apart before you have visited them. */
    fun region(number: Int): Int =
        0xFF000000.toInt() or (java.awt.Color.HSBtoRGB(((number * 47) % 360) / 360f, 0.34f, 0.34f) and 0xFFFFFF)

    /** Region group colours, as in the server's own map legend. */
    fun group(name: String?): Int = when (name) {
        "EU Central" -> 0xFF9CCC65.toInt()
        "EU West" -> 0xFF2EAA60.toInt()
        "NA East" -> 0xFF40C4FF.toInt()
        "NA West" -> 0xFF316AE2.toInt()
        "Asia" -> 0xFFFFC107.toInt()
        "Oceania" -> 0xFFFF9800.toInt()
        else -> 0xFF2A2E5C.toInt() // not known yet
    }
}
