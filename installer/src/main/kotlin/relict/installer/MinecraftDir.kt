package relict.installer

import java.nio.file.Files
import java.nio.file.Path

object MinecraftDir {
    /** Where the official launcher keeps its data on this operating system. */
    fun default(os: String = System.getProperty("os.name"), home: String = System.getProperty("user.home"), appData: String? = System.getenv("APPDATA")): Path {
        val name = os.lowercase()
        return when {
            "win" in name -> Path.of(appData ?: "$home\\AppData\\Roaming", ".minecraft")
            "mac" in name -> Path.of(home, "Library", "Application Support", "minecraft")
            else -> Path.of(home, ".minecraft")
        }
    }

    /** The launcher creates launcher_profiles.json on first run; without it there is nothing to add a profile to. */
    fun isValid(dir: Path): Boolean = Files.isRegularFile(dir.resolve("launcher_profiles.json"))
}
