package relict.donut

/** How many blocks of each type are in one 16 x 16 x 16 section. Gathered from the section's palette, so it is cheap. */
class SectionSnapshot(val minY: Int, val counts: Map<String, Int>) {
    val maxY: Int get() = minY + 15
}

/** What the analysis needs to know about one loaded chunk. */
class ChunkSnapshot(val chunkX: Int, val chunkZ: Int, val sections: List<SectionSnapshot>) {
    /**
     * Changes when player-made blocks or air pockets change: a cheap "did someone build or dig here" check.
     * Stone, water and crops are left out so natural changes don't read as a player.
     */
    val activityFingerprint: Int by lazy {
        var h = 19
        for (s in sections) {
            h = h * 31 + s.minY
            for ((id, n) in s.counts.entries.sortedBy { it.key }) {
                if (BlockCategories.of(id) != null || id == "minecraft:air" || id == "minecraft:cave_air") h = (h * 31 + id.hashCode()) * 31 + n
            }
        }
        h
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
