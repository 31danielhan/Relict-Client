package relict.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.UUID

/** All fields have defaults so Gson can read files written by older versions. */
class Waypoint(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "Waypoint",
    var x: Int = 0,
    var y: Int = 64,
    var z: Int = 0,
    var dimension: String = "minecraft:overworld",
    var tags: List<String> = emptyList(),
    var note: String = "",
    var createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        addProperty("x", x)
        addProperty("y", y)
        addProperty("z", z)
        addProperty("dimension", dimension)
        add("tags", JsonArray().also { arr -> tags.forEach(arr::add) })
        addProperty("note", note)
        addProperty("createdAt", createdAt)
    }
}

/** Tag ids a waypoint may carry; the UI supplies the labels and colours. */
object WaypointTags {
    val ALL = listOf("base", "farm", "lava", "sign", "other")
}

/** The waypoints of one profile. Input is always sanitised, so the UI and key presses can't store junk. */
class WaypointStore(private val onChange: () -> Unit = {}) {
    private val items = ArrayList<Waypoint>()

    val all: List<Waypoint> get() = items.toList()

    fun load(list: List<Waypoint>) {
        items.clear()
        list.forEach { items += sanitize(it) }
    }

    fun get(id: String): Waypoint? = items.firstOrNull { it.id == id }

    fun add(waypoint: Waypoint): Waypoint {
        val clean = sanitize(waypoint)
        items += clean
        onChange()
        return clean
    }

    /** Applies only the fields present in [patch]. Returns null if there is no such waypoint. */
    fun update(id: String, patch: JsonObject): Waypoint? {
        val w = get(id) ?: return null
        val clean = sanitize(patched(w, patch))
        items[items.indexOf(w)] = clean
        onChange()
        return clean
    }

    fun remove(id: String): Boolean {
        val removed = items.removeIf { it.id == id }
        if (removed) onChange()
        return removed
    }

    /** "Find 1", "Find 2", ...: one higher than the largest number already used with [base]. */
    fun nextName(base: String): String {
        val prefix = Regex("^" + Regex.escape(base) + " (\\d+)$")
        val max = items.maxOfOrNull { prefix.find(it.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0 } ?: 0
        return "$base ${max + 1}"
    }

    companion object {
        /** A copy of [w] with the fields present in [patch] replaced (not yet sanitised). */
        fun patched(w: Waypoint, patch: JsonObject): Waypoint {
            val copy = Waypoint(w.id, w.name, w.x, w.y, w.z, w.dimension, w.tags, w.note, w.createdAt)
            patch.str("name")?.let { copy.name = it }
            patch.int("x")?.let { copy.x = it }
            patch.int("y")?.let { copy.y = it }
            patch.int("z")?.let { copy.z = it }
            patch.str("dimension")?.let { copy.dimension = it }
            patch.str("note")?.let { copy.note = it }
            patch.get("tags")?.takeIf { it.isJsonArray }?.asJsonArray?.let { copy.tags = it.mapNotNull { e -> e.stringOrNull() } }
            return copy
        }

        private fun JsonObject.str(key: String) = get(key)?.stringOrNull()
        private fun JsonObject.int(key: String) = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble?.toInt()
        private fun JsonElement.stringOrNull() = takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

        private const val MAX_NAME = 48
        private const val MAX_NOTE = 500
        private const val COORD_LIMIT = 30_000_000
    }

    private fun sanitize(w: Waypoint) = Waypoint(
        id = w.id.ifBlank { UUID.randomUUID().toString() }.take(64),
        name = w.name.trim().take(MAX_NAME).ifEmpty { "Waypoint" },
        x = w.x.coerceIn(-COORD_LIMIT, COORD_LIMIT),
        y = w.y.coerceIn(-2048, 4096),
        z = w.z.coerceIn(-COORD_LIMIT, COORD_LIMIT),
        dimension = w.dimension.trim().take(64).ifEmpty { "minecraft:overworld" },
        tags = w.tags.map { it.lowercase() }.filter { it in WaypointTags.ALL }.distinct(),
        note = w.note.take(MAX_NOTE),
        createdAt = w.createdAt,
    )
}
