package relict.modules

import relict.core.Module
import relict.donut.AnalysisSettings
import relict.donut.BaseFinderEngine
import relict.donut.Compass
import relict.donut.DonutScanner
import relict.donut.Kind
import relict.game.GameHolder

/**
 * Tells you when something turns up, with a notification in the bottom right corner: a new stash, farm or base the finder is
 * sure enough about, a chunk where blocks changed (a player may be there) and players the game has loaded around you.
 * It is a module of its own so the other modules can stay silent; it reads the same loaded chunks and sends nothing.
 */
class NotifierModule : Module(
    "notifier", "Notifier",
    "Pops up a notification when it finds a stash, farm or base, or when a player may be nearby.",
) {
    val bases = settings.bool("bases", "Stashes and bases", true, "Notify about stashes, machines and bases.")
    val farms = settings.bool("farms", "Farms", true, "Notify about farms.")
    val players = settings.bool("players", "Players", true, "Notify when something was built or a machine switched in a chunk (a player may be there) and when a player is loaded near you.")
    val minConfidence = settings.int("minConfidence", "Minimum chance (%)", 90, 90..99, "Only tell me about finds at least this likely. It never goes below 90%, so a notification means something.")
    val gap = settings.int("gap", "Time between notifications (seconds)", 4, 1..60, "Waits at least this long after one before showing the next.")
    val radius = settings.int("radius", "Scan radius (chunks)", 12, 2..32, "How far around you to look.")

    private val engine = BaseFinderEngine().also { it.maxNewPerUpdate = 4 }
    private var ticks = 0L
    private var lastShown = Long.MIN_VALUE / 2

    /** Chunks already announced, with the chance they were announced at, so a find is announced once. */
    private val told = HashMap<Long, Int>()
    private val playerTold = HashMap<String, Long>()

    override fun onEnable() = DonutScanner.acquire(this, radius.value)

    override fun onDisable() {
        DonutScanner.release(this)
        engine.reset()
        told.clear()
        playerTold.clear()
    }

    override fun onTick() {
        DonutScanner.setRadius(this, radius.value)
        DonutScanner.tick(this)
        if (++ticks % REFRESH_TICKS != 0L) return
        val game = GameHolder.game
        val me = game.player() ?: return

        val entries = engine.update(
            DonutScanner.snapshots(), DonutScanner::liveSignals, DonutScanner.playerX, DonutScanner.playerZ,
            AnalysisSettings(320, MIN_SCORE),
            me.dimension,
            DonutScanner::redstoneSignals, DonutScanner::farmDrops,
        )
        for (e in entries.sortedByDescending { it.result.confidence }) {
            val r = e.result
            if (r.kind == Kind.EXCAVATED || r.confidence < maxOf(MIN_CONFIDENCE, minConfidence.value)) continue
            val farm = r.kind == Kind.FARM
            if (if (farm) !farms.value else !bases.value) continue
            val key = (r.chunkX.toLong() shl 32) or (r.chunkZ.toLong() and 0xFFFFFFFFL)
            if (key in told) continue // announced once; it stays in the Base Finder list
            if (!room()) break
            told[key] = r.confidence
            val where = "${e.centerX} ${e.centerZ}"
            val title = when (r.kind) {
                Kind.STASH -> "Stash found"
                Kind.FARM -> "Farm found"
                Kind.MACHINE -> "Machine found"
                Kind.DUG_SHAFT -> "Dug shaft found"
                else -> "Possible base"
            }
            show(title, "${r.confidence}% · ${e.direction} ${e.distance}m · $where", if (farm) "farm" else "base")
        }

        if (!players.value) return
        // Blocks changed in a chunk: a player may be standing there (it is only a hint, so it says "possible").
        for ((cx, cz, _) in DonutScanner.activeChunks()) {
            val key = "chunk:$cx,$cz"
            if (ticks - (playerTold[key] ?: Long.MIN_VALUE / 2) < PLAYER_REPEAT_TICKS) continue
            if (!room()) break
            playerTold[key] = ticks
            val x = cx * 16 + 8
            val z = cz * 16 + 8
            show("Possible player", "$x $z · ${Compass.direction(x - me.x, z - me.z)} ${Compass.distance(x - me.x, z - me.z)}m · building or machines at work", "player")
        }
        // A player the game has actually loaded near you is certain, not a hint.
        for (p in game.otherPlayers()) {
            val key = "name:${p.name}"
            if (ticks - (playerTold[key] ?: Long.MIN_VALUE / 2) < PLAYER_REPEAT_TICKS) continue
            if (!room()) break
            playerTold[key] = ticks
            show("Player nearby", "${p.name} · ${Compass.direction(p.x - me.x, p.z - me.z)} ${Compass.distance(p.x - me.x, p.z - me.z)}m", "alert")
        }
    }

    /** True if enough time has passed since the last notification; the rest wait for the next scan. */
    private fun room(): Boolean = ticks - lastShown >= gap.value * 20L / REFRESH_TICKS

    private fun show(title: String, message: String, tone: String) {
        lastShown = ticks
        GameHolder.game.showNotification(title, message, tone)
    }

    private companion object {
        const val REFRESH_TICKS = 5L
        const val MIN_SCORE = 20
        const val MIN_CONFIDENCE = 90
        const val PLAYER_REPEAT_TICKS = 60L * 20 / REFRESH_TICKS // a minute, in scans
    }
}
