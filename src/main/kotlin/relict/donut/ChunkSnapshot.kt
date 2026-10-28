package relict.donut

/** How many blocks of each type are in one 16 x 16 x 16 section. Gathered from the section's palette, so it is cheap. */
class SectionSnapshot(val minY: Int, val counts: Map<String, Int>) {
    val maxY: Int get() = minY + 15
}

/** What the analysis needs to know about one loaded chunk. */
class ChunkSnapshot(val chunkX: Int, val chunkZ: Int, val sections: List<SectionSnapshot>) {
    /**
     * Changes when player-made blocks change or something big is dug out or filled in: a cheap "did someone build or dig
     * here" check. Plants, water and everything else that changes by itself (kelp and grass growing, dripstone, leaves)
     * is left out, and the amount of air underground is rounded off, so a fresh world's own changes do not read as a player.
     */
    val activityFingerprint: Int by lazy { activityFingerprintOver(null) }

    /** The same, over only the sections whose bottom is in [minYs] (null means all of them). */
    fun activityFingerprintOver(minYs: Set<Int>?): Int {
        var h = 19
        for (s in sections) {
            if (minYs != null && s.minY !in minYs) continue
            h = h * 31 + s.minY
            var air = 0
            for ((id, n) in s.counts.entries.sortedBy { it.key }) {
                when {
                    id == "minecraft:air" || id == "minecraft:cave_air" -> air += n
                    BlockCategories.of(id) != null || BlockCategories.isPlayerMade(id) -> h = (h * 31 + id.hashCode()) * 31 + n
                }
            }
            if (s.minY < 0) h = h * 31 + air / AIR_STEP // a few air blocks more or less underground is not digging
        }
        return h
    }

    private companion object {
        const val AIR_STEP = 48
    }

    /** Changes whenever any block count in the chunk changes. */
    val fingerprint: Int by lazy {
        var h = 17
        for (s in sections) {
            h = h * 31 + s.minY
            for ((id, n) in s.counts.entries.sortedBy { it.key }) h = (h * 31 + id.hashCode()) * 31 + n
        }
        h
    }
}

/** A block the overlays want to draw. */
data class BlockPos3(val x: Int, val y: Int, val z: Int) {
    fun distanceSq(px: Double, py: Double, pz: Double): Double {
        val dx = x + 0.5 - px
        val dy = y + 0.5 - py
        val dz = z + 0.5 - pz
        return dx * dx + dy * dy + dz * dz
    }
}

data class BlockRef(val id: String, val pos: BlockPos3)
