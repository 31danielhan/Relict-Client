package relict.render

/**
 * The colour of a chance, used by the Base Finder list, the chunk outlines and the aim card so they agree: orange-red
 * when it is unlikely, yellow when it is kind of likely, green when it is likely and blue at 99%.
 * (The HUD page has the same stops in state.svelte.ts.)
 */
object ConfidenceColor {
    private val STOPS = listOf(
        0 to 0xFF5A36,
        30 to 0xFF9800,
        50 to 0xFFD83B,
        75 to 0x66BB6A,
        90 to 0x66BB6A,
        96 to 0x42A5F5,
    )

    /** 0xRRGGBB. */
    fun rgb(percent: Int): Int {
        val p = percent.coerceIn(0, 100)
        for (i in 1 until STOPS.size) {
            val (p1, c1) = STOPS[i]
            if (p <= p1) {
                val (p0, c0) = STOPS[i - 1]
                val t = (p - p0).toFloat() / (p1 - p0)
                fun mix(shift: Int): Int {
                    val a = (c0 shr shift) and 0xFF
                    val b = (c1 shr shift) and 0xFF
                    return Math.round(a + (b - a) * t)
                }
                return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
            }
        }
        return STOPS.last().second
    }

    /** Words for it, as shown on the aim card. */
    fun level(percent: Int): String = when {
        percent < 35 -> "LOW"
        percent < 75 -> "MEDIUM"
        else -> "HIGH"
    }
}
