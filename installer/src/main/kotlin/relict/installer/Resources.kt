package relict.installer

object Resources {
    /** A file packed into the installer, e.g. web/index.html, or null if it isn't there. */
    fun stream(path: String): java.io.InputStream? = Resources::class.java.getResourceAsStream("/$path")

    /** Reads a Relict jar that was packed into the installer. */
    fun bundledJar(name: String): ByteArray {
        val stream = Resources::class.java.getResourceAsStream("/jars/$name") ?: error("$name is not bundled in this installer")
        return stream.use { it.readAllBytes() }
    }
}
