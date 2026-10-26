package relict.core

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleTest {
    private class Probe(var failOnEnable: Boolean = false) : Module("probe", "Probe", "test module") {
        val radius = settings.int("radius", "Radius", 8, 1..64)
        var enableCalls = 0
        var disableCalls = 0
        override fun onEnable() {
            enableCalls++
            if (failOnEnable) error("boom")
        }

        override fun onDisable() {
            disableCalls++
        }
    }

    @Test
    fun enableAndDisableRunHooksOnce() {
        val m = Probe()
        m.setEnabled(true)
        m.setEnabled(true)
        assertTrue(m.enabled)
        assertEquals(1, m.enableCalls)
        m.setEnabled(false)
        m.setEnabled(false)
        assertEquals(1, m.disableCalls)
    }

    @Test
    fun throwingOnEnableLeavesModuleDisabled() {
        val m = Probe(failOnEnable = true)
        m.setEnabled(true)
        assertFalse(m.enabled)
    }

    @Test
    fun changesNotifyButLoadingConfigDoesNot() {
        val m = Probe()
        var changes = 0
        m.onChange = { changes++ }

        m.loadConfig(JsonParser.parseString("""{"enabled":true,"settings":{"radius":20}}""").asJsonObject)
        assertEquals(0, changes)
        assertTrue(m.enabled)
        assertEquals(20, m.radius.value)

        m.radius.value = 30
        m.setEnabled(false)
        assertEquals(2, changes)
    }

    @Test
    fun loadConfigToleratesGarbage() {
        val m = Probe()
        m.loadConfig(JsonParser.parseString("""{"enabled":"yes","settings":[1,2]}""").asJsonObject)
        assertFalse(m.enabled)
        assertEquals(8, m.radius.value)
    }

    @Test
    fun configRoundTrip() {
        val a = Probe()
        a.setEnabled(true)
        a.radius.value = 12
        val b = Probe()
        b.loadConfig(a.toConfig())
        assertTrue(b.enabled)
        assertEquals(12, b.radius.value)
    }

    @Test
    fun idMustBeSnakeCase() {
        assertFailsWith<IllegalArgumentException> { object : Module("Bad-Id", "x", "x") {} }
    }

    @Test
    fun describeListsSettings() {
        val d = Probe().describe()
        assertEquals("probe", d["id"].asString)
        assertEquals("green", d["tier"].asString)
        assertEquals(1, d["settings"].asJsonArray.size())
    }
}
