package relict.installer

import java.util.Properties

/** Facts about this installer app itself. */
object InstallerInfo {
    val version: String by lazy {
        val stream = InstallerInfo::class.java.getResourceAsStream("/installer.properties") ?: error("installer.properties is missing")
        Properties().apply { stream.use { load(it) } }.getProperty("version")
    }

    /**
     * The key under which an update feed lists this computer's installer download: mac (Apple Silicon), mac-intel,
     * windows or linux. The Mac build is made on an Apple Silicon runner, so Intel Macs are never offered it.
     */
    val os: String get() = platformKey(System.getProperty("os.name"), System.getProperty("os.arch"))

    fun platformKey(name: String, arch: String): String {
        val key = osKey(name)
        return if (key == "mac" && arch.lowercase().let { it == "x86_64" || it == "amd64" }) "mac-intel" else key
    }

    fun osKey(name: String): String = name.lowercase().let { if ("win" in it) "windows" else if ("mac" in it) "mac" else "linux" }
}
