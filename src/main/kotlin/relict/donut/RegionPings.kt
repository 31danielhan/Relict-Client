package relict.donut

import com.google.gson.JsonObject

/**
 * Your ping in each cell of DonutSMP's region map, as measured while you stood in it. DonutSMP is a Velocity network, so
 * each region can be a different server in a different place, and the connection you get differs from area to area.
 * A cell you have never stood in has no value.
 *
 * Each cell keeps a running average rather than the last reading, because the number the server reports jumps around.
 */
class RegionPings {
    private val values = HashMap<Int, Float>()

    @Volatile var dirty = false
        private set

    private fun key(column: Int, row: Int) = row * DonutRegions.GRID + column

    /** Folds a new reading into the cell's average. The first reading is taken as is. */
    fun record(column: Int, row: Int, ms: Int) {
        if (ms <= 0) return
        val k = key(column, row)
        val old = values[k]
        values[k] = if (old == null) ms.toFloat() else old + (ms - old) * SMOOTHING
        dirty = true
    }

    /** The cell's average ping in milliseconds, or null if you have not been there. */
    fun get(column: Int, row: Int): Int? = values[key(column, row)]?.let { Math.round(it) }

    val count: Int get() = values.size

    fun toJson(): JsonObject = JsonObject().apply {
        values.forEach { (k, v) -> addProperty(k.toString(), Math.round(v)) }
    }

    /** Pings by "column,row", the way the HUD page wants them. */
    fun toGrid(): JsonObject = JsonObject().apply {
        values.forEach { (k, v) -> addProperty("${k % DonutRegions.GRID},${k / DonutRegions.GRID}", Math.round(v)) }
    }

    fun markSaved() { dirty = false }

    companion object {
        /** How strongly a new reading moves the average. */
        const val SMOOTHING = 0.25f

        fun fromJson(json: JsonObject): RegionPings = RegionPings().also { p ->
            for ((k, v) in json.entrySet()) {
                val key = k.toIntOrNull() ?: continue
                if (key !in 0 until DonutRegions.GRID * DonutRegions.GRID) continue
                runCatching { v.asFloat }.getOrNull()?.takeIf { it > 0f }?.let { p.values[key] = it }
            }
        }
    }
}
