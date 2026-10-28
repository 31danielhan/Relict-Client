package relict.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import relict.game.GameHolder
import relict.game.ProfileSource
import relict.donut.ChunkMemory
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Owns every module: registration, persisted state, key handling and event dispatch.
 * All enabling and disabling goes through here so profile hooks stay consistent.
 */
object ModuleManager {
    private val logger = LoggerFactory.getLogger("Relict")
    private const val SAVE_INTERVAL_TICKS = 40
    private const val SLOW_TICK_NANOS = 8_000_000L
    private const val MEMORY_SAVE_TICKS = 1200 // a minute

    private val modules = LinkedHashMap<String, Module>()
    private val toggleKeys = mutableListOf<Pair<Module, KeyHandle>>()
    private val actionKeys = mutableListOf<Triple<Module, Bind, KeyHandle>>()

    private lateinit var config: ConfigStore
    private lateinit var configRoot: JsonObject
    lateinit var storage: Storage
        private set

    private var dirty = false
    private var ticksSinceSave = 0

    /** Notified when a module's state or settings change, from any source (keybind, UI, code). */
    val changeListeners = mutableListOf<(Module) -> Unit>()

    /** Where module events (see [Module.emit]) go. Set to the UI bridge at startup. */
    var eventSink: ((String, JsonElement) -> Unit)? = null

    val all: List<Module> get() = modules.values.toList()

    operator fun get(id: String): Module? = modules[id]

    /** Call before [init]; key bindings must exist before the game loads its options. */
    fun register(module: Module) {
        require(module.id !in modules) { "Duplicate module id '${module.id}'" }
        modules[module.id] = module
        toggleKeys += module to Keys.register("key.relict.${module.id}.toggle")
        module.binds.forEach { bind ->
            actionKeys += Triple(module, bind, Keys.register("key.relict.${module.id}.${bind.action}", bind.defaultKey))
        }
    }

    fun init(configFile: Path, profilesDir: Path) {
        config = ConfigStore(configFile)
        storage = Storage(profilesDir)
        configRoot = config.load()

        val saved = configRoot.getAsJsonObject("modules")
        for (module in modules.values) {
            saved?.get(module.id)?.takeIf { it.isJsonObject }?.let { module.loadConfig(it.asJsonObject) }
            module.onChange = {
                dirty = true
                changeListeners.forEach { it(module) }
            }
        }
        configRoot.get("client")?.takeIf { it.isJsonObject }?.let { ClientSettings.group.load(it.asJsonObject) }
        ClientSettings.group.onChange = { dirty = true }

        // The handlers ignore Fabric's Minecraft-typed arguments, so they compile under any naming scheme.
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> openProfile() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> closeProfile() }
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            closeProfile() // lets modules write their data
            save()
        }
    }

    fun setEnabled(module: Module, enabled: Boolean) {
        if (module.enabled == enabled) return
        val profile = storage.current
        if (!enabled && profile != null) guarded(module, "onProfileClose") { module.onProfileClose(profile) }
        module.setEnabled(enabled)
        if (enabled && module.enabled && profile != null) guarded(module, "onProfileOpen") { module.onProfileOpen(profile) }
    }

    fun toggle(module: Module) = setEnabled(module, !module.enabled)

    /** Writes the config now. Normally happens automatically a moment after a change. */
    fun save() {
        val saved = JsonObject()
        val existing = configRoot.getAsJsonObject("modules")
        existing?.entrySet()?.forEach { (id, state) -> if (id !in modules) saved.add(id, state) } // keep config of modules this build lacks
        modules.values.forEach { saved.add(it.id, it.toConfig()) }
        configRoot.addProperty("version", 1)
        configRoot.add("modules", saved)
        configRoot.add("client", ClientSettings.group.toJson())
        try {
            config.save(configRoot)
            dirty = false
        } catch (e: Exception) {
            logger.error("Could not save Relict config", e)
        }
    }

    private fun tick() {
        for ((module, key) in toggleKeys) while (key.consumePress()) toggle(module)
        for ((module, bind, key) in actionKeys) {
            while (key.consumePress()) if (module.enabled) guarded(module, "key '${bind.action}'") { bind.onPress() }
        }
        for (module in modules.values) if (module.enabled) {
            val start = System.nanoTime()
            guarded(module, "onTick") { module.onTick() }
            noteTickTime(module, System.nanoTime() - start)
        }

        if (ChunkMemory.dirty && ++memoryTicks >= MEMORY_SAVE_TICKS) {
            memoryTicks = 0
            storage.current?.let { saveChunkMemory(it) }
        }
        if (dirty && ++ticksSinceSave >= SAVE_INTERVAL_TICKS) {
            ticksSinceSave = 0
            save()
        }
    }

    private const val CHUNK_MEMORY_FILE = "chunk-memory.json"
    private var memoryTicks = 0

    private fun saveChunkMemory(store: ProfileStore) {
        if (!ChunkMemory.dirty) return
        try {
            store.writeBytes(CHUNK_MEMORY_FILE, ChunkMemory.toJson().toString().toByteArray())
        } catch (e: Exception) {
            logger.warn("Could not save {}", CHUNK_MEMORY_FILE, e)
        }
    }

    private val lastSlowLog = HashMap<String, Long>()

    /** A module that holds up the game thread for long shows as a stutter, so say so (at most every ten seconds per module). */
    private fun noteTickTime(module: Module, nanos: Long) {
        if (nanos < SLOW_TICK_NANOS) return
        val now = System.currentTimeMillis()
        if (now - (lastSlowLog[module.id] ?: 0L) < 10_000) return
        lastSlowLog[module.id] = now
        logger.warn("Module '{}' took {} ms in one tick", module.id, nanos / 1_000_000)
    }

    private fun openProfile() {
        val key = when (val source = GameHolder.game.currentProfile()) {
            is ProfileSource.Server -> ProfileKey.server(source.address)
            is ProfileSource.SingleplayerWorld -> ProfileKey.singleplayer(source.folder)
            null -> "unknown"
        }
        val store = storage.open(key)
        logger.info("Opened profile {}", key)
        // What the Base Finder last saw underground, kept for when the server stops sending it (you are above Y 0).
        ChunkMemory.clear()
        store.readBytes(CHUNK_MEMORY_FILE)?.let { bytes ->
            runCatching { ChunkMemory.load(com.google.gson.JsonParser.parseString(String(bytes)).asJsonObject) }
                .onFailure { logger.warn("Could not read {}", CHUNK_MEMORY_FILE, it) }
        }
        modules.values.filter { it.enabled }.forEach { guarded(it, "onProfileOpen") { it.onProfileOpen(store) } }
    }

    private fun closeProfile() {
        val store = storage.current ?: return
        saveChunkMemory(store)
        modules.values.filter { it.enabled }.forEach { guarded(it, "onProfileClose") { it.onProfileClose(store) } }
        storage.close()
    }

    /** One misbehaving module must not crash the game or stop the others. */
    private inline fun guarded(module: Module, what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logger.error("Module '{}' failed in {}; disabling it", module.id, what, e)
            module.setEnabled(false)
        }
    }
}
