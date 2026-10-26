package relict.installer

import com.google.gson.Gson
import java.net.URI

/** One dependency file the installer downloads and verifies. */
data class ModFile(val id: String, val file: String, val url: String, val sha512: String, val size: Long)

/** Everything needed to set up Relict for one Minecraft version. */
data class VersionEntry(
    val minecraft: String,
    val fabricLoader: String,
    val relictJar: String,
    val mods: List<ModFile>,
    /** Where to download the Relict jar from. Set in an update feed; the jars packed into the installer have no URL. */
    val relictUrl: String? = null,
    val relictSha512: String? = null,
) {
    /** The id Fabric gives its launcher version, e.g. fabric-loader-0.20.1-1.21.11. */
    val fabricVersionId: String get() = "fabric-loader-$fabricLoader-$minecraft"

    /**
     * Whether this build runs on [version]. The 26.x builds cover a whole series (the 26.1 jar accepts any 26.1.x);
     * every other build is for exactly one Minecraft version.
     */
    fun supports(version: String): Boolean =
        version == minecraft || (minecraft.startsWith("26.") && series(version) == series(minecraft))

    private fun series(v: String) = v.split('.').take(2).joinToString(".")
}

/** One download of the installer app itself (a .dmg, a zip of the Windows app, ...). */
data class InstallerFile(val file: String, val url: String, val sha512: String)

/** A newer installer app, listed in the update feed. [files] is keyed by platform: mac, mac-intel, windows or linux. */
data class InstallerRelease(val version: String, val files: Map<String, InstallerFile> = emptyMap())

data class Manifest(
    val relictVersion: String,
    val versions: List<VersionEntry>,
    val notes: String? = null,
    val installer: InstallerRelease? = null,
) {
    fun entryFor(minecraft: String): VersionEntry? = versions.firstOrNull { it.supports(minecraft) }

    companion object {
        /** Hosts the installer is willing to download from. */
        val ALLOWED_HOSTS = setOf("cdn.modrinth.com", "meta.fabricmc.net")

        private val SAFE_FILE = Regex("[A-Za-z0-9._+-]+\\.jar")
        private val SAFE_VERSION = Regex("[0-9][0-9A-Za-z.+-]*")
        private val SHA512 = Regex("[0-9a-f]{128}")
        private val INSTALLER_FILE = Regex("[A-Za-z0-9._+ -]+\\.(dmg|zip|exe|msi|jar)")
        private val PLATFORMS = setOf("mac", "mac-intel", "windows", "linux")

        /**
         * [extraHosts] are hosts that may be downloaded from in addition to [ALLOWED_HOSTS] (the update feed's own host).
         * An update feed has to say where each Relict jar is, so it is parsed with [requireJarUrls].
         */
        fun parse(json: String, extraHosts: Set<String> = emptySet(), requireJarUrls: Boolean = false): Manifest {
            val manifest = try {
                Gson().fromJson(json, Manifest::class.java)
            } catch (e: Exception) {
                throw IllegalArgumentException("The installer's manifest is unreadable", e)
            }
            requireNotNull(manifest?.versions) { "The installer's manifest is empty" }
            manifest.versions.forEach { validate(it, extraHosts, requireJarUrls) }
            manifest.installer?.let { validate(it, extraHosts) }
            return manifest
        }

        /** The manifest packaged inside the installer. */
        fun bundled(): Manifest {
            val stream = Manifest::class.java.getResourceAsStream("/manifest.json")
                ?: error("manifest.json is missing from the installer")
            return parse(stream.use { String(it.readAllBytes()) })
        }

        /** Anything that ends up in a path or a URL must be boring: no separators, no traversal, only known hosts. */
        private fun validate(entry: VersionEntry, extraHosts: Set<String>, requireJarUrls: Boolean) {
            require(entry.minecraft.matches(SAFE_VERSION)) { "Bad Minecraft version '${entry.minecraft}'" }
            require(entry.fabricLoader.matches(SAFE_VERSION)) { "Bad Fabric Loader version '${entry.fabricLoader}'" }
            require(entry.relictJar.matches(SAFE_FILE)) { "Bad Relict jar name '${entry.relictJar}'" }
            for (mod in entry.mods) {
                require(mod.id.matches(Regex("[a-z0-9-]+"))) { "Bad mod id '${mod.id}'" }
                require(mod.file.matches(SAFE_FILE)) { "Bad file name '${mod.file}'" }
                require(mod.sha512.matches(SHA512)) { "Bad hash for ${mod.file}" }
                require(isAllowedUrl(mod.url, extraHosts)) { "Download for ${mod.file} is not from an allowed host: ${mod.url}" }
            }
            if (entry.relictUrl != null || entry.relictSha512 != null || requireJarUrls) {
                require(entry.relictUrl != null && isAllowedUrl(entry.relictUrl, extraHosts)) { "The Relict jar for ${entry.minecraft} is not from an allowed host" }
                require(entry.relictSha512?.matches(SHA512) == true) { "Bad hash for ${entry.relictJar}" }
            }
        }

        private fun validate(release: InstallerRelease, extraHosts: Set<String>) {
            require(release.version.matches(SAFE_VERSION)) { "Bad installer version '${release.version}'" }
            for ((platform, file) in release.files) {
                require(platform in PLATFORMS) { "Unknown platform '$platform'" }
                require(file.file.matches(INSTALLER_FILE)) { "Bad installer file name '${file.file}'" }
                require(file.sha512.matches(SHA512)) { "Bad hash for ${file.file}" }
                require(isAllowedUrl(file.url, extraHosts)) { "Download for ${file.file} is not from an allowed host: ${file.url}" }
            }
        }

        fun isAllowedUrl(url: String, extraHosts: Set<String> = emptySet()): Boolean = try {
            val uri = URI(url)
            uri.scheme == "https" && (uri.host in ALLOWED_HOSTS || uri.host in extraHosts)
        } catch (_: Exception) {
            false
        }
    }
}
