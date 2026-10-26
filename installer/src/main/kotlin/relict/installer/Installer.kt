package relict.installer

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Clock

/** Where Relict goes: its own instance (leaves the player's mods and settings alone), the main Minecraft folder, or another launcher's instance. */
enum class Layout { SEPARATE_INSTANCE, MAIN_FOLDER, /** An instance of another launcher: mods go into its own mods folder. */ INSTANCE }

class InstallException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class Status(val installedVersion: String?, val needsUpdate: Boolean) {
    val installed: Boolean get() = installedVersion != null
}

/** What was installed, so it can be removed or updated later without guessing. */
private data class InstallRecord(
    /** (Installs made before the rename saved this as veylyVersion.) */
    @SerializedName(value = "relictVersion", alternate = ["veylyVersion"])
    val relictVersion: String,
    val layout: Layout,
    val gameDir: String,
    val fabricVersionId: String,
    val profileKey: String,
    val createdVersionDir: Boolean,
    val files: List<String>, // relative to gameDir
)

/**
 * Installs Relict into a Minecraft launcher: the Fabric loader profile, the dependency jars and the Relict jar.
 * Everything is downloaded and verified before anything on disk is changed.
 */
class Installer(
    private val manifest: Manifest,
    private val downloader: Downloader,
    /** Reads a Relict jar that ships inside the installer. */
    private val bundledJar: (String) -> ByteArray,
    private val clock: Clock = Clock.systemUTC(),
    /** Hosts allowed besides the built-in ones: the update feed's host, when the manifest came from one. */
    private val extraHosts: Set<String> = emptySet(),
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun status(entry: VersionEntry, mcDir: Path): Status {
        val file = recordFile(entry, mcDir)
        // An install made under the old name always counts as behind: its jar and profile still carry the old name.
        return statusOf(readRecord(file), behind = file == legacyRecordPath(entry, mcDir))
    }

    private fun statusOf(record: InstallRecord?, behind: Boolean = false): Status {
        record ?: return Status(null, false)
        val gameDir = Path.of(record.gameDir)
        val complete = record.files.all { Files.exists(gameDir.resolve(it)) }
        return Status(record.relictVersion, behind || record.relictVersion != manifest.relictVersion || !complete)
    }

    fun install(entry: VersionEntry, mcDir: Path, layout: Layout, log: (String) -> Unit = {}) {
        if (!MinecraftDir.isValid(mcDir)) {
            throw InstallException("This doesn't look like a Minecraft folder. Open the Minecraft Launcher once, then try again.")
        }
        require(layout != Layout.INSTANCE) { "Use the instance overload for another launcher's instance" }

        // 1. Fetch and verify everything before touching the disk.
        val prepared = prepare(entry, withFabricProfile = true, log)

        // The old copy goes only now, so a failed download or check above leaves a working install in place.
        val previous = readRecord(recordFile(entry, mcDir))
        if (previous != null) {
            log("Removing the previous install first")
            uninstall(entry, mcDir, log)
        }

        // 2. Write. An earlier separate instance is reused, so its worlds and settings carry on.
        val gameDir = previous?.takeIf { layout == Layout.SEPARATE_INSTANCE && it.layout == layout }?.let { Path.of(it.gameDir) } ?: gameDirFor(entry, mcDir, layout)
        val modsDir = gameDir.resolve("mods")
        try {
            Files.createDirectories(modsDir)

            val versionDir = mcDir.resolve("versions").resolve(entry.fabricVersionId)
            val createdVersionDir = !Files.exists(versionDir)
            Files.createDirectories(versionDir)
            Files.write(versionDir.resolve("${entry.fabricVersionId}.json"), prepared.fabricProfile!!)

            val written = writeMods(entry, modsDir, prepared, log)

            log("Adding the launcher profile")
            val profileKey = profileKey(entry)
            updateLauncherProfiles(mcDir) { root ->
                LauncherProfiles.addProfile(
                    root, profileKey, "Relict ${entry.minecraft}", entry.fabricVersionId,
                    gameDir = if (layout == Layout.SEPARATE_INSTANCE) gameDir.toString() else null,
                    now = clock.instant(),
                )
            }

            writeRecord(
                recordPath(entry, mcDir),
                InstallRecord(manifest.relictVersion, layout, gameDir.toString(), entry.fabricVersionId, profileKey, createdVersionDir, written),
            )
        } catch (e: IOException) {
            throw InstallException("Couldn't write the files (is Minecraft still open?): ${e.message}", e)
        }
        log("Done. Open the Minecraft Launcher and pick the \"Relict ${entry.minecraft}\" profile.")
    }

    // ---- another launcher's instance (Prism, Modrinth App, ...) ------------------------------

    /** Installs into an instance's game folder. The launcher already has its own profile and Fabric Loader. */
    fun installInstance(entry: VersionEntry, gameDir: Path, log: (String) -> Unit = {}) {
        val parent = gameDir.parent
        if (!Files.isDirectory(gameDir) && (parent == null || !Files.isDirectory(parent))) {
            throw InstallException("The instance folder $gameDir doesn't exist.")
        }
        val prepared = prepare(entry, withFabricProfile = false, log)

        if (readRecord(instanceRecord(gameDir)) != null) {
            log("Removing the previous install first")
            uninstallInstance(gameDir, log)
        }
        try {
            val modsDir = gameDir.resolve("mods")
            Files.createDirectories(modsDir)
            val written = writeMods(entry, modsDir, prepared, log)
            writeRecord(instanceRecord(gameDir), InstallRecord(manifest.relictVersion, Layout.INSTANCE, gameDir.toString(), "", "", false, written))
        } catch (e: IOException) {
            throw InstallException("Couldn't write the files (is the game still open?): ${e.message}", e)
        }
        log("Done. Start the instance in your launcher.")
    }

    fun instanceStatus(gameDir: Path): Status = statusOf(readRecord(instanceRecord(gameDir)))

    /** Reinstalls an installed instance from this manifest. False if Relict isn't installed there. */
    fun updateInstance(entry: VersionEntry, gameDir: Path, log: (String) -> Unit = {}): Boolean {
        if (readRecord(instanceRecord(gameDir)) == null) return false
        installInstance(entry, gameDir, log)
        return true
    }

    fun uninstallInstance(gameDir: Path, log: (String) -> Unit = {}) {
        val record = readRecord(instanceRecord(gameDir)) ?: throw InstallException("Relict isn't installed in this instance.")
        try {
            removeFiles(Path.of(record.gameDir), record, log)
            restoreAside(gameDir.resolve("mods"), log)
            Files.deleteIfExists(instanceRecord(gameDir))
            runCatching { Files.deleteIfExists(instanceRecord(gameDir).parent) }
        } catch (e: IOException) {
            throw InstallException("Couldn't remove everything (is the game still open?): ${e.message}", e)
        }
        log("Uninstalled Relict from this instance.")
    }

    // ---- one API for both kinds of target -----------------------------------------------------

    fun status(entry: VersionEntry, target: Target): Status = if (target.isInstance) instanceStatus(target.dir) else status(entry, target.dir)

    fun install(entry: VersionEntry, target: Target, layout: Layout, log: (String) -> Unit = {}) =
        if (target.isInstance) installInstance(entry, target.dir, log) else install(entry, target.dir, layout, log)

    fun update(entry: VersionEntry, target: Target, log: (String) -> Unit = {}): Boolean =
        if (target.isInstance) updateInstance(entry, target.dir, log) else update(entry, target.dir, log)

    fun uninstall(entry: VersionEntry, target: Target, log: (String) -> Unit = {}) =
        if (target.isInstance) uninstallInstance(target.dir, log) else uninstall(entry, target.dir, log)

    /** Reinstalls an installed version from this manifest, keeping the layout it was installed with. False if it isn't installed. */
    fun update(entry: VersionEntry, mcDir: Path, log: (String) -> Unit = {}): Boolean {
        val record = readRecord(recordFile(entry, mcDir)) ?: return false
        install(entry, mcDir, record.layout, log)
        return true
    }

    fun uninstall(entry: VersionEntry, mcDir: Path, log: (String) -> Unit = {}) {
        val file = recordFile(entry, mcDir)
        val record = readRecord(file) ?: throw InstallException("Relict ${entry.minecraft} isn't installed here.")
        val gameDir = Path.of(record.gameDir)
        try {
            removeFiles(gameDir, record, log)
            restoreAside(gameDir.resolve("mods"), log)

            if (record.layout == Layout.SEPARATE_INSTANCE) {
                // Remove the instance folder only if nothing of the player's is in it.
                runCatching { Files.deleteIfExists(gameDir.resolve("mods")) }
                runCatching { Files.deleteIfExists(gameDir) }
            }
            updateLauncherProfiles(mcDir) { LauncherProfiles.removeProfile(it, record.profileKey) }

            if (record.createdVersionDir && !otherRecordUses(mcDir, file, record.fabricVersionId)) {
                val versionDir = mcDir.resolve("versions").resolve(record.fabricVersionId)
                versionDir.toFile().deleteRecursively()
            }
            Files.deleteIfExists(file)
            // Folders the installer created, once nothing is left in them (a non-empty one is simply kept).
            for (dir in listOf(file.parent, mcDir.resolve("relict-instances"), mcDir.resolve("veyly-instances"))) {
                runCatching { Files.deleteIfExists(dir) }
            }
        } catch (e: IOException) {
            throw InstallException("Couldn't remove everything (is Minecraft still open?): ${e.message}", e)
        }
        log("Uninstalled Relict ${entry.minecraft}.")
    }

    // ---- helpers ----------------------------------------------------------------------------

    private fun gameDirFor(entry: VersionEntry, mcDir: Path, layout: Layout): Path = when (layout) {
        Layout.SEPARATE_INSTANCE -> mcDir.resolve("relict-instances").resolve(entry.minecraft)
        Layout.MAIN_FOLDER, Layout.INSTANCE -> mcDir
    }

    private fun profileKey(entry: VersionEntry) = "relict-${entry.minecraft}"

    private class Prepared(val fabricProfile: ByteArray?, val mods: List<Pair<ModFile, ByteArray>>, val jar: ByteArray)

    /** Downloads and checks everything an install needs, changing nothing on disk. */
    private fun prepare(entry: VersionEntry, withFabricProfile: Boolean, log: (String) -> Unit): Prepared {
        val fabricProfile = if (withFabricProfile) {
            log("Fetching the Fabric loader profile")
            fetch("https://meta.fabricmc.net/v2/versions/loader/${entry.minecraft}/${entry.fabricLoader}/profile/json").also { parseFabricProfile(it, entry) }
        } else {
            null
        }
        val downloaded = entry.mods.map { mod ->
            log("Downloading ${mod.file}")
            mod to verified(mod, fetch(mod.url))
        }
        return Prepared(fabricProfile, downloaded, relictJar(entry, log))
    }

    /** Puts the dependency jars and the Relict jar into [modsDir], setting older copies aside. Returns what was written, relative to the game folder. */
    private fun writeMods(entry: VersionEntry, modsDir: Path, prepared: Prepared, log: (String) -> Unit): List<String> {
        val written = mutableListOf<String>()
        for ((mod, bytes) in prepared.mods) {
            moveAsideOlder(modsDir, "${mod.id}-", log)
            Files.write(modsDir.resolve(mod.file), bytes)
            written += "mods/${mod.file}"
        }
        // "veyly-mc" is what this client was called before it was renamed.
        for (prefix in listOf("relict-mc", "veyly-mc")) moveAsideOlder(modsDir, prefix, log)
        Files.write(modsDir.resolve(entry.relictJar), prepared.jar)
        written += "mods/${entry.relictJar}"
        return written
    }

    private fun removeFiles(gameDir: Path, record: InstallRecord, log: (String) -> Unit) {
        for (relative in record.files) {
            val file = gameDir.resolve(relative).normalize()
            if (file.startsWith(gameDir) && Files.deleteIfExists(file)) log("Removed $relative")
        }
    }

    /** The Relict jar: downloaded and checked when the manifest says where it is (an update), otherwise the one packed into the installer. */
    private fun relictJar(entry: VersionEntry, log: (String) -> Unit): ByteArray {
        if (entry.relictUrl != null) {
            log("Downloading ${entry.relictJar}")
            return verified(entry.relictJar, entry.relictSha512.orEmpty(), fetch(entry.relictUrl))
        }
        log("Unpacking ${entry.relictJar}")
        return try {
            bundledJar(entry.relictJar)
        } catch (e: Exception) {
            throw InstallException("This installer doesn't contain ${entry.relictJar}", e)
        }
    }

    private fun fetch(url: String): ByteArray {
        if (!Manifest.isAllowedUrl(url, extraHosts)) throw InstallException("Refusing to download from $url")
        return try {
            downloader.get(url)
        } catch (e: IOException) {
            throw InstallException("Download failed: ${e.message}. Check your internet connection.", e)
        }
    }

    private fun verified(mod: ModFile, bytes: ByteArray): ByteArray = verified(mod.file, mod.sha512, bytes)

    private fun verified(file: String, sha512: String, bytes: ByteArray): ByteArray {
        val actual = MessageDigest.getInstance("SHA-512").digest(bytes).joinToString("") { "%02x".format(it) }
        if (actual != sha512) throw InstallException("$file failed its integrity check, so nothing was installed.")
        return bytes
    }

    private fun parseFabricProfile(bytes: ByteArray, entry: VersionEntry): JsonObject {
        val json = try {
            JsonParser.parseString(String(bytes)).asJsonObject
        } catch (e: Exception) {
            throw InstallException("Fabric returned something unexpected for Minecraft ${entry.minecraft}.", e)
        }
        if (json.get("id")?.asString != entry.fabricVersionId || json.get("inheritsFrom")?.asString != entry.minecraft) {
            throw InstallException("Fabric's profile doesn't match Minecraft ${entry.minecraft} with loader ${entry.fabricLoader}.")
        }
        return json
    }

    /** Older copies of a mod would make Fabric refuse to start (duplicate mod id), so they are set aside, not deleted. */
    private fun moveAsideOlder(modsDir: Path, prefix: String, log: (String) -> Unit) {
        if (!Files.isDirectory(modsDir)) return
        val pattern = Regex("^${Regex.escape(prefix)}.*\\.jar$")
        Files.list(modsDir).use { stream ->
            stream.filter { Files.isRegularFile(it) && pattern.matches(it.fileName.toString()) }.toList()
        }.forEach { old ->
            val aside = modsDir.resolve(ASIDE).also { Files.createDirectories(it) }
            Files.move(old, aside.resolve(old.fileName), StandardCopyOption.REPLACE_EXISTING)
            log("Moved ${old.fileName} aside (kept in mods/$ASIDE)")
        }
    }

    private fun restoreAside(modsDir: Path, log: (String) -> Unit) {
        for (name in listOf(ASIDE, LEGACY_ASIDE)) {
            val aside = modsDir.resolve(name)
            if (!Files.isDirectory(aside)) continue
            Files.list(aside).use { it.toList() }.forEach { saved ->
                val target = modsDir.resolve(saved.fileName)
                if (!Files.exists(target)) {
                    Files.move(saved, target)
                    log("Restored ${saved.fileName}")
                }
            }
            runCatching { Files.deleteIfExists(aside) }
        }
    }

    private fun updateLauncherProfiles(mcDir: Path, edit: (JsonObject) -> Unit) {
        val file = mcDir.resolve("launcher_profiles.json")
        val root = try {
            JsonParser.parseString(Files.readString(file)).asJsonObject
        } catch (e: Exception) {
            throw InstallException("The launcher's profile file is unreadable, so it was left alone.", e)
        }
        val backup = mcDir.resolve("launcher_profiles.json.relict-backup")
        if (!Files.exists(backup)) Files.copy(file, backup)
        edit(root)
        val temp = file.resolveSibling("launcher_profiles.json.tmp")
        Files.writeString(temp, gson.toJson(root))
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun recordPath(entry: VersionEntry, mcDir: Path) = mcDir.resolve("relict-installer").resolve("${entry.minecraft}.json")

    /** The record of an install made before the client was renamed lives in the old folder. */
    private fun legacyRecordPath(entry: VersionEntry, mcDir: Path) = mcDir.resolve("veyly-installer").resolve("${entry.minecraft}.json")

    private fun recordFile(entry: VersionEntry, mcDir: Path): Path =
        recordPath(entry, mcDir).takeIf { Files.isRegularFile(it) } ?: legacyRecordPath(entry, mcDir)

    private fun instanceRecord(gameDir: Path) = gameDir.resolve("relict").resolve("installer.json")

    private fun readRecord(file: Path): InstallRecord? {
        if (!Files.isRegularFile(file)) return null
        return try {
            gson.fromJson(Files.readString(file), InstallRecord::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeRecord(file: Path, record: InstallRecord) {
        Files.createDirectories(file.parent)
        Files.writeString(file, gson.toJson(record))
    }

    private fun otherRecordUses(mcDir: Path, own: Path, fabricVersionId: String): Boolean =
        listOf("relict-installer", "veyly-installer").map { mcDir.resolve(it) }.filter { Files.isDirectory(it) }.any { dir ->
            Files.list(dir).use { it.toList() }.any { file ->
                file != own && readRecord(file)?.fabricVersionId == fabricVersionId
            }
        }

    private companion object {
        const val ASIDE = ".relict-replaced"
        const val LEGACY_ASIDE = ".veyly-replaced"
    }
}
