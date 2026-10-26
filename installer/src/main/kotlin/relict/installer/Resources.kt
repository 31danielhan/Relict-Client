package relict.installer

object Resources {
    /** Reads a Relict jar that was packed into the installer. */
    fun bundledJar(name: String): ByteArray {
        val stream = Resources::class.java.getResourceAsStream("/jars/$name") ?: error("$name is not bundled in this installer")
        return stream.use { it.readAllBytes() }
    }
}
