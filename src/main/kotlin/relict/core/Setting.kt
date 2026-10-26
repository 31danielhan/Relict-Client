package relict.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * A typed, persisted module option. Values are always kept valid: out-of-range input is clamped
 * and malformed JSON is ignored, so a hand-edited config can never break startup.
 */
abstract class Setting<T : Any>(
    val id: String,
    val name: String,
    val type: String,
    val default: T,
    val description: String = "",
) {
    internal var onChange: (() -> Unit)? = null

    var value: T = default
        set(new) {
            val v = coerce(new)
            if (v != field) {
                field = v
                onChange?.invoke()
            }
        }

    protected abstract fun coerce(v: T): T
    protected abstract fun encode(v: T): JsonElement

    /** Applies a stored or UI-supplied value. Returns false (leaving the value alone) if it is malformed. */
    abstract fun fromJson(element: JsonElement): Boolean

    fun toJson(): JsonElement = encode(value)

    fun reset() {
        value = default
    }

    /** Schema + current value, as sent to the UI. */
    open fun describe(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        addProperty("type", type)
        addProperty("description", description)
        add("value", toJson())
        add("default", encode(default))
    }
}

class BoolSetting(id: String, name: String, default: Boolean, description: String = "") :
    Setting<Boolean>(id, name, "bool", default, description) {
    override fun coerce(v: Boolean) = v
    override fun encode(v: Boolean): JsonElement = JsonPrimitive(v)
    override fun fromJson(element: JsonElement): Boolean {
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) return false
        value = element.asBoolean
        return true
    }
}

class IntSetting(id: String, name: String, default: Int, val range: IntRange, description: String = "") :
    Setting<Int>(id, name, "int", default, description) {
    override fun coerce(v: Int) = v.coerceIn(range)
    override fun encode(v: Int): JsonElement = JsonPrimitive(v)
    override fun fromJson(element: JsonElement): Boolean {
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return false
        value = element.asDouble.toInt()
        return true
    }

    override fun describe() = super.describe().apply {
        addProperty("min", range.first)
        addProperty("max", range.last)
    }
}

class DoubleSetting(id: String, name: String, default: Double, val range: ClosedFloatingPointRange<Double>, description: String = "") :
    Setting<Double>(id, name, "double", default, description) {
    override fun coerce(v: Double) = if (v.isNaN()) default else v.coerceIn(range)
    override fun encode(v: Double): JsonElement = JsonPrimitive(v)
    override fun fromJson(element: JsonElement): Boolean {
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return false
        value = element.asDouble
        return true
    }

    override fun describe() = super.describe().apply {
        addProperty("min", range.start)
        addProperty("max", range.endInclusive)
    }
}

class StringSetting(id: String, name: String, default: String, val maxLength: Int = 256, description: String = "") :
    Setting<String>(id, name, "string", default, description) {
    override fun coerce(v: String) = v.take(maxLength)
    override fun encode(v: String): JsonElement = JsonPrimitive(v)
    override fun fromJson(element: JsonElement): Boolean {
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return false
        value = element.asString
        return true
    }

    override fun describe() = super.describe().apply { addProperty("maxLength", maxLength) }
}

class EnumSetting<E : Enum<E>>(id: String, name: String, default: E, private val options: List<E>, description: String = "") :
    Setting<E>(id, name, "enum", default, description) {
    override fun coerce(v: E) = v
    override fun encode(v: E): JsonElement = JsonPrimitive(v.name)
    override fun fromJson(element: JsonElement): Boolean {
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return false
        val match = options.firstOrNull { it.name.equals(element.asString, ignoreCase = true) } ?: return false
        value = match
        return true
    }

    override fun describe() = super.describe().apply {
        add("options", JsonArray().also { arr -> options.forEach { arr.add(it.name) } })
    }
}

/** The ordered set of settings belonging to one module. */
class SettingGroup {
    private val byId = LinkedHashMap<String, Setting<*>>()
    internal var onChange: (() -> Unit)? = null

    val all: List<Setting<*>> get() = byId.values.toList()

    operator fun get(id: String): Setting<*>? = byId[id]

    fun <S : Setting<*>> add(setting: S): S {
        require(setting.id !in byId) { "Duplicate setting id '${setting.id}'" }
        byId[setting.id] = setting
        setting.onChange = { onChange?.invoke() }
        return setting
    }

    fun bool(id: String, name: String, default: Boolean, description: String = "") =
        add(BoolSetting(id, name, default, description))

    fun int(id: String, name: String, default: Int, range: IntRange, description: String = "") =
        add(IntSetting(id, name, default, range, description))

    fun double(id: String, name: String, default: Double, range: ClosedFloatingPointRange<Double>, description: String = "") =
        add(DoubleSetting(id, name, default, range, description))

    fun string(id: String, name: String, default: String, maxLength: Int = 256, description: String = "") =
        add(StringSetting(id, name, default, maxLength, description))

    inline fun <reified E : Enum<E>> enum(id: String, name: String, default: E, description: String = "") =
        add(EnumSetting(id, name, default, enumValues<E>().toList(), description))

    fun toJson(): JsonObject = JsonObject().also { obj -> byId.values.forEach { obj.add(it.id, it.toJson()) } }

    /** Unknown keys (removed settings) and malformed values are ignored. */
    fun load(obj: JsonObject) {
        for ((key, element) in obj.entrySet()) byId[key]?.fromJson(element)
    }
}
