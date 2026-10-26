package relict.ui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import relict.core.ApiException
import relict.core.ClientSettings
import relict.core.Module
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiTest {
    private class Probe : Module("probe", "Probe", "test module") {
        val radius = settings.int("radius", "Radius", 8, 1..64)
        val flag = settings.bool("flag", "Flag", false)

        override fun call(method: String, params: JsonObject): com.google.gson.JsonElement = when (method) {
            "echo" -> params
            else -> super.call(method, params)
        }
    }

    private val probe = Probe()
    private val toggles = mutableListOf<Pair<String, Boolean>>()

    private val api = Api(object : ApiHost {
        override val modules = listOf<Module>(probe)
        override fun setEnabled(module: Module, enabled: Boolean) {
            toggles += module.id to enabled
            module.setEnabled(enabled)
        }

        override fun info() = JsonObject().apply { addProperty("version", "test") }
    })

    @BeforeTest
    fun resetClientSettings() = ClientSettings.group.all.forEach { it.reset() }

    private fun call(method: String, params: String = "{}"): com.google.gson.JsonElement =
        JsonParser.parseString(api.handle("""{"method":"$method","params":$params}"""))

    private fun code(block: () -> Unit) = assertFailsWith<ApiException>(block = block).code

    @Test
    fun infoAndModuleList() {
        assertEquals("test", call("app.info").asJsonObject["version"].asString)
        val list = call("modules.list").asJsonArray
        assertEquals(1, list.size())
        assertEquals("probe", list[0].asJsonObject["id"].asString)
        assertEquals(2, list[0].asJsonObject["settings"].asJsonArray.size())
    }

    @Test
    fun setEnabledGoesThroughTheHostAndReturnsTheModule() {
        val result = call("modules.setEnabled", """{"id":"probe","enabled":true}""").asJsonObject
        assertTrue(result["enabled"].asBoolean)
        assertEquals(listOf("probe" to true), toggles)
    }

    @Test
    fun settingValueIsClampedAndTheAppliedValueReturned() {
        val result = call("settings.set", """{"module":"probe","id":"radius","value":9999}""").asJsonObject
        assertEquals(64, result["value"].asInt)
        assertEquals(64, probe.radius.value)
    }

    @Test
    fun invalidValueIsRejectedAndLeavesTheSettingAlone() {
        assertEquals(400, code { call("settings.set", """{"module":"probe","id":"radius","value":"lots"}""") })
        assertEquals(8, probe.radius.value)
    }

    @Test
    fun clientSettingsAreReachableUnderTheClientId() {
        val result = call("settings.set", """{"module":"client","id":"accent","value":"green"}""").asJsonObject
        assertEquals("GREEN", result["value"].asString)
        assertEquals(ClientSettings.Accent.GREEN, ClientSettings.accent.value)
        assertEquals(2, call("client.settings").asJsonArray.size())
    }

    @Test
    fun resetRestoresTheDefault() {
        probe.flag.value = true
        val result = call("settings.reset", """{"module":"probe","id":"flag"}""").asJsonObject
        assertFalse(result["value"].asBoolean)
    }

    @Test
    fun unknownThingsAre404() {
        assertEquals(404, code { call("nope") })
        assertEquals(404, code { call("modules.setEnabled", """{"id":"ghost","enabled":true}""") })
        assertEquals(404, code { call("settings.set", """{"module":"probe","id":"ghost","value":1}""") })
    }

    @Test
    fun moduleMethodsAreRoutedToTheModuleWhenEnabled() {
        probe.setEnabled(true)
        val result = call("probe.echo", """{"a":1}""").asJsonObject
        assertEquals(1, result["a"].asInt)
        assertEquals(404, code { call("probe.missing") })
    }

    @Test
    fun moduleMethodsAreRefusedWhileDisabled() {
        assertEquals(409, code { call("probe.echo") })
    }

    @Test
    fun methodsOfUnknownModulesAre404() {
        assertEquals(404, code { call("ghost.echo") })
    }

    @Test
    fun malformedRequestsAre400() {
        assertEquals(400, code { api.handle("not json") })
        assertEquals(400, code { api.handle("[]") })
        assertEquals(400, code { call("modules.setEnabled", """{"id":"probe","enabled":"yes"}""") })
        assertEquals(400, code { call("settings.set", """{"module":"probe","id":"flag"}""") })
    }
}
