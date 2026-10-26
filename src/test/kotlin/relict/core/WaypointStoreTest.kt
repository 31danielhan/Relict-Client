package relict.core

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaypointStoreTest {
    private var changes = 0
    private val store = WaypointStore { changes++ }

    private fun patch(json: String) = JsonParser.parseString(json).asJsonObject

    @Test
    fun addSanitisesInput() {
        val w = store.add(Waypoint(name = "   ", x = 99_999_999, y = 10_000, z = -99_999_999, tags = listOf("BASE", "nonsense", "base", "Lava"), note = "n".repeat(900), dimension = ""))
        assertEquals("Waypoint", w.name)
        assertEquals(30_000_000, w.x)
        assertEquals(-30_000_000, w.z)
        assertEquals(4096, w.y)
        assertEquals(listOf("base", "lava"), w.tags)
        assertEquals(500, w.note.length)
        assertEquals("minecraft:overworld", w.dimension)
        assertEquals(1, changes)
    }

    @Test
    fun updateChangesOnlyTheGivenFields() {
        val w = store.add(Waypoint(name = "A", x = 1, y = 2, z = 3, note = "keep"))
        val u = store.update(w.id, patch("""{"name":"B","x":10,"tags":["farm"]}"""))!!
        assertEquals("B", u.name)
        assertEquals(10, u.x)
        assertEquals(2, u.y)
        assertEquals(3, u.z)
        assertEquals("keep", u.note)
        assertEquals(listOf("farm"), u.tags)
        assertEquals(w.id, u.id)
        assertEquals(1, store.all.size)
    }

    @Test
    fun updateIgnoresWrongTypes() {
        val w = store.add(Waypoint(name = "A", x = 5))
        val u = store.update(w.id, patch("""{"name":42,"x":"far","tags":"base"}"""))!!
        assertEquals("A", u.name)
        assertEquals(5, u.x)
    }

    @Test
    fun updateAndRemoveOfUnknownIdsDoNothing() {
        assertNull(store.update("nope", patch("{}")))
        assertFalse(store.remove("nope"))
        assertEquals(0, changes)
    }

    @Test
    fun removeDeletes() {
        val w = store.add(Waypoint(name = "A"))
        assertTrue(store.remove(w.id))
        assertTrue(store.all.isEmpty())
    }

    @Test
    fun nextNameCountsUp() {
        assertEquals("Find 1", store.nextName("Find"))
        store.add(Waypoint(name = "Find 1"))
        store.add(Waypoint(name = "Find 7"))
        store.add(Waypoint(name = "Finder 9")) // different base, ignored
        assertEquals("Find 8", store.nextName("Find"))
        assertEquals("Base 1", store.nextName("Base"))
    }

    @Test
    fun nextNameTreatsTheBaseLiterally() {
        store.add(Waypoint(name = "a.b 3"))
        assertEquals("a.b 4", store.nextName("a.b"))
        assertEquals("axb 1", store.nextName("axb"))
    }

    @Test
    fun loadSanitisesAndDoesNotNotify() {
        store.load(listOf(Waypoint(name = "x".repeat(200), tags = listOf("zzz"))))
        assertEquals(48, store.all[0].name.length)
        assertTrue(store.all[0].tags.isEmpty())
        assertEquals(0, changes)
    }

    @Test
    fun idsAreUnique() {
        assertNotEquals(Waypoint().id, Waypoint().id)
    }

    @Test
    fun jsonRoundTripThroughGson() {
        val w = Waypoint(name = "Spot", x = -5, y = 70, z = 12, tags = listOf("sign"), note = "hi")
        val back = Json.compact.fromJson(Json.compact.toJson(listOf(w)), Array<Waypoint>::class.java)[0]
        assertEquals(w.id, back.id)
        assertEquals("Spot", back.name)
        assertEquals(listOf("sign"), back.tags)
    }

    @Test
    fun oldFilesWithMissingFieldsStillLoad() {
        val back = Json.compact.fromJson("""[{"name":"Old","x":1,"z":2}]""", Array<Waypoint>::class.java)[0]
        store.load(listOf(back))
        assertEquals("Old", store.all[0].name)
        assertEquals("minecraft:overworld", store.all[0].dimension)
        assertTrue(store.all[0].id.isNotBlank())
    }
}
