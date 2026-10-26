package relict.core

import com.google.gson.reflect.TypeToken
import java.nio.file.Files
import java.nio.file.Path

/** Turns a server address or world name into a safe directory name. */
object ProfileKey {
    private val unsafe = Regex("[^a-z0-9._-]")

    fun server(address: String): String = "srv-" + sanitize(address)

    fun singleplayer(levelName: String): String = "sp-" + sanitize(levelName)

    private fun sanitize(raw: String): String =
        raw.trim().lowercase().replace(unsafe, "_").trim('.', '_').take(64).ifEmpty { "unnamed" }
}

/**
 * Data belonging to one server (or singleplayer world): waypoints, coverage, logs.
 * Modules read and write named files here; writes are atomic.
 */
class ProfileStore(val key: String, val dir: Path) {
    fun path(name: String): Path = dir.resolve(name)

    fun exists(name: String): Boolean = Files.exists(path(name))

    fun readBytes(name: String): ByteArray? = path(name).takeIf { Files.exists(it) }?.let { Files.readAllBytes(it) }

    fun writeBytes(name: String, bytes: ByteArray) = Json.writeAtomic(path(name), bytes)

    /** Returns [default] if the file is missing or unreadable. */
    inline fun <reified T : Any> readJson(name: String, default: () -> T): T {
        val bytes = readBytes(name) ?: return default()
        return try {
            Json.compact.fromJson<T>(String(bytes), object : TypeToken<T>() {}.type) ?: default()
        } catch (_: Exception) {
            default()
        }
    }

    fun writeJson(name: String, value: Any) = writeBytes(name, Json.compact.toJson(value).toByteArray())
}

/** Tracks which profile is open. Opened on joining a server or world, closed on leaving. */
class Storage(private val root: Path) {
    var current: ProfileStore? = null
        private set

    fun open(key: String): ProfileStore {
        val store = ProfileStore(key, root.resolve(key))
        Files.createDirectories(store.dir)
        current = store
        return store
    }

    fun close() {
        current = null
    }
}
