package relict.modules

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import relict.RelictClient
import relict.core.ApiException
import relict.core.CoverageGrid
import relict.core.Module
import relict.core.ProfileStore
import relict.game.GameHolder
import java.util.Base64

/**
 * Remembers which chunks you have been near, so you can see what is left to search.
 * Only chunks your own game has loaded around you are counted; nothing here sees anything the game doesn't already show.
 */
class CoverageModule : Module(
    "coverage", "Search Coverage",
    "Records the ground you have already flown over so you never search the same area twice.",
) {
    val maxRadius = settings.int(
        "maxRadius", "Max radius (chunks)", 32, 2..32,
        "Never count chunks further than this from you. Only chunks your game has loaded are counted either way.",
    )

    private var store: ProfileStore? = null
    private val grids = HashMap<String, CoverageGrid>()
    private val dirty = HashSet<String>()

    private var ticks = 0
    private var lastScan: List<Any>? = null

    override fun onProfileOpen(store: ProfileStore) {
        this.store = store
        grids.clear()
        dirty.clear()
        lastScan = null
    }

    override fun onProfileClose(store: ProfileStore) {
        flush()
        this.store = null
        grids.clear()
    }

    override fun onDisable() = flush()

    override fun onTick() {
        if (++ticks % SCAN_INTERVAL != 0) return
        val game = GameHolder.game
        val player = game.player() ?: return
        if (store == null) return

        val dim = player.dimension
        val cx = player.blockX shr 4
        val cz = player.blockZ shr 4
        val signature = listOf(dim, cx, cz, maxRadius.value, game.loadedChunkCount())
        if (signature != lastScan) {
            lastScan = signature
            val added = grid(dim).markDisc(cx, cz, maxRadius.value, game::isChunkLoaded)
            if (added > 0) dirty += dim
        }
        if (ticks % SAVE_INTERVAL == 0) flush()
    }

    private fun fileFor(dim: String) = "coverage-${dim.replace(':', '_').replace('/', '_')}.bin"

    private fun dimFromFile(name: String) = name.removePrefix("coverage-").removeSuffix(".bin").replaceFirst('_', ':')

    private fun grid(dim: String): CoverageGrid = grids.getOrPut(dim) {
        val s = store ?: return@getOrPut CoverageGrid()
        val bytes = s.readBytes(fileFor(dim)) ?: return@getOrPut CoverageGrid()
        try {
            CoverageGrid.fromBytes(bytes)
        } catch (e: IllegalArgumentException) {
            RelictClient.logger.warn("Coverage file for {} is unreadable ({}); keeping it as .bad and starting fresh", dim, e.message)
            s.writeBytes(fileFor(dim) + ".bad", bytes)
            CoverageGrid()
        }
    }

    private fun flush() {
        val s = store ?: return
        for (dim in dirty) grids[dim]?.let { s.writeBytes(fileFor(dim), it.toBytes()) }
        dirty.clear()
    }

    /** Dimensions that have saved coverage or are being recorded now. */
    private fun knownDimensions(): Set<String> {
        val s = store ?: return emptySet()
        val onDisk = java.nio.file.Files.list(s.dir).use { files ->
            files.map { it.fileName.toString() }.filter { it.startsWith("coverage-") && it.endsWith(".bin") }.map(::dimFromFile).toList()
        }
        return (onDisk + grids.keys).toSortedSet()
    }

    override fun call(method: String, params: JsonObject): JsonElement = when (method) {
        "stats" -> JsonArray().also { arr ->
            for (dim in knownDimensions()) {
                val chunks = grid(dim).count
                arr.add(JsonObject().apply {
                    addProperty("dimension", dim)
                    addProperty("chunks", chunks)
                    addProperty("areaKm2", chunks * 256.0 / 1_000_000.0) // a chunk is 16 x 16 blocks
                })
            }
        }
        "view" -> view(params)
        "clear" -> {
            val dim = params.string("dimension")
            grid(dim).clear()
            dirty += dim
            flush()
            JsonObject()
        }
        else -> super.call(method, params)
    }

    private fun view(p: JsonObject): JsonElement {
        val w = p.int("w")
        val h = p.int("h")
        val step = p.int("step")
        if (w !in 1..MAX_CELLS_PER_SIDE || h !in 1..MAX_CELLS_PER_SIDE || step !in 1..MAX_STEP) throw ApiException(400, "Window out of range")
        val x0 = p.int("x0")
        val z0 = p.int("z0")
        val data = grid(p.string("dimension")).raster(x0, z0, w, h, step)
        return JsonObject().apply {
            addProperty("x0", x0)
            addProperty("z0", z0)
            addProperty("w", w)
            addProperty("h", h)
            addProperty("step", step)
            addProperty("data", Base64.getEncoder().encodeToString(data))
        }
    }

    private fun JsonObject.string(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: throw ApiException(400, "'$key' must be a string")

    private fun JsonObject.int(key: String): Int =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: throw ApiException(400, "'$key' must be a number")

    private companion object {
        const val SCAN_INTERVAL = 10
        const val SAVE_INTERVAL = 600
        const val MAX_CELLS_PER_SIDE = 512
        const val MAX_STEP = 256
    }
}
