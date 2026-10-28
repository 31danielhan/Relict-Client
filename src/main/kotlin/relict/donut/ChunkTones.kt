package relict.donut

/**
 * Chunk ESP's fixed colours and its fade with height. The outline's colour is the chance, from [relict.render.ConfidenceColor]
 * (orange-red unlikely, yellow kind of likely, green likely, blue at 99%); purple means a player may be in the chunk.
 */
object ChunkTones {
    const val PURPLE = 0xAB47BC

    /** The outline sits on the world's floor: Y -64 in the overworld, 0 in the Nether and the End. */
    fun floorY(dimension: String): Double = if (dimension == "minecraft:overworld") -64.0 else 0.0

    private const val FULL_ABOVE = 10.0
    private const val FAINT_BELOW = -30.0
    private const val FAINTEST = 0.15f

    /**
     * How strong the outlines are for a player at this height. They are bright from far above, where you need them to
     * find a base, and fade as you get close to Y -30, where you are inside the base and would only be looking at them.
     */
    fun strength(playerY: Double): Float {
        if (playerY >= FULL_ABOVE) return 1f
        if (playerY <= FAINT_BELOW) return FAINTEST
        val t = ((playerY - FAINT_BELOW) / (FULL_ABOVE - FAINT_BELOW)).toFloat()
        return FAINTEST + (1f - FAINTEST) * t
    }
}
