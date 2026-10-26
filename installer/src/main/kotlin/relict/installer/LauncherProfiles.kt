package relict.installer

import com.google.gson.JsonObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Edits the official launcher's launcher_profiles.json, leaving everything else in it untouched. */
object LauncherProfiles {
    // The launcher writes e.g. 2024-01-01T00:00:00.000Z: always three fractional digits.
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun addProfile(root: JsonObject, key: String, name: String, versionId: String, gameDir: String?, now: Instant) {
        val profiles = root.getAsJsonObject("profiles") ?: JsonObject().also { root.add("profiles", it) }
        val existing = profiles.getAsJsonObject(key)
        val profile = JsonObject()
        profile.addProperty("created", existing?.get("created")?.asString ?: TIME.format(now))
        profile.addProperty("lastUsed", TIME.format(now))
        profile.addProperty("icon", "Crafting_Table")
        profile.addProperty("lastVersionId", versionId)
        profile.addProperty("name", name)
        profile.addProperty("type", "custom")
        if (gameDir != null) profile.addProperty("gameDir", gameDir)
        profiles.add(key, profile)
    }

    /** Returns true if a profile was removed. */
    fun removeProfile(root: JsonObject, key: String): Boolean = root.getAsJsonObject("profiles")?.remove(key) != null

    fun hasProfile(root: JsonObject, key: String): Boolean = root.getAsJsonObject("profiles")?.has(key) == true
}
