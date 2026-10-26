package relict.installer

import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties

/** Compares dotted version numbers such as 0.2.0 or 1.10.1: numerically, segment by segment, missing segments count as 0. */
object Versions {
    fun compare(a: String, b: String): Int {
        val left = parts(a)
        val right = parts(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val c = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    private fun parts(version: String) = version.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
}

/**
 * Where newer builds are published: a JSON file in the manifest's format, plus a download URL and hash for each Relict jar.
 * It is set when the installer is built (src/main/resources/update.properties). Setting `relict.updateUrl` or the
 * RELICT_UPDATE_URL environment variable overrides it, which is how the update flow is tried out against a test server.
 */
object UpdateConfig {
    fun feedUrl(): String? {
        val configured = System.getProperty("relict.updateUrl") ?: System.getenv("RELICT_UPDATE_URL") ?: fromResource()
        return configured?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun fromResource(): String? {
        val stream = UpdateConfig::class.java.getResourceAsStream("/update.properties") ?: return null
        return Properties().apply { stream.use { load(it) } }.getProperty("feedUrl")
    }
}

sealed interface UpdateCheck {
    /** No feed URL was built into this installer. */
    data object NotConfigured : UpdateCheck

    data class UpToDate(val version: String) : UpdateCheck

    /**
     * Something newer exists. [feed] is the newer manifest; an [Installer] built from it ([hosts]) installs the newer jars.
     * [newRelict] says the client itself has a newer build; [installerFile] is a newer installer app for this platform, if any.
     */
    data class Available(
        val feed: Manifest,
        val hosts: Set<String>,
        val newRelict: Boolean = true,
        val installerFile: InstallerFile? = null,
    ) : UpdateCheck

    data class Failed(val message: String) : UpdateCheck
}

/** Looks at the update feed and says whether it holds a newer build than [current]. */
class UpdateChecker(
    private val feedUrl: String?,
    private val downloader: Downloader,
    private val current: String,
    private val installerVersion: String = InstallerInfo.version,
    private val os: String = InstallerInfo.os,
) {
    fun check(): UpdateCheck {
        val url = feedUrl ?: return UpdateCheck.NotConfigured
        val host = try {
            URI(url).takeIf { it.scheme == "https" }?.host
        } catch (_: Exception) {
            null
        } ?: return UpdateCheck.Failed("The update address must be an https:// address.")

        val hosts = setOf(host)
        val feed = try {
            Manifest.parse(String(downloader.get(url)), hosts, requireJarUrls = true)
        } catch (e: IOException) {
            return UpdateCheck.Failed("Couldn't reach the update server: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return UpdateCheck.Failed(e.message ?: "The update information is unreadable.")
        }
        val newRelict = Versions.compare(feed.relictVersion, current) > 0
        val installerFile = feed.installer?.takeIf { Versions.compare(it.version, installerVersion) > 0 }?.files?.get(os)
        return if (newRelict || installerFile != null) UpdateCheck.Available(feed, hosts, newRelict, installerFile) else UpdateCheck.UpToDate(current)
    }
}

/** Downloads a newer installer app. It can't replace itself while it runs, so the file is saved for the player to open. */
object InstallerDownload {
    fun download(file: InstallerFile, hosts: Set<String>, downloader: Downloader, into: Path): Path {
        if (!Manifest.isAllowedUrl(file.url, hosts)) throw InstallException("Refusing to download from ${file.url}")
        val bytes = try {
            downloader.get(file.url)
        } catch (e: IOException) {
            throw InstallException("Download failed: ${e.message}", e)
        }
        val actual = MessageDigest.getInstance("SHA-512").digest(bytes).joinToString("") { "%02x".format(it) }
        if (actual != file.sha512) throw InstallException("${file.file} failed its integrity check, so it was not saved.")
        Files.createDirectories(into)
        val target = into.resolve(file.file).normalize()
        if (target.parent != into.normalize()) throw InstallException("Bad file name ${file.file}")
        return Files.write(target, bytes)
    }
}
