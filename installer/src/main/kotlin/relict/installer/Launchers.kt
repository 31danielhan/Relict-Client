package relict.installer

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory

enum class LauncherKind(val label: String) {
    VANILLA("Minecraft Launcher"),
    PRISM("Prism Launcher"),
    POLYMC("PolyMC"),
    MODRINTH("Modrinth App"),
    CUSTOM("Custom folder"),
}

/**
 * Somewhere Relict can be installed. For the official launcher that is its data folder (the installer adds a profile
 * there); for every other launcher it is one instance's game folder, the one with `mods/` in it.
 */
data class Target(
    val kind: LauncherKind,
    val name: String,
    /** The Minecraft version this instance runs, or null when that isn't known and the player picks one. */
    val minecraft: String?,
    /** fabric, forge, neoforge, quilt or vanilla; null when unknown. */
    val loader: String?,
    val loaderVersion: String?,
    val dir: Path,
) {
    val isInstance: Boolean get() = kind != LauncherKind.VANILLA
    val label: String get() = if (kind == LauncherKind.VANILLA) kind.label else "${kind.label} · $name"
    val hasFabric: Boolean get() = loader == "fabric"

    /** Why Relict can't work in this instance, or null if nothing is known to be wrong. */
    fun fabricProblem(minLoader: String = MIN_FABRIC_LOADER): String? = when {
        loader == null || kind == LauncherKind.VANILLA -> null
        loader != "fabric" -> "This instance doesn't use Fabric. Add Fabric Loader to it in ${kind.label} first."
        loaderVersion != null && Versions.compare(loaderVersion, minLoader) < 0 ->
            "Its Fabric Loader $loaderVersion is older than the $minLoader Relict needs. Update it in ${kind.label}."
        else -> null
    }

    companion object {
        /** What fabric.mod.json asks of Fabric Loader. */
        const val MIN_FABRIC_LOADER = "0.18.4"
    }
}

/** What the detection needs to know about the computer, so it can be tried against made-up folders. */
class LauncherEnv(val os: String, val home: Path, val appData: Path?, val xdgData: Path?) {
    val key: String get() = InstallerInfo.osKey(os)

    /** The folder most launchers keep their data in. */
    val dataRoot: Path
        get() = when (key) {
            "windows" -> appData ?: home.resolve("AppData").resolve("Roaming")
            "mac" -> home.resolve("Library").resolve("Application Support")
            else -> xdgData ?: home.resolve(".local").resolve("share")
        }

    companion object {
        fun system() = LauncherEnv(
            System.getProperty("os.name"),
            Path.of(System.getProperty("user.home")),
            System.getenv("APPDATA")?.let { Path.of(it) },
            System.getenv("XDG_DATA_HOME")?.let { Path.of(it) },
        )
    }
}

/** Finds the Minecraft launchers installed on this computer and the instances in them. */
object Launchers {
    fun detect(env: LauncherEnv = LauncherEnv.system()): List<Target> = buildList {
        vanilla(env)?.let { add(it) }
        addAll(prismLike(env, LauncherKind.PRISM, listOf("PrismLauncher"), "prismlauncher.cfg",
            listOf(env.home.resolve(".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher"))))
        addAll(prismLike(env, LauncherKind.POLYMC, listOf("PolyMC"), "polymc.cfg", emptyList()))
        addAll(modrinth(env))
    }

    private fun vanilla(env: LauncherEnv): Target? {
        val dir = MinecraftDir.default(env.os, env.home.toString(), env.appData?.toString())
        if (!Files.isDirectory(dir)) return null
        return Target(LauncherKind.VANILLA, LauncherKind.VANILLA.label, null, null, null, dir)
    }

    /** The Minecraft version of the profile the player used last, if the launcher's profile file says. */
    fun lastPlayedVersion(mcDir: Path): String? = try {
        val profiles = JsonParser.parseString(Files.readString(mcDir.resolve("launcher_profiles.json"))).asJsonObject.getAsJsonObject("profiles")
        profiles.entrySet().mapNotNull { (_, p) ->
            val o = p.asJsonObject
            val id = o.get("lastVersionId")?.asString ?: return@mapNotNull null
            val version = if (id.startsWith("fabric-loader-")) id.substringAfterLast('-') else id
            (o.get("lastUsed")?.asString ?: "") to version
        }.filter { (_, v) -> v.firstOrNull()?.isDigit() == true }.maxByOrNull { it.first }?.second
    } catch (_: Exception) {
        null
    }

    // ---- Prism Launcher, PolyMC and other MultiMC-style launchers ---------------------------

