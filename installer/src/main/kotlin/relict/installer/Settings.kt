package relict.installer

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path

/** What the installer remembers between runs. */
data class Settings(
    /** Look for a newer Relict when the installer opens. */
    val checkOnLaunch: Boolean = true,
    /** Install a newer Relict right away, for the versions that are already installed. Off: show a button instead. */
    val autoUpdate: Boolean = true,
) {
    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()

        fun defaultFile(): Path = Path.of(System.getProperty("user.home"), ".relict-installer", "settings.json")

        /** Never fails: missing or damaged settings just mean the defaults. */
        fun load(file: Path = defaultFile()): Settings = try {
            gson.fromJson(Files.readString(file), Settings::class.java) ?: Settings()
        } catch (_: Exception) {
            Settings()
        }
    }

    fun save(file: Path = defaultFile()) {
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(this))
        } catch (_: Exception) {
            // Losing a preference is not worth an error dialog.
        }
    }
}
