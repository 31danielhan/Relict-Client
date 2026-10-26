package relict.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingTest {
    private enum class Speed { SLOW, FAST }

    @Test
    fun intIsClampedToRange() {
        val s = IntSetting("n", "N", 5, 1..10)
        s.value = 99
        assertEquals(10, s.value)
        s.value = -3
        assertEquals(1, s.value)
    }

    @Test
    fun doubleRejectsNaN() {
        val s = DoubleSetting("d", "D", 1.5, 0.0..2.0)
        s.value = Double.NaN
        assertEquals(1.5, s.value)
        s.value = 7.0
        assertEquals(2.0, s.value)
    }

    @Test
    fun stringIsTruncated() {
        val s = StringSetting("s", "S", "", maxLength = 4)
        s.value = "abcdefgh"
        assertEquals("abcd", s.value)
    }

    @Test
    fun malformedJsonIsIgnored() {
        val s = IntSetting("n", "N", 5, 1..10)
        assertFalse(s.fromJson(JsonParser.parseString("\"nope\"")))
        assertFalse(s.fromJson(JsonParser.parseString("true")))
        assertFalse(s.fromJson(JsonParser.parseString("null")))
        assertEquals(5, s.value)
        assertTrue(s.fromJson(JsonParser.parseString("7")))
        assertEquals(7, s.value)
    }

    @Test
    fun enumMatchesCaseInsensitivelyAndRejectsUnknown() {
        val g = SettingGroup()
        val s = g.enum("speed", "Speed", Speed.SLOW)
        assertTrue(s.fromJson(JsonParser.parseString("\"fast\"")))
        assertEquals(Speed.FAST, s.value)
        assertFalse(s.fromJson(JsonParser.parseString("\"warp\"")))
        assertEquals(Speed.FAST, s.value)
    }

    @Test
    fun changeCallbackFiresOnlyOnRealChange() {
        val g = SettingGroup()
        val s = g.int("n", "N", 5, 1..10)
        var changes = 0
        g.onChange = { changes++ }
        s.value = 5
        assertEquals(0, changes)
        s.value = 6
        s.value = 6
        assertEquals(1, changes)
    }

    @Test
    fun groupRoundTripsAndIgnoresUnknownKeys() {
        val g = SettingGroup()
        val n = g.int("n", "N", 5, 1..10)
        val flag = g.bool("flag", "Flag", false)
        val mode = g.enum("mode", "Mode", Speed.SLOW)
        n.value = 8
        flag.value = true
        mode.value = Speed.FAST

        val saved = g.toJson()

        val g2 = SettingGroup()
        val n2 = g2.int("n", "N", 5, 1..10)
        val flag2 = g2.bool("flag", "Flag", false)
        val mode2 = g2.enum("mode", "Mode", Speed.SLOW)
        saved.addProperty("removed_in_this_version", 1)
        g2.load(saved)
        assertEquals(8, n2.value)
        assertTrue(flag2.value)
        assertEquals(Speed.FAST, mode2.value)
    }

    @Test
    fun duplicateIdsAreRejected() {
        val g = SettingGroup()
        g.bool("x", "X", false)
        assertFailsWith<IllegalArgumentException> { g.bool("x", "X again", true) }
    }

    @Test
    fun describeIncludesSchemaForTheUi() {
        val d = IntSetting("n", "N", 5, 1..10, "count").describe()
        assertEquals("int", d["type"].asString)
        assertEquals(1, d["min"].asInt)
        assertEquals(10, d["max"].asInt)
        assertEquals(5, d["value"].asInt)
        assertEquals(5, d["default"].asInt)
        val e = EnumSetting("m", "M", Speed.SLOW, Speed.entries).describe()
        assertEquals(listOf("SLOW", "FAST"), e["options"].asJsonArray.map { it.asString })
    }

    @Test
    fun resetRestoresDefault() {
        val s = BoolSetting("b", "B", true)
        s.value = false
        s.reset()
        assertTrue(s.value)
    }

    @Suppress("unused")
    private fun unusedToSilenceImport(o: JsonObject) = o
}
