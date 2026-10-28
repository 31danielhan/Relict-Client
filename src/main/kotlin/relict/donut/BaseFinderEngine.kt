package relict.donut

/** One line of the Base Finder list: a flagged chunk, as seen from where the player is. */
data class FinderEntry(
    val result: ChunkResult,
    val direction: String,
    /** Blocks from the player to the middle of the chunk. */
    val distance: Int,
    val centerX: Int,
    val centerZ: Int,
)

/**
 * Builds the Base Finder list from the scanner's chunk snapshots. It remembers the best score each chunk has had, so a
 * chunk that was clearly a base and has since been cleared out shows as Excavated instead of just vanishing.
 */
class BaseFinderEngine {
    private val peaks = HashMap<Pair<Int, Int>, Int>()

    /** The last verdict for each chunk and what it was based on, so a chunk that has not changed is not analysed again. */
    private class Cached(val snapshot: ChunkSnapshot, val live: Int, val maxY: Int, val minScore: Int, val result: ChunkResult?)

    private val cache = HashMap<Pair<Int, Int>, Cached>()

    private fun analyse(snapshot: ChunkSnapshot, settings: AnalysisSettings, live: Int): ChunkResult? {
        val key = snapshot.chunkX to snapshot.chunkZ
        val hit = cache[key]
        if (hit != null && hit.snapshot === snapshot && hit.live == live && hit.maxY == settings.maxY && hit.minScore == settings.minScore) return hit.result
        val result = BaseAnalyzer.analyze(snapshot, settings, live)
        cache[key] = Cached(snapshot, live, settings.maxY, settings.minScore, result)
        return result
    }

    fun update(
        snapshots: List<ChunkSnapshot>,
        liveSignals: (chunkX: Int, chunkZ: Int) -> Int,
        playerX: Double,
        playerZ: Double,
        settings: AnalysisSettings,
        dimension: String = "minecraft:overworld",
    ): List<FinderEntry> {
        val flagged = ArrayList<ChunkResult>()
        val noUnderground = HashSet<Pair<Int, Int>>()
        for (snap in snapshots) {
            val key = snap.chunkX to snap.chunkZ
            if (snap.sections.none { it.minY < settings.maxY }) {
                // Nothing underground is loaded (you are above Y 0, where the server sends none): show what was worked out when it was.
                noUnderground += key
                ChunkMemory.get(dimension, snap.chunkX, snap.chunkZ)?.let { flagged += it.copy(fromMemory = true) }
                continue
            }
            val result = analyse(snap, settings, liveSignals(snap.chunkX, snap.chunkZ))
            if (result != null) { flagged += result; ChunkMemory.put(dimension, result) } else ChunkMemory.remove(dimension, snap.chunkX, snap.chunkZ)
        }
        cache.keys.retainAll(snapshots.map { it.chunkX to it.chunkZ }.toHashSet()) // forget chunks that are no longer loaded
        val results = BaseAnalyzer.withNeighbourBonus(flagged).associateBy { it.chunkX to it.chunkZ }.toMutableMap()

        for (r in results.values) peaks.merge(r.chunkX to r.chunkZ, r.score, ::maxOf)

        // A chunk that was clearly a base and is now mostly empty is "Excavated": someone cleared it out.
        for (snapshot in snapshots) {
            val key = snapshot.chunkX to snapshot.chunkZ
            if (key in noUnderground) continue // an unloaded underground is not an emptied one
            val peak = peaks[key] ?: continue
            val current = results[key]?.score ?: 0
            if (peak >= EXCAVATED_MIN_PEAK && current * 2 < peak) {
                val window = snapshot.sections.filter { it.minY < settings.maxY }
                results[key] = ChunkResult(
                    chunkX = key.first, chunkZ = key.second, kind = Kind.EXCAVATED, score = current,
                    confidence = (100 * current / peak).coerceIn(5, 90),
                    evidence = listOf(Evidence(0, "was $peak, now $current")),
                    evidenceSections = 0, totalSections = window.size,
                    minY = window.minOfOrNull { it.minY } ?: 0, maxY = window.maxOfOrNull { it.maxY } ?: 0,
                )
            }
        }

        return results.values.map { r ->
            val cx = r.chunkX * 16 + 8
            val cz = r.chunkZ * 16 + 8
            val dx = cx - playerX
            val dz = cz - playerZ
            FinderEntry(r, Compass.direction(dx, dz), Compass.distance(dx, dz), cx, cz)
        }.sortedWith(compareBy({ it.distance }, { -it.result.score }))
    }

    fun reset() {
        peaks.clear()
        cache.clear()
    }

    private companion object {
        const val EXCAVATED_MIN_PEAK = 60
    }
}
