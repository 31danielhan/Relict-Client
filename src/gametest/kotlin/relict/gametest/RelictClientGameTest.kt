package relict.gametest

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import relict.game.GameHolder
import relict.render.WebHud
import relict.core.KeyCodes
import relict.core.ModuleManager
import relict.ui.Bridge
import relict.ui.BrowserScreen
import relict.ui.Mcef

/**
 * Plays the mod in a real world: records coverage while "flying", drops waypoints with the real key,
 * opens the GUI with the real key and screenshots every page. Run with ./gradlew runClientGametest.
 */
class RelictClientGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.runOnClient<RuntimeException> { GameTestCompat.setRenderDistance(it, 6) }
        context.waitFor({ Mcef.ready }, 20_000) // the first run downloads Chromium

        // The open key works from the title screen, and Esc comes back to it.
        context.input.pressKey(KeyCodes.RIGHT_SHIFT)
        context.waitForScreen(BrowserScreen::class.java)
        context.waitTicks(20)
        context.input.pressKey(KeyCodes.ESCAPE)
        context.waitTicks(5)
        context.runOnClient<RuntimeException> { check(GameTestCompat.screenName(it) == "TitleScreen") { "Esc should return to the title screen, screen is ${GameTestCompat.screenName(it)}" } }

        context.worldBuilder().create().use { world ->
            ChunkWait.download(world)

            world.server.runCommand("gamemode creative @p") // no fall damage while teleporting around
            context.runOnClient<RuntimeException> {
                for (id in listOf("coverage", "waypoints")) ModuleManager.setEnabled(ModuleManager[id]!!, true)
            }

            // The profile persists between runs, so start from a clean slate.
            for (w in rpc(context, "waypoints.list").asJsonArray) rpc(context, "waypoints.delete", """{"id":"${w.asJsonObject["id"].asString}"}""")
            rpc(context, "coverage.clear", """{"dimension":"minecraft:overworld"}""")

            // "Fly" a diagonal path; coverage records the chunks the client has loaded around the player.
            for (i in 0..7) {
                world.server.runCommand("tp @p ${i * 90} 120 ${i * 50}")
                context.waitTicks(50)
            }
            context.waitTicks(20)

            val stats = rpc(context, "coverage.stats").asJsonArray
            check(stats.size() >= 1) { "coverage.stats returned nothing: $stats" }
            val chunks = stats[0].asJsonObject["chunks"].asInt
            check(chunks > 200) { "expected a trail of loaded chunks, got $chunks" }
            println("[gametest] coverage chunks=$chunks")

            // A raster of the trail has searched cells and the right shape.
            val view = rpc(context, "coverage.view", """{"dimension":"minecraft:overworld","x0":-20,"z0":-20,"w":60,"h":40,"step":1}""").asJsonObject
            val cells = java.util.Base64.getDecoder().decode(view["data"].asString)
            check(cells.size == 60 * 40) { "raster has ${cells.size} cells" }
            check(cells.count { it.toInt() != 0 } > 100) { "raster is almost empty" }

            // Drop waypoints with the real key binding.
            context.runOnClient<RuntimeException> { client ->
                GameTestCompat.bind(client, "key.relict.waypoints.drop", KeyCodes.B)
            }
            context.input.pressKey(KeyCodes.B)
            context.waitTicks(5)
            world.server.runCommand("tp @p 300 120 200")
            context.waitTicks(40)
            context.input.pressKey(KeyCodes.B)
            context.waitTicks(5)
            rpc(context, "waypoints.add", """{"name":"Suspicious torches","x":400,"y":70,"z":260,"dimension":"minecraft:overworld","tags":["base","sign"],"note":"Torches on the surface"}""")

            val list = rpc(context, "waypoints.list").asJsonArray
            check(list.size() == 3) { "expected 3 waypoints, got ${list.size()}: $list" }
            check(list[0].asJsonObject["name"].asString == "Find 1") { "first waypoint should be 'Find 1': $list" }
            check(list[1].asJsonObject["name"].asString == "Find 2") { "second waypoint should be 'Find 2': $list" }

            // The Donut modules: build a stash underground and check each one sees it.
            context.runOnClient<RuntimeException> {
                for (id in listOf("donut_regions", "base_finder", "live_players")) ModuleManager.setEnabled(ModuleManager[id]!!, true)
            }
            world.server.runCommand("tp @p 1000 -50 1000")
            context.waitTicks(80)
            val region = rpc(context, "donut_regions.info").asJsonObject["current"].asJsonObject
            check(region["label"].asString == "E5" && region["number"].asInt == 5) { "wrong region: $region" }
            check(region["group"].asString == "NA East") { "wrong region group: $region" }

            world.server.runCommand("fill 1000 -55 1000 1005 -55 1005 minecraft:hopper")
            world.server.runCommand("fill 1000 -54 1000 1005 -54 1005 minecraft:chest")
            context.waitTicks(120)
            val found = rpc(context, "base_finder.list").asJsonArray
            println("[gametest] base finder: $found")
            val stash = found.map { it.asJsonObject }.firstOrNull { it["kind"].asString == "Stash" }
            check(stash != null) { "Base Finder did not flag the stash: $found" }
            check(stash["distance"].asInt < 40) { "stash should be close: $stash" }
            check(stash["evidence"].asJsonArray.any { "hoppers" in it.asJsonObject["text"].asString }) { "no hopper evidence: $stash" }

            // A change in a chunk far from the player is a live signal; one right next to them (their own digging) is not.
            world.server.runCommand("tp @p 1000 -50 1100")
            context.waitTicks(100)
            world.server.runCommand("setblock 1002 -56 1002 minecraft:hopper")
            context.waitTicks(100)
            val live = rpc(context, "live_players.list").asJsonArray
            println("[gametest] live players: $live")
            check(live.any { it.asJsonObject["type"].asString == "activity" }) { "no activity signal after a distant change: $live" }
            val flagged = rpc(context, "base_finder.list").asJsonArray.map { it.asJsonObject }
            check(flagged.any { e -> e["evidence"].asJsonArray.any { "possible player here" in it.asJsonObject["text"].asString } }) { "no possible-player evidence: $flagged" }

            // Look at the stash from a clear spot: first without the overlays (the real blocks), then with them.
            world.server.runCommand("tp @p 1002.5 -58 985 facing 1002.5 -54.5 1002.5")
            context.waitTicks(80)
            context.takeScreenshot(TestScreenshotOptions.of("donut-1-plain").withSize(1280, 720))
            context.runOnClient<RuntimeException> {
                for (id in listOf("base_esp", "chunk_esp", "block_esp", "notifier")) ModuleManager.setEnabled(ModuleManager[id]!!, true)
            }
            // Chromium starts the first time something needs it; give the HUD page time to come up.
            repeat(80) { if (!WebHud.pageReady) context.waitTicks(10) }
            context.runOnClient<RuntimeException> { (ModuleManager["donut_regions"] as relict.modules.DonutRegionsModule).fakePingsForTest(48) }
            context.waitTicks(60)
            // What do the HUD pages cost? Average the game's frame rate over a few seconds with them on screen, then with them gone.
            fun fps(): Double {
                var sum = 0.0
                repeat(20) { context.waitTicks(5); context.runOnClient<RuntimeException> { sum += it.currentFps } }
                return sum / 20
            }
            val withHud = fps()
            context.runOnClient<RuntimeException> { for (id in listOf("donut_regions", "base_finder")) ModuleManager.setEnabled(ModuleManager[id]!!, false) }
            context.waitTicks(40)
            val withoutHud = fps()
            context.runOnClient<RuntimeException> { for (id in listOf("donut_regions", "base_finder")) ModuleManager.setEnabled(ModuleManager[id]!!, true) }
            context.waitTicks(40)
            println("[gametest] frame rate with the HUD pages ${"%.1f".format(withHud)}, without ${"%.1f".format(withoutHud)}")
            println("[gametest] base esp clusters: ${rpc(context, "base_esp.list")}")
            context.takeScreenshot(TestScreenshotOptions.of("donut-2-esp").withSize(1280, 720))

            // The map and finder HUDs and pop-ups are drawn by a web page; check it came up and shows a pop-up.
            context.runOnClient<RuntimeException> {
                println("[gametest] web hud: supported=${WebHud.supported} failed=${WebHud.failed} pageReady=${WebHud.pageReady}")
                GameHolder.game.showNotification("Possible player", "1000 1000 · S 15m · blocks changed here", "player")
                GameHolder.game.showNotification("Stash found", "91% · NW 15m · 1000 1000", "base")
            }
            // How often does the page get to animate? Count animation frames over one second and log them.
            context.runOnClient<RuntimeException> {
                WebHud.regions.runScript?.invoke("(function(){var n=0,t0=performance.now();function f(){n++;if(performance.now()-t0<1000)requestAnimationFrame(f);else console.log('HUD_FPS '+n)}requestAnimationFrame(f)})()")
            }
            context.waitTicks(14)
            context.takeScreenshot(TestScreenshotOptions.of("hud-3-toast-entering").withSize(1280, 720))
            context.waitTicks(40)
            context.takeScreenshot(TestScreenshotOptions.of("hud-4-toast").withSize(1280, 720))
            context.runOnClient<RuntimeException> { check(WebHud.pageReady) { "the HUD page never loaded" } }

            // From high above, the chunk outline sits on the world's floor and is bright; down at the stash it has faded.
            world.server.runCommand("tp @p 1008 120 1008 facing 1008 -64 1008")
            context.waitTicks(100)
            context.takeScreenshot(TestScreenshotOptions.of("esp-5-from-above").withSize(1280, 720))
            world.server.runCommand("tp @p 1002.5 -58 985 facing 1002.5 -54.5 1002.5")
            context.waitTicks(60)
            context.takeScreenshot(TestScreenshotOptions.of("esp-6-up-close").withSize(1280, 720))

            // Aim at the dot in the middle of the stash chunk: its card shows next to the crosshair.
            world.server.runCommand("tp @p 1000.5 -58 980 facing 1000.5 -63.8 1000.5")
            context.waitTicks(60)
            context.takeScreenshot(TestScreenshotOptions.of("esp-7-aim").withSize(1280, 720))

            // Open the GUI with the real key, in the world.
            context.input.pressKey(KeyCodes.RIGHT_SHIFT)
            context.waitForScreen(BrowserScreen::class.java)
            context.waitTicks(80) // let the page load and fetch its data

            context.runOnClient<RuntimeException> {
                val b = Bridge.active!!.cef
                b.executeJavaScript("console.log('VIEWPORT ' + innerWidth + 'x' + innerHeight + ' dpr=' + devicePixelRatio + ' win=' + outerWidth + 'x' + outerHeight + ' pointerEvents=' + getComputedStyle(document.body).pointerEvents + ' clickable=' + (document.elementFromPoint(innerWidth / 2, innerHeight / 2)?.closest('.window') != null) + ' window=' + JSON.stringify(document.querySelector('.window')?.getBoundingClientRect()) + ' stage=' + JSON.stringify(document.querySelector('.stage')?.getBoundingClientRect()) + ' body=' + document.body.scrollHeight)", b.url, 0)
            }
            context.waitTicks(10)
            for (page in listOf("overview", "map", "waypoints", "modules", "settings", "theme")) {
                context.runOnClient<RuntimeException> {
                    val b = Bridge.active!!.cef
                    b.executeJavaScript("location.hash = '#$page'", b.url, 0)
                }
                context.waitTicks(if (page == "map") 90 else 30)
                context.takeScreenshot(TestScreenshotOptions.of("ui-$page").withSize(1280, 720))
            }

            // The open key closes the window again (after its closing animation), and so does Esc.
            context.runOnClient<RuntimeException> {
                val b = WebHud.regions.runScript
                b?.invoke("console.log('HUD_VIEWPORT ' + innerWidth + 'x' + innerHeight)")
            }
            context.input.pressKey(KeyCodes.RIGHT_SHIFT)
            context.waitTicks(14)
            context.runOnClient<RuntimeException> { check(GameTestCompat.screenName(it) == null) { "Right Shift should close the GUI, screen is ${GameTestCompat.screenName(it)}" } }

            // Reopening is instant: the page was kept loaded in the background.
            context.input.pressKey(KeyCodes.RIGHT_SHIFT)
            context.waitForScreen(BrowserScreen::class.java)
            context.waitTicks(20)
            context.takeScreenshot(TestScreenshotOptions.of("ui-reopened").withSize(1280, 720))
            context.input.pressKey(KeyCodes.ESCAPE)
            context.waitTicks(14)
            context.runOnClient<RuntimeException> { check(GameTestCompat.screenName(it) == null) { "Esc should return to the world, screen is ${GameTestCompat.screenName(it)}" } }
        }
    }

    private fun rpc(context: ClientGameTestContext, method: String, params: String = "{}"): JsonElement {
        var result: JsonElement? = null
        context.runOnClient<RuntimeException> { result = JsonParser.parseString(Bridge.api.handle("""{"method":"$method","params":$params}""")) }
        return result!!
    }
}