    private fun prismLike(env: LauncherEnv, kind: LauncherKind, names: List<String>, cfg: String, extra: List<Path>): List<Target> {
        val dataDirs = names.map { env.dataRoot.resolve(it) } + extra
        return dataDirs.filter { Files.isDirectory(it) }.flatMap { data ->
            val instances = instancesDir(data, cfg)
            if (!Files.isDirectory(instances)) return@flatMap emptyList()
            Files.list(instances).use { it.toList() }
                .filter { Files.isDirectory(it) && (Files.exists(it.resolve("instance.cfg")) || Files.exists(it.resolve("mmc-pack.json"))) }
                .sortedBy { it.fileName.toString().lowercase() }
                .map { prismInstance(kind, it) }
        }
    }

    /** The launcher lets people move the instances folder; its config file says where (relative to its data folder). */
    private fun instancesDir(data: Path, cfg: String): Path {
        val configured = iniValue(data.resolve(cfg), "InstanceDir") ?: "instances"
        return data.resolve(configured)
    }

    private fun prismInstance(kind: LauncherKind, dir: Path): Target {
        val name = iniValue(dir.resolve("instance.cfg"), "name") ?: dir.fileName.toString()
        var minecraft: String? = null
        var loader = "vanilla"
        var loaderVersion: String? = null
        try {
            val components = JsonParser.parseString(Files.readString(dir.resolve("mmc-pack.json"))).asJsonObject.getAsJsonArray("components")
            for (c in components) {
                val o = c.asJsonObject
                val version = o.get("version")?.asString ?: o.get("cachedVersion")?.asString
                when (o.get("uid")?.asString) {
                    "net.minecraft" -> minecraft = version
                    "net.fabricmc.fabric-loader" -> { loader = "fabric"; loaderVersion = version }
                    "net.minecraftforge" -> loader = "forge"
                    "net.neoforged" -> loader = "neoforge"
                    "org.quiltmc.quilt-loader" -> loader = "quilt"
                }
            }
        } catch (_: Exception) {
            loader = ""
        }
        val gameDir = listOf("minecraft", ".minecraft").map { dir.resolve(it) }.firstOrNull { Files.isDirectory(it) } ?: dir.resolve("minecraft")
        return Target(kind, name, minecraft, loader.ifEmpty { null }, loaderVersion, gameDir)
    }

    private fun iniValue(file: Path, key: String): String? = try {
        Files.readAllLines(file).firstNotNullOfOrNull { line -> line.takeIf { it.startsWith("$key=") }?.substringAfter('=')?.trim()?.takeIf { it.isNotEmpty() } }
    } catch (_: Exception) {
        null
    }

    // ---- Modrinth App -----------------------------------------------------------------------

    private class ModrinthRow(val name: String?, val gameVersion: String?, val loader: String?, val loaderVersion: String?)

    private fun modrinth(env: LauncherEnv): List<Target> {
        val dirs = listOf("com.modrinth.theseus", "ModrinthApp").map { env.dataRoot.resolve(it) } +
            listOf(env.home.resolve(".var/app/com.modrinth.ModrinthApp/data/ModrinthApp"))
        return dirs.filter { Files.isDirectory(it.resolve("profiles")) }.flatMap { data ->
            val rows = modrinthRows(data)
            Files.list(data.resolve("profiles")).use { it.toList() }
                .filter { Files.isDirectory(it) }
                .sortedBy { it.fileName.toString().lowercase() }
                .map { dir ->
                    val row = rows[dir.fileName.toString()]
                    Target(LauncherKind.MODRINTH, row?.name ?: dir.fileName.toString(), row?.gameVersion, row?.loader?.lowercase(), row?.loaderVersion, dir)
                }
        }
    }

    /**
     * The app keeps its profiles (name, Minecraft version, mod loader) in a SQLite database. It is read from a copy, so a
     * running app's file is never touched. If anything about it is unexpected the profile folders are still listed, just
     * without versions, and the player picks one.
     */
    private fun modrinthRows(data: Path): Map<String, ModrinthRow> {
        val db = data.resolve("app.db")
        if (!Files.isRegularFile(db)) return emptyMap()
        val temp = createTempDirectory("relict-modrinth")
        return try {
            for (suffix in listOf("", "-wal", "-shm")) {
                val source = data.resolve("app.db$suffix")
                if (Files.isRegularFile(source)) Files.copy(source, temp.resolve("app.db$suffix"))
            }
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${temp.resolve("app.db")}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT path, name, game_version, mod_loader, mod_loader_version FROM profiles").use { rs ->
                        buildMap { while (rs.next()) put(rs.getString(1), ModrinthRow(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5))) }
                    }
                }
            }
        } catch (_: Throwable) {
            emptyMap()
        } finally {
            temp.toFile().deleteRecursively()
        }
    }
}
