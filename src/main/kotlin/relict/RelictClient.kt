package relict

import com.google.gson.JsonObject
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
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
import relict.modules.WaypointsModule
import relict.render.RenderHooks
import relict.ui.Api
import relict.ui.ApiHost
import relict.ui.Bridge
import relict.ui.Mcef
import relict.ui.UiAssets

object RelictClient : ClientModInitializer {
    const val MOD_ID = "relict"
    private const val POSITION_INTERVAL = 4
    val logger = LoggerFactory.getLogger("Relict")

    private val openUi: KeyHandle = Keys.register("key.relict.open_ui", KeyCodes.RIGHT_SHIFT)

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
        Mcef.start()
        ExitGuard.install()
        RenderHooks.install()

        ScreenKeys.install(openUi, ::openGui)

        var uiTicks = 0
        ClientTickEvents.END_CLIENT_TICK.register {
            if (++uiTicks % POSITION_INTERVAL == 0) pushPosition()
            while (openUi.consumePress()) openGui()
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
        if (game.isGuiOpen()) return
        if (!Mcef.ready) {
            logger.info("Open key pressed but the browser is not ready ({})", Mcef.status)
            game.showToast("Relict", "The browser is still starting (${Mcef.status}).")
            return
        }
        game.openGui(UiAssets.url("index.html"))
    }
}
