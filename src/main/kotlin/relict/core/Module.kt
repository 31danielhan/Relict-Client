package relict.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory

/**
 * How a module is allowed to be used. Red-list features (anything revealing hidden information)
 * are not modules at all; see PLAN.md.
 */
enum class Tier { GREEN, YELLOW }

/** A key the player can rebind in Controls; [onPress] runs only while the module is enabled. */
class Bind(val action: String, val defaultKey: Int, val onPress: () -> Unit)

abstract class Module(
    val id: String,
    val name: String,
    val description: String,
    val tier: Tier = Tier.GREEN,
) {
    private val logger = LoggerFactory.getLogger("Relict")

    val settings = SettingGroup()
    internal val binds = mutableListOf<Bind>()
    internal var onChange: (() -> Unit)? = null

    var enabled = false
        private set

    init {
        require(id.matches(Regex("[a-z][a-z0-9_]*"))) { "Module id '$id' must be lower_snake_case" }
        settings.onChange = { onChange?.invoke() }
    }

    /** Registers a rebindable key for this module, unbound by default unless [defaultKey] is given. */
    protected fun bind(action: String, defaultKey: Int = -1, onPress: () -> Unit) {
        binds += Bind(action, defaultKey, onPress)
    }

    internal fun toggle() = setEnabled(!enabled)

    /** A module that throws while enabling stays disabled instead of taking the game down. Use [ModuleManager.setEnabled]. */
    internal fun setEnabled(value: Boolean) {
        if (value == enabled) return
        enabled = value
        try {
            if (value) onEnable() else onDisable()
        } catch (e: Exception) {
            logger.error("Module '{}' failed in {}; disabling it", id, if (value) "onEnable" else "onDisable", e)
            enabled = false
        }
        onChange?.invoke()
    }

    /**
     * Handles a `<id>.<method>` call from the UI, always on the game thread. Throw [ApiException] for bad input.
     * Only called while the module is enabled.
     */
    open fun call(method: String, params: JsonObject): JsonElement =
        throw ApiException(404, "Unknown method '$id.$method'")

    /** Pushes `<id>.<name>` to the open UI, if any. */
    protected fun emit(name: String, payload: JsonElement) {
        ModuleManager.eventSink?.invoke("$id.$name", payload)
    }

    /** Why this module can't do its job on this Minecraft version yet, shown in the UI. Null means it works. */
    open val unavailableReason: String? get() = null

    open fun onEnable() {}
    open fun onDisable() {}
    open fun onTick() {}

    /** A profile (server or world) was opened; its files are available via [store]. */
    open fun onProfileOpen(store: ProfileStore) {}
    open fun onProfileClose(store: ProfileStore) {}

    fun toConfig(): JsonObject = JsonObject().apply {
        addProperty("enabled", enabled)
        add("settings", settings.toJson())
    }

    /** Applies saved state without triggering a config save. */
    internal fun loadConfig(obj: JsonObject) {
        val saved = onChange
        onChange = null
        try {
            obj.get("settings")?.takeIf { it.isJsonObject }?.let { settings.load(it.asJsonObject) }
            val enabledFlag = obj.get("enabled")
            if (enabledFlag != null && enabledFlag.isJsonPrimitive && enabledFlag.asJsonPrimitive.isBoolean && enabledFlag.asBoolean) {
                setEnabled(true)
            }
        } finally {
            onChange = saved
        }
    }

    /** Description of the module and its settings, as sent to the UI. */
    fun describe(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        addProperty("description", description)
        addProperty("tier", tier.name.lowercase())
        addProperty("enabled", enabled)
        unavailableReason?.let { addProperty("unavailable", it) }
        add("settings", com.google.gson.JsonArray().also { arr -> settings.all.forEach { arr.add(it.describe()) } })
    }
}
