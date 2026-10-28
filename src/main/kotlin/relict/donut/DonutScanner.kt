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
    private const val TICK_BUDGET_NANOS = 2_500_000L
    private const val REFRESH_TICKS = 20
    private const val DROP_SCAN_TICKS = 40
    private const val REPORT_TICKS = 1200L

    /** Changes in the chunks this close to you are most likely your own doing (building, digging, flying about), so they are not read as another player. */
    private const val OWN_REACH_CHUNKS = 2
    const val LIVE_WINDOW_TICKS = 600L // a change counts as "live" for 30 seconds

    private class Tracked(var snapshot: ChunkSnapshot, var activity: Int, var switched: Map<Int, Int>) {
        /** When something player-made changed here, and when a redstone part switched. */
        val changes = ArrayDeque<Long>()
        val redstoneChanges = ArrayDeque<Long>()
    }

    private val owners = LinkedHashMap<Any, Int>() // module -> radius it wants
    private val tracked = HashMap<Long, Tracked>()
    private var queue: List<Long> = emptyList()
    private var cursor = 0
    private var dimension: String? = null
    private var now = 0L
    private var lastRefresh = -REFRESH_TICKS.toLong() // so the very first tick refreshes
    private var lastDropScan = -DROP_SCAN_TICKS.toLong()
    private var drops: Map<Long, Int> = emptyMap() // farm drops lying in each chunk

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
        lastDropScan = -DROP_SCAN_TICKS.toLong()
        drops = emptyMap()
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

        if (now % REPORT_TICKS == 0L) report(player.y)

        if (now - lastDropScan >= DROP_SCAN_TICKS) {
            lastDropScan = now
            drops = game.droppedItems().filter { FarmDrops.isFarmDrop(it.id) }
                .groupBy { key(Math.floorDiv(Math.floor(it.x).toInt(), 16), Math.floorDiv(Math.floor(it.z).toInt(), 16)) }
                .mapValues { (_, items) -> items.sumOf { it.count } }
        }

        // Reading a chunk's block counts is the main cost of this module, so a tick gets a time budget as well as a chunk count:
        // when the game is busy the scan simply goes a little slower instead of making that tick long.
        val deadline = System.nanoTime() + TICK_BUDGET_NANOS
        var done = 0
        while (done < minOf(CHUNKS_PER_TICK, queue.size) && System.nanoTime() < deadline) {
            val k = queue[cursor % queue.size]
            cursor = (cursor + 1) % queue.size
            scan(k, nearPlayer = kotlin.math.abs(keyX(k) - pcx) <= OWN_REACH_CHUNKS && kotlin.math.abs(keyZ(k) - pcz) <= OWN_REACH_CHUNKS)
            done++
        }
    }

    /**
     * A line in the log saying how much of the underground the server has sent: how many chunks have sections under Y 0, and how
     * deep the lowest ones go. It is how to tell what a server really sends while you are above or below Y 0.
     */
    private fun report(playerY: Double) {
        val lowest = tracked.values.map { t -> t.snapshot.sections.minOfOrNull { it.minY } }
        val under = lowest.count { it != null && it < 0 }
        val histogram = lowest.filterNotNull().groupingBy { it }.eachCount().toSortedMap().entries.joinToString(" ") { "${it.key}:${it.value}" }
        relict.RelictClient.logger.info("[scan] player Y {}: {} chunks tracked, {} with sections under Y 0, {} with none; lowest section Y (count): {}", playerY.toInt(), lowest.size, under, lowest.size - under, histogram)
    }

    private fun sq(v: Int) = v.toLong() * v

    private fun scan(k: Long, nearPlayer: Boolean) {
        val snapshot = GameHolder.game.snapshotChunk(keyX(k), keyZ(k)) ?: return
        val existing = tracked[k]
        if (existing == null) {
            tracked[k] = Tracked(snapshot, snapshot.activityFingerprint, switchedBySection(snapshot))
            return
        }
        val activity = snapshot.activityFingerprint
        val switched = switchedBySection(snapshot)
        // Changes in the chunks you are standing near are most likely your own digging and building. And when the sections you
        // can see change (the server stops sending the underground when you go above Y 0, and sends it again when you go down),
        // only the sections that were there both times are compared, so that is not read as someone building.
        if (!nearPlayer) {
            val common = comparableSections(existing.snapshot, snapshot)
            if (activity != existing.activity && existing.snapshot.activityFingerprintOver(common) != snapshot.activityFingerprintOver(common)) existing.changes.addLast(now)
            // Redstone switching on and off is a machine running: someone built it, and it is working now.
            if (snapshot.hasRedstone && common.any { existing.switched[it] != switched[it] }) existing.redstoneChanges.addLast(now)
        }
        existing.activity = activity
        existing.switched = switched
        // Keep the old snapshot when nothing changed, so everything downstream can tell (by identity) that there is nothing new to work out.
        if (snapshot.fingerprint != existing.snapshot.fingerprint) existing.snapshot = snapshot
    }

    /** How many redstone parts are switched on in each section (by the section's bottom), for spotting a machine at work. */
    private fun switchedBySection(snapshot: ChunkSnapshot): Map<Int, Int> = snapshot.sections.associate { it.minY to it.active }

    private fun comparableSections(old: ChunkSnapshot, new: ChunkSnapshot): Set<Int> {
        val oldYs = old.sections.mapTo(HashSet()) { it.minY }
        return new.sections.mapNotNullTo(HashSet()) { s -> s.minY.takeIf { it in oldYs } }
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

    /** How many times a redstone part switched in this chunk in the live window. */
    @Synchronized
    fun redstoneSignals(chunkX: Int, chunkZ: Int): Int {
        val t = tracked[key(chunkX, chunkZ)] ?: return 0
        while (t.redstoneChanges.isNotEmpty() && now - t.redstoneChanges.first() > LIVE_WINDOW_TICKS) t.redstoneChanges.removeFirst()
        return t.redstoneChanges.size
    }

    /** How many farm drops (what a farm's machines make) lie on the ground in this chunk. */
    @Synchronized
    fun farmDrops(chunkX: Int, chunkZ: Int): Int = drops[key(chunkX, chunkZ)] ?: 0

    /** Chunks that changed recently: blocks built or a machine running. The number is how many signals. */
    @Synchronized
    fun activeChunks(): List<Triple<Int, Int, Int>> = tracked.keys.mapNotNull { k ->
        val n = liveSignals(keyX(k), keyZ(k)) + redstoneSignals(keyX(k), keyZ(k))
        if (n > 0) Triple(keyX(k), keyZ(k), n) else null
    }

    val trackedCount: Int @Synchronized get() = tracked.size
}
