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
    private class Cached(val snapshot: ChunkSnapshot, val signals: Signals, val maxY: Int, val minScore: Int, val result: ChunkResult?)

    /** What is going on in a chunk right now, besides its blocks. */
    private data class Signals(val live: Int, val redstone: Int, val drops: Int)

    private val cache = HashMap<Pair<Int, Int>, Cached>()

    /** Chunks analysed for real in the current [update]; see [maxNewPerUpdate]. */
    private var analysedNow = 0

    /**
     * How many chunks one [update] may analyse from scratch. A chunk that has not changed is not analysed again, but the first
     * time a whole area is seen that is hundreds of chunks, which in one tick is a stutter. So only this many are done per update
     * and the rest follow on the next ones (callers update a few times a second), and a chunk waiting its turn is simply not listed yet.
     */
    var maxNewPerUpdate = Int.MAX_VALUE

    /** Null with [pending] set when the chunk has to wait. */
    private fun analyse(snapshot: ChunkSnapshot, settings: AnalysisSettings, signals: Signals, pending: BooleanArray): ChunkResult? {
        val key = snapshot.chunkX to snapshot.chunkZ
        val hit = cache[key]
        if (hit != null && hit.snapshot === snapshot && hit.signals == signals && hit.maxY == settings.maxY && hit.minScore == settings.minScore) return hit.result
        if (analysedNow >= maxNewPerUpdate) { pending[0] = true; return hit?.result }
        analysedNow++
        val result = BaseAnalyzer.analyze(snapshot, settings, signals.live, signals.redstone, signals.drops)
        cache[key] = Cached(snapshot, signals, settings.maxY, settings.minScore, result)
        return result
    }

    fun update(
        snapshots: List<ChunkSnapshot>,
        liveSignals: (chunkX: Int, chunkZ: Int) -> Int,
        playerX: Double,
        playerZ: Double,
        settings: AnalysisSettings,
        dimension: String = "minecraft:overworld",
        redstoneSignals: (chunkX: Int, chunkZ: Int) -> Int = { _, _ -> 0 },
        farmDrops: (chunkX: Int, chunkZ: Int) -> Int = { _, _ -> 0 },
    ): List<FinderEntry> {
        analysedNow = 0
        fun signalsOf(snap: ChunkSnapshot) = Signals(liveSignals(snap.chunkX, snap.chunkZ), redstoneSignals(snap.chunkX, snap.chunkZ), farmDrops(snap.chunkX, snap.chunkZ))
        val flagged = ArrayList<ChunkResult>()
        val noUnderground = HashSet<Pair<Int, Int>>()
        for (snap in snapshots) {
            val key = snap.chunkX to snap.chunkZ
            if (snap.sections.none { it.minY < 0 }) {
                // Nothing under Y 0 is loaded (the server sends none while you are above it): what is in view is judged as it is, and
                // the answer from when the underground was loaded is shown too, the likelier of the two.
                noUnderground += key
                val remembered = ChunkMemory.get(dimension, snap.chunkX, snap.chunkZ)?.copy(fromMemory = true)
                val inView = if (snap.sections.any { it.minY < settings.maxY }) analyse(snap, settings, signalsOf(snap), BooleanArray(1)) else null
                (if (remembered != null && (inView == null || remembered.score >= inView.score)) remembered else inView)?.let { flagged += it }
                continue
            }
            val pending = BooleanArray(1)
            val result = analyse(snap, settings, signalsOf(snap), pending)
            if (pending[0]) { if (result != null) flagged += result; continue } // waiting its turn: keep the last answer, change nothing
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
