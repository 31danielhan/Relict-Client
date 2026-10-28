package relict.donut

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * What the Base Finder worked out about each chunk the last time it could really see it.
 *
 * DonutSMP only sends what is under Y 0 while you are under Y 0. Go up and those sections vanish from your game, and
 * without this the chunk would look empty and its outline would disappear (or the base would be taken for cleared out).
 * So when a chunk has no underground data, the last answer from when it had some is shown instead. It is saved with the
 * server's other files, so it is still there after you relog.
 */
object ChunkMemory {
    private val byDimension = HashMap<String, HashMap<Long, ChunkResult>>()

    @Volatile
    var dirty = false
        private set

    private fun key(cx: Int, cz: Int) = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    @Synchronized
    fun get(dimension: String, cx: Int, cz: Int): ChunkResult? = byDimension[dimension]?.get(key(cx, cz))

    @Synchronized
    fun put(dimension: String, result: ChunkResult) {
        val map = byDimension.getOrPut(dimension) { HashMap() }
        val old = map.put(key(result.chunkX, result.chunkZ), result.copy(fromMemory = false))
        if (old == null || old.score != result.score || old.kind != result.kind) dirty = true
        if (map.size > MAX_PER_DIMENSION) {
            // Keep the likeliest ones.
            val keep = map.entries.sortedByDescending { it.value.confidence }.take(MAX_PER_DIMENSION).map { it.key }.toHashSet()
            map.keys.retainAll(keep)
        }
    }

    @Synchronized
    fun remove(dimension: String, cx: Int, cz: Int) {
        if (byDimension[dimension]?.remove(key(cx, cz)) != null) dirty = true
    }

    @Synchronized
    fun clear() {
        byDimension.clear()
        dirty = false
    }

    @Synchronized
    fun toJson(): JsonObject = JsonObject().also { root ->
        for ((dimension, map) in byDimension) root.add(dimension, JsonArray().also { arr ->
            for (r in map.values) arr.add(JsonObject().apply {
                addProperty("x", r.chunkX); addProperty("z", r.chunkZ)
                addProperty("kind", r.kind.name); addProperty("score", r.score); addProperty("confidence", r.confidence)
                addProperty("sections", r.evidenceSections); addProperty("total", r.totalSections)
                addProperty("minY", r.minY); addProperty("maxY", r.maxY)
                add("evidence", JsonArray().also { e -> r.evidence.forEach { ev -> e.add(JsonArray().also { p -> p.add(ev.points); p.add(ev.text) }) } })
            })
        })
        dirty = false
    }

    @Synchronized
    fun load(json: JsonObject) {
        byDimension.clear()
        for ((dimension, value) in json.entrySet()) {
            if (!value.isJsonArray) continue
            val map = HashMap<Long, ChunkResult>()
            for (el in value.asJsonArray) runCatching {
                val o = el.asJsonObject
                val r = ChunkResult(
                    chunkX = o["x"].asInt, chunkZ = o["z"].asInt, kind = Kind.valueOf(o["kind"].asString),
                    score = o["score"].asInt, confidence = o["confidence"].asInt,
                    evidence = o["evidence"].asJsonArray.map { e -> Evidence(e.asJsonArray[0].asInt, e.asJsonArray[1].asString) },
                    evidenceSections = o["sections"].asInt, totalSections = o["total"].asInt,
                    minY = o["minY"].asInt, maxY = o["maxY"].asInt, fromMemory = true,
                )
                map[key(r.chunkX, r.chunkZ)] = r
            }
            byDimension[dimension] = map
        }
        dirty = false
    }

    private const val MAX_PER_DIMENSION = 4000
}
