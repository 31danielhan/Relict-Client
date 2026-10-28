package relict.render

import com.google.gson.JsonObject
import relict.game.PlayerState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InspectorTest {
    private val owner = Any()

    private fun item(x: Double, y: Double, z: Double, title: String) = Inspector.Item("k:$title", x, y, z) { distance, direction ->
        JsonObject().apply { addProperty("title", title); addProperty("distance", distance); addProperty("direction", direction) }
    }

    private fun shown(): String? = WebHud.panels.state["inspect"]?.let { com.google.gson.JsonParser.parseString(it).asJsonObject["title"].asString }

    @AfterTest
    fun clear() {
        Inspector.publish(owner, emptyList())
        Inspector.tick(null)
    }

    // Minecraft: yaw 0 looks south (+Z), yaw -90 east (+X), pitch positive looks down.
    private fun player(yaw: Float, pitch: Float = 0f) = PlayerState(0.0, 64.0, 0.0, yaw, "minecraft:overworld", pitch)

    @Test
    fun theThingTheCrosshairIsOnShowsItsCard() {
        Inspector.publish(owner, listOf(item(0.0, 65.62, 20.0, "south"), item(20.0, 65.62, 0.0, "east")))
        Inspector.tick(player(yaw = 0f))
        assertEquals("south", shown())
        Inspector.tick(player(yaw = -90f))
        assertEquals("east", shown())
    }

    @Test
    fun lookingAwayShowsNothing() {
        Inspector.publish(owner, listOf(item(0.0, 65.62, 20.0, "south")))
        Inspector.tick(player(yaw = 180f))
        assertNull(shown())
        Inspector.tick(player(yaw = 40f)) // 40 degrees off
        assertNull(shown())
    }

    @Test
    fun aLightlyPointingCrosshairCounts() {
        Inspector.publish(owner, listOf(item(0.0, 65.62, 30.0, "south")))
        Inspector.tick(player(yaw = 1.0f)) // about half a block off at 30 blocks
        assertEquals("south", shown())
    }

    @Test
    fun theCardKnowsHowFarAndWhichWay() {
        Inspector.publish(owner, listOf(item(0.0, 65.62, 25.0, "south")))
        Inspector.tick(player(yaw = 0f))
        val card = com.google.gson.JsonParser.parseString(assertNotNull(WebHud.panels.state["inspect"])).asJsonObject
        assertEquals(25, card["distance"].asInt)
        assertEquals("S", card["direction"].asString)
    }

    @Test
    fun lookingStraightDownFindsSomethingBelow() {
        Inspector.publish(owner, listOf(item(0.0, 20.0, 0.0, "below")))
        Inspector.tick(player(yaw = 0f, pitch = 90f))
        assertEquals("below", shown())
    }

    @Test
    fun theKeyOfWhatIsAimedAtIsAvailableForTheDot() {
        Inspector.publish(owner, listOf(item(0.0, 65.62, 20.0, "south")))
        Inspector.tick(player(yaw = 0f))
        assertEquals("k:south", Inspector.aimedKey)
        Inspector.tick(player(yaw = 180f))
        assertNull(Inspector.aimedKey)
    }

    @Test
    fun aFreeCameraAimsFromWhereTheCameraIs() {
        Inspector.publish(owner, listOf(item(100.0, 10.0, 0.0, "far east")))
        // The player stands at the origin looking south, nowhere near it; the free camera is out east looking west at it.
        val player = player(yaw = 0f)
        Inspector.tick(player, null)
        assertNull(shown())
        Inspector.tick(player, CameraPose(130.0, 10.0, 0.0, 90f, 0f))
        assertEquals("far east", shown())
        val card = com.google.gson.JsonParser.parseString(assertNotNull(WebHud.panels.state["inspect"])).asJsonObject
        assertEquals(30, card["distance"].asInt, "measured from the camera")
        assertEquals("W", card["direction"].asString)
    }
}
