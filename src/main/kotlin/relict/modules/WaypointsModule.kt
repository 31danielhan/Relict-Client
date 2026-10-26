package relict.modules

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import relict.core.ApiException
import relict.core.KeyCodes
import relict.core.Module
import relict.core.ProfileStore
import relict.core.Waypoint
import relict.core.WaypointStore
import relict.core.WaypointTags
import relict.game.GameHolder

/** Save and tag the places you find. Drop one at your feet with a key, or add and edit them in the UI. */
class WaypointsModule : Module(
    "waypoints", "Waypoints",
    "Save and tag the places you find. Bind a key in Controls to drop one where you stand.",
) {
    enum class DefaultTag { BASE, FARM, LAVA, SIGN, OTHER }

    val defaultName = settings.string("defaultName", "Default name", "Find", 24, "New waypoints are named like \"Find 1\", \"Find 2\".")
    val defaultTag = settings.enum("defaultTag", "Default tag", DefaultTag.BASE, "Tag given to waypoints dropped with the key.")

    private var profile: ProfileStore? = null
    private var dirty = false
    private val store = WaypointStore { dirty = true; emit("changed", list()) }

    init {
        bind("drop", KeyCodes.UNBOUND) { dropHere() }
    }

    override fun onProfileOpen(store: ProfileStore) {
        profile = store
        this.store.load(store.readJson<List<Waypoint>>(FILE) { emptyList() })
        dirty = false
    }

    override fun onProfileClose(store: ProfileStore) {
        flush()
        profile = null
        this.store.load(emptyList())
        dirty = false
    }

    override fun onDisable() = flush()

    private var ticks = 0
    override fun onTick() {
        if (dirty && ++ticks % SAVE_INTERVAL == 0) flush()
    }

    private fun flush() {
        val p = profile ?: return
        if (!dirty) return
        p.writeJson(FILE, store.all)
        dirty = false
    }

    private fun list(): JsonArray = JsonArray().also { arr -> store.all.forEach { arr.add(it.toJson()) } }

    private fun here(): Waypoint? {
        val player = GameHolder.game.player() ?: return null
        return Waypoint(
            name = store.nextName(defaultName.value.ifBlank { "Find" }),
            x = player.blockX, y = player.blockY, z = player.blockZ,
            dimension = player.dimension,
            tags = listOf(defaultTag.value.name.lowercase()),
        )
    }

    private fun dropHere() {
        if (profile == null) return
        val saved = store.add(here() ?: return)
        GameHolder.game.showOverlayMessage("Saved \"${saved.name}\" at ${saved.x}, ${saved.y}, ${saved.z}")
    }

    override fun call(method: String, params: JsonObject): JsonElement {
        if (profile == null) throw ApiException(409, "Join a world or server to use waypoints")
        return when (method) {
            "list" -> list()
            "add" -> store.add(fromParams(params)).toJson()
            "addHere" -> {
                val w = here() ?: throw ApiException(409, "You are not in a world")
                params.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotBlank() }?.let { w.name = it }
                store.add(w).toJson()
            }
            "update" -> store.update(params.string("id"), params)?.toJson() ?: throw ApiException(404, "Unknown waypoint")
            "delete" -> {
                if (!store.remove(params.string("id"))) throw ApiException(404, "Unknown waypoint")
                JsonObject()
            }
            "tags" -> JsonArray().also { arr -> WaypointTags.ALL.forEach(arr::add) }
            else -> super.call(method, params)
        }
    }

    private fun fromParams(p: JsonObject): Waypoint {
        if (p.get("x") == null || p.get("z") == null) throw ApiException(400, "'x' and 'z' are required")
        return WaypointStore.patched(Waypoint(name = store.nextName(defaultName.value.ifBlank { "Find" })), p)
    }

    private fun JsonObject.string(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: throw ApiException(400, "'$key' must be a string")

    private companion object {
        const val FILE = "waypoints.json"
        const val SAVE_INTERVAL = 40
    }
}
