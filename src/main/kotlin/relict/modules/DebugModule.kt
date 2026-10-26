package relict.modules

import relict.RelictClient
import relict.core.KeyCodes
import relict.core.Module
import relict.core.ProfileStore

/** Exercises the module framework (settings, keybinds, profiles). Only registered in the dev environment. */
class DebugModule : Module("debug", "Debug", "Developer test module for the module framework.") {
    enum class Mode { QUIET, CHATTY }

    val interval = settings.int("interval", "Log interval (seconds)", 5, 1..60)
    val mode = settings.enum("mode", "Mode", Mode.QUIET)
    val label = settings.string("label", "Label", "relict", maxLength = 24)

    private var ticks = 0

    init {
        bind("ping", KeyCodes.UNBOUND) { RelictClient.logger.info("[debug:{}] ping", label.value) }
    }

    override fun onEnable() = RelictClient.logger.info("[debug:{}] enabled", label.value)
    override fun onDisable() = RelictClient.logger.info("[debug:{}] disabled", label.value)

    override fun onProfileOpen(store: ProfileStore) = RelictClient.logger.info("[debug:{}] profile open: {}", label.value, store.key)
    override fun onProfileClose(store: ProfileStore) = RelictClient.logger.info("[debug:{}] profile closed: {}", label.value, store.key)

    override fun onTick() {
        if (mode.value == Mode.QUIET) return
        if (++ticks % (interval.value * 20) == 0) RelictClient.logger.info("[debug:{}] {} ticks", label.value, ticks)
    }
}
