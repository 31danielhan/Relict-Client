package relict.donut

import relict.game.GameHolder

/**
 * Watches the chunks the client has loaded around the player and remembers each one's block counts, so the Donut
 * modules can analyse them and notice when something changed. It only ever reads data the server has already sent.
 *
 * Several modules share one scanner: each [acquire]s it while enabled, and only the first of them drives it.
 */
object DonutScanner {
    private const val CHUNKS_PER_TICK = 6
    private const val REFRESH_TICKS = 20
    const val LIVE_WINDOW_TICKS = 600L // a change counts as "live" for 30 seconds

    private class Tracked(var snapshot: ChunkSnapshot, var activity: Int) {
        val changes = ArrayDeque<Long>()
    }

    private val owners = LinkedHashMap<Any, Int>() // module -> radius it wants
    private val tracked = HashMap<Long, Tracked>()
    private var queue: List<Long> = emptyList()
    private var cursor = 0
    private var dimension: String? = null
    private var now = 0L
    private var lastRefresh = -REFRESH_TICKS.toLong() // so the very first tick refreshes

    /** Where the player was at the last tick, in blocks. */
    var playerX = 0.0
        private set
    var playerZ = 0.0
        private set

    private fun key(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
    private fun keyX(k: Long) = (k shr 32).toInt()
    private fun keyZ(k: Long) = k.toInt()

    @Synchronized
    fun acquire(owner: Any, radius: Int) {
        owners[owner] = radius
    }

    @Synchronized
    fun release(owner: Any) {
        owners.remove(owner)
        if (owners.isEmpty()) reset()
    }

    @Synchronized
    fun setRadius(owner: Any, radius: Int) {
        if (owner in owners) owners[owner] = radius
    }

    private fun reset() {
        tracked.clear()
        queue = emptyList()
        cursor = 0
        dimension = null
        lastRefresh = -REFRESH_TICKS.toLong()
    }

    /** Call once per game tick from the module that owns the scanner; other callers are ignored. */
    @Synchronized
    fun tick(owner: Any) {
        if (owner !== owners.keys.firstOrNull()) return
        val game = GameHolder.game
        val player = game.player()
        if (player == null) {
            reset()
            return
        }
        now++
        if (player.dimension != dimension) {
            reset()
            dimension = player.dimension
        }
        playerX = player.x
        playerZ = player.z
        val radius = owners.values.max()
        val pcx = player.blockX shr 4
        val pcz = player.blockZ shr 4

        if (now - lastRefresh >= REFRESH_TICKS) {
            lastRefresh = now
            val loaded = ArrayList<Long>()
            for (dz in -radius..radius) for (dx in -radius..radius) {
                if (dx * dx + dz * dz <= radius * radius && game.isChunkLoaded(pcx + dx, pcz + dz)) loaded += key(pcx + dx, pcz + dz)
            }
            loaded.sortBy { k -> sq(keyX(k) - pcx) + sq(keyZ(k) - pcz) }
            queue = loaded
            cursor = 0
            val keep = loaded.toHashSet()
            tracked.keys.retainAll(keep)
        }

        repeat(minOf(CHUNKS_PER_TICK, queue.size)) {
            val k = queue[cursor % queue.size]
            cursor = (cursor + 1) % queue.size
            scan(k, nearPlayer = kotlin.math.abs(keyX(k) - pcx) <= 1 && kotlin.math.abs(keyZ(k) - pcz) <= 1)
        }
    }

    private fun sq(v: Int) = v.toLong() * v

    private fun scan(k: Long, nearPlayer: Boolean) {
        val snapshot = GameHolder.game.snapshotChunk(keyX(k), keyZ(k)) ?: return
        val existing = tracked[k]
        if (existing == null) {
            tracked[k] = Tracked(snapshot, snapshot.activityFingerprint)
            return
        }
        val activity = snapshot.activityFingerprint
        // Changes in the chunks you are standing next to are most likely your own digging and building.
        if (activity != existing.activity && !nearPlayer) existing.changes.addLast(now)
        existing.activity = activity
        existing.snapshot = snapshot
    }

    @Synchronized
    fun snapshots(): List<ChunkSnapshot> = tracked.values.map { it.snapshot }

    /** How many times this chunk changed in the live window. */
    @Synchronized
    fun liveSignals(chunkX: Int, chunkZ: Int): Int {
        val t = tracked[key(chunkX, chunkZ)] ?: return 0
        while (t.changes.isNotEmpty() && now - t.changes.first() > LIVE_WINDOW_TICKS) t.changes.removeFirst()
        return t.changes.size
    }

    /** Chunks that changed recently, newest information first. */
    @Synchronized
    fun activeChunks(): List<Triple<Int, Int, Int>> = tracked.keys.mapNotNull { k ->
        val n = liveSignals(keyX(k), keyZ(k))
        if (n > 0) Triple(keyX(k), keyZ(k), n) else null
    }

    val trackedCount: Int @Synchronized get() = tracked.size
}
