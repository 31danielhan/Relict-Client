package relict.ui

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import relict.core.ApiException
import relict.core.ClientSettings
import relict.core.Json
import relict.core.Module
import relict.core.Setting
import relict.core.SettingGroup

/** What the API needs from the running game. Kept as an interface so the API can be tested without Minecraft. */
interface ApiHost {
    val modules: List<Module>
    fun setEnabled(module: Module, enabled: Boolean)
    fun info(): JsonObject
}

/**
 * The calls the web UI can make. A request is `{"method": "...", "params": {...}}` and the reply is the JSON result.
 * Settings are always re-sent after a change so the UI shows the value that was actually applied (e.g. clamped).
 */
class Api(private val host: ApiHost) {
    fun handle(request: String): String = Json.compact.toJson(dispatch(request))

    private fun dispatch(request: String): JsonElement {
        val root = try {
            JsonParser.parseString(request).asJsonObject
        } catch (e: Exception) {
            throw ApiException(400, "Malformed request")
        }
        val method = root.string("method")
        val params = root.get("params")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()

        return when (method) {
            "app.info" -> host.info()
            "modules.list" -> JsonArray().also { arr -> host.modules.forEach { arr.add(it.describe()) } }
            "client.settings" -> describe(ClientSettings.group)
            "modules.setEnabled" -> {
                val module = module(params.string("id"))
                val enabled = params.get("enabled")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
                    ?: throw ApiException(400, "'enabled' must be a boolean")
                host.setEnabled(module, enabled)
                module.describe()
            }
            "settings.set" -> {
                val setting = setting(params.string("module"), params.string("id"))
                val value = params.get("value") ?: throw ApiException(400, "Missing 'value'")
                if (!setting.fromJson(value)) throw ApiException(400, "Invalid value for '${setting.id}'")
                setting.describe()
            }
            "settings.reset" -> {
                val setting = setting(params.string("module"), params.string("id"))
                setting.reset()
                setting.describe()
            }
            else -> moduleCall(method, params)
        }
    }

    /** `<moduleId>.<method>` is handed to the module itself, but only while it is enabled. */
    private fun moduleCall(method: String, params: JsonObject): JsonElement {
        val dot = method.indexOf('.')
        val module = if (dot > 0) host.modules.firstOrNull { it.id == method.substring(0, dot) } else null
        if (module == null) throw ApiException(404, "Unknown method '$method'")
        if (!module.enabled) throw ApiException(409, "Module '${module.id}' is disabled")
        return module.call(method.substring(dot + 1), params)
    }

    private fun describe(group: SettingGroup) = JsonArray().also { arr -> group.all.forEach { arr.add(it.describe()) } }

    private fun module(id: String): Module =
        host.modules.firstOrNull { it.id == id } ?: throw ApiException(404, "Unknown module '$id'")

    private fun setting(moduleId: String, id: String): Setting<*> {
        val group = if (moduleId == ClientSettings.ID) ClientSettings.group else module(moduleId).settings
        return group[id] ?: throw ApiException(404, "Unknown setting '$moduleId.$id'")
    }

    private fun JsonObject.string(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: throw ApiException(400, "'$key' must be a string")
}
