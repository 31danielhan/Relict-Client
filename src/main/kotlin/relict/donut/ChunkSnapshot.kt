package relict.donut

/**
 * How many blocks of each type are in one 16 x 16 x 16 section. Gathered from the section's palette, so it is cheap.
 * [active] is how many redstone parts in it are switched on right now (powered, lit, extended...): it only matters when it changes.
 */
class SectionSnapshot(val minY: Int, val counts: Map<String, Int>, val active: Int = 0) {
    val maxY: Int get() = minY + 15
}

/** What the analysis needs to know about one loaded chunk. */
class ChunkSnapshot(val chunkX: Int, val chunkZ: Int, val sections: List<SectionSnapshot>) {
    /**
     * Changes when player-made blocks change: a cheap "did someone build here" check. Plants, water, air and everything
     * else that changes by itself (kelp and grass growing, dripstone, leaves, mobs, explosions, flowing water) is left out,
     * so a fresh world's own changes do not read as a player.
     */
    val activityFingerprint: Int by lazy { activityFingerprintOver(null) }

    /** The same, over only the sections whose bottom is in [minYs] (null means all of them). */
    fun activityFingerprintOver(minYs: Set<Int>?): Int {
        var h = 19
        for (s in sections) {
            if (minYs != null && s.minY !in minYs) continue
            h = h * 31 + s.minY
            for ((id, n) in s.counts.entries.sortedBy { it.key }) {
                if (BlockCategories.of(id) != null || BlockCategories.isPlayerMade(id)) h = (h * 31 + id.hashCode()) * 31 + n
            }
        }
        return h
    }

    /** True if the chunk holds any redstone part at all (so a change in what is switched on can mean something). */
    val hasRedstone: Boolean by lazy { sections.any { s -> s.counts.keys.any { BlockCategories.of(it).let { c -> c == Category.REDSTONE || c == Category.MACHINE } } } }

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
