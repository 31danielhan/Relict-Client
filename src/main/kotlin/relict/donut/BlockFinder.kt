package relict.donut

import relict.game.Game

/**
 * Finds block positions in loaded chunks, remembering each chunk's answer until the chunk changes. Looking blocks up is
 * far more work than counting them, so this only searches chunks that the counts say contain something.
 */
class BlockFinder {
    private data class Cached(val fingerprint: Int, val query: Int, val blocks: List<BlockRef>)

    private val cache = HashMap<Pair<Int, Int>, Cached>()

    fun find(game: Game, snapshot: ChunkSnapshot, ids: Set<String>, minY: Int, maxY: Int): List<BlockRef> {
        // Only look if this chunk has at least one of the wanted blocks in a section within range.
        val present = snapshot.sections.any { s -> s.minY <= maxY && s.maxY >= minY && s.counts.keys.any { it in ids } }
        val key = snapshot.chunkX to snapshot.chunkZ
        if (!present) {
            cache.remove(key)
            return emptyList()
        }
        val query = ids.hashCode() * 31 + minY * 7 + maxY
        cache[key]?.let { if (it.fingerprint == snapshot.fingerprint && it.query == query) return it.blocks }
        val blocks = game.findBlocks(snapshot.chunkX, snapshot.chunkZ, minY, maxY, ids)
        cache[key] = Cached(snapshot.fingerprint, query, blocks)
        return blocks
    }

    /** Forget chunks that are no longer being tracked. */
    fun retain(keep: Set<Pair<Int, Int>>) {
        cache.keys.retainAll(keep)
    }

    fun clear() = cache.clear()
}
