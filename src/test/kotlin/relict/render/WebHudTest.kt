package relict.render

import com.google.gson.JsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebHudTest {
    @AfterTest
    fun clear() {
        for (key in listOf("finder", "regions", "inspect", "player", "config")) WebHud.set(key, null)
    }

    private fun value(n: Int) = JsonObject().apply { addProperty("n", n) }

    @Test
    fun eachPanelGoesOnlyToItsOwnLayer() {
        WebHud.set("finder", value(1))
        assertTrue(WebHud.finder.state.containsKey("finder"))
        assertFalse(WebHud.regions.state.containsKey("finder"))
        assertFalse(WebHud.inspect.state.containsKey("finder"))
        assertFalse(WebHud.toasts.state.containsKey("finder"))
        WebHud.set("regions", value(2))
        assertTrue(WebHud.regions.state.containsKey("regions"))
        assertFalse(WebHud.finder.state.containsKey("regions"))
    }

    @Test
    fun thePlayersPositionGoesToTheLayersThatUseIt() {
        WebHud.set("player", value(3))
        assertTrue(WebHud.finder.state.containsKey("player"))
        assertTrue(WebHud.regions.state.containsKey("player"))
        assertFalse(WebHud.inspect.state.containsKey("player"), "the aim card is complete without it")
        assertFalse(WebHud.toasts.state.containsKey("player"))
    }

    @Test
    fun theSettingsGoToEveryLayer() {
        WebHud.set("config", value(4))
        assertEquals(4, WebHud.layers.count { it.state.containsKey("config") })
    }

    @Test
    fun supportDataAloneDoesNotMakeALayerWanted() {
        WebHud.set("player", value(1))
        WebHud.set("config", value(1))
        for (layer in WebHud.layers) assertFalse(WebHud.wanted(layer), layer.name)
        WebHud.set("finder", value(1))
        assertTrue(WebHud.wanted(WebHud.finder))
        assertFalse(WebHud.wanted(WebHud.regions))
    }

    @Test
    fun removingAPanelRemovesItFromItsLayer() {
        WebHud.set("regions", value(1))
        WebHud.set("regions", null)
        assertFalse(WebHud.regions.state.containsKey("regions"))
    }
}
