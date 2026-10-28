package relict

import com.google.gson.JsonObject
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import relict.core.ClientSettings
import relict.core.KeyCodes
import relict.core.KeyHandle
import relict.core.Keys
import relict.core.Module
import relict.core.ModuleManager
import relict.game.GameHolder
import relict.modules.BaseEspModule
import relict.modules.BaseFinderModule
import relict.modules.BlockEspModule
import relict.modules.ChunkEspModule
import relict.modules.CoverageModule
import relict.modules.DebugModule
import relict.modules.DonutRegionsModule
import relict.modules.LivePlayersModule
import relict.modules.NotifierModule
import relict.modules.WaypointsModule
import relict.render.Inspector
import relict.render.Render
import relict.render.RenderHooks
import relict.render.WebHud
import relict.ui.Api
import relict.ui.ApiHost
import relict.ui.Bridge
import relict.ui.Mcef
import relict.ui.UiAssets

object RelictClient : ClientModInitializer {
    const val MOD_ID = "relict"
    private const val POSITION_INTERVAL = 4
    private const val WARM_UP_TICKS = 100 // five seconds in a world
    val logger = LoggerFactory.getLogger("Relict")

    private val openUi: KeyHandle = Keys.register("key.relict.open_ui", KeyCodes.RIGHT_SHIFT)

    /** The key that opens the GUI. The open GUI also closes on it (unless a text field has focus). */
    val openKey: KeyHandle get() = openUi

    override fun onInitializeClient() {
        GameHolder.game = GameImpl
        val loader = FabricLoader.getInstance()
        val version = loader.getModContainer(MOD_ID).get().metadata.version.friendlyString
        logger.info("Relict {} initialised", version)

        ModuleManager.register(CoverageModule())
        ModuleManager.register(WaypointsModule())
        ModuleManager.register(DonutRegionsModule())
        ModuleManager.register(BaseFinderModule())
        ModuleManager.register(LivePlayersModule())
        ModuleManager.register(NotifierModule())
        ModuleManager.register(BaseEspModule())
        ModuleManager.register(BlockEspModule())
        ModuleManager.register(ChunkEspModule())
        if (loader.isDevelopmentEnvironment) ModuleManager.register(DebugModule())
        ModuleManager.init(
            configFile = loader.configDir.resolve("relict.json"),
            profilesDir = loader.gameDir.resolve(MOD_ID).resolve("profiles"),
        )

        Bridge.api = Api(object : ApiHost {
            override val modules get() = ModuleManager.all
            override fun setEnabled(module: Module, enabled: Boolean) = ModuleManager.setEnabled(module, enabled)
            override fun info() = JsonObject().apply {
                addProperty("version", version)
                addProperty("minecraft", loader.getModContainer("minecraft").get().metadata.version.friendlyString)
                addProperty("mcef", Mcef.status)
                addProperty("dev", loader.isDevelopmentEnvironment)
                addProperty("uiKey", openUi.displayName)
            }
        })
        ModuleManager.changeListeners += { Bridge.emit("module.changed", it.describe()) }
        ModuleManager.eventSink = Bridge::emit

        UiAssets.extract()
        // Chromium is not started here: it costs memory and start-up time, so it waits until something needs it (the GUI key, a HUD or a pop-up).
        ExitGuard.install()
        RenderHooks.install()

        ScreenKeys.install(openUi, ::openGui)

        var uiTicks = 0
        ClientTickEvents.END_CLIENT_TICK.register {
            if (++uiTicks % POSITION_INTERVAL == 0) pushPosition()
            if (WebHud.supported) { pushHudState(); Inspector.tick(GameHolder.game.player(), Render.freshCamera()) }
            warmUpBrowser()
            if (Mcef.ready) Mcef.preloadGui()
            while (openUi.consumePress()) openGui()
            if (openWhenReady) openGui()
        }
    }

    private var hudTicks = 0
    private var lastBlockX = Int.MIN_VALUE
    private var lastBlockZ = Int.MIN_VALUE

    /**
     * Feeds the HUD pages what they need: where the player is, and the look the player picked. Every change makes a page repaint
     * and the game copy that page to the GPU, so this is sent as rarely as is still smooth: the position four times a second and
     * only when the player has moved to another block (the markers glide between positions by themselves), the settings every second.
     */
    private fun pushHudState() {
        hudTicks++
        if (hudTicks % 4 == 0) {
            val player = GameHolder.game.player()
            if (player == null) {
                if (lastBlockX != Int.MIN_VALUE) { WebHud.set("player", null); lastBlockX = Int.MIN_VALUE; lastBlockZ = Int.MIN_VALUE }
            } else if (player.blockX != lastBlockX || player.blockZ != lastBlockZ) {
                lastBlockX = player.blockX
                lastBlockZ = player.blockZ
                WebHud.set("player", JsonObject().apply { addProperty("x", player.blockX); addProperty("z", player.blockZ) })
            }
        }
        if (hudTicks % 20 == 0) {
            WebHud.set("config", JsonObject().apply {
                addProperty("accent", ClientSettings.accent.value.name)
                addProperty("reducedMotion", ClientSettings.reducedMotion.value)
            })
        }
    }

    /** Keeps the open UI's player marker current. */
    private fun pushPosition() {
        if (Bridge.active == null) return
        val player = GameHolder.game.player() ?: return
        Bridge.emit("player.position", JsonObject().apply {
            addProperty("x", player.x)
            addProperty("y", player.y)
            addProperty("z", player.z)
            addProperty("yaw", player.yaw)
            addProperty("dimension", player.dimension)
        })
    }

    /** Opens the GUI over whatever is showing; closing it returns there. */
    private fun openGui() {
        val game = GameHolder.game
        if (game.isGuiOpen()) { openWhenReady = false; return }
        Mcef.start()
        if (Mcef.failed) {
            openWhenReady = false
            game.showToast("Relict", "The browser could not start. See the log.")
            return
        }
        if (!Mcef.ready) {
            // Chromium is still starting: open the window the moment it is up instead of asking for another key press.
            if (!openWhenReady) {
                logger.info("Open key pressed but the browser is not ready ({}); opening when it is", Mcef.status)
                game.showToast("Relict", "Starting the browser (${Mcef.status}). The window opens when it is ready.")
            }
            openWhenReady = true
            return
        }
        openWhenReady = false
        game.openGui(UiAssets.url("index.html"))
    }

    /** Set when the key was pressed before Chromium was ready. */
    private var openWhenReady = false

    private var ticksInWorld = 0

    /**
     * Chromium is left alone until it is needed, but the player is likely to want the window soon after joining a world,
     * so it starts a few seconds after that (not during the world loading, which it would slow down).
     */
    private fun warmUpBrowser() {
        if (GameHolder.game.player() == null) { ticksInWorld = 0; return }
        if (ticksInWorld <= WARM_UP_TICKS && ++ticksInWorld == WARM_UP_TICKS) Mcef.start()
    }
}
