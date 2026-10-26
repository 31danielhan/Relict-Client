package relict.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Reads and writes the global config file (module on/off state and settings).
 * A corrupt file is moved aside to `<name>.bad` and replaced with defaults instead of failing startup.
 */
class ConfigStore(private val file: Path) {
    private val logger = LoggerFactory.getLogger("Relict")

    fun load(): JsonObject {
        if (!Files.exists(file)) return JsonObject()
        return try {
            JsonParser.parseString(Files.readString(file)).asJsonObject
        } catch (e: Exception) {
            val bad = file.resolveSibling(file.fileName.toString() + ".bad")
            logger.warn("Config {} is unreadable ({}); moving it to {} and using defaults", file, e.message, bad)
            runCatching { Files.move(file, bad, StandardCopyOption.REPLACE_EXISTING) }
            JsonObject()
        }
    }

    fun save(root: JsonObject) {
        Json.writeAtomic(file, Json.pretty.toJson(root).toByteArray())
    }
}
