package relict.ui

import net.fabricmc.loader.api.FabricLoader
import relict.RelictClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * MCEF Modern has no custom resource scheme, so the bundled web UI is copied out of the jar
 * to the game directory on startup and loaded over file://.
 */
object UiAssets {
    private const val RESOURCE_ROOT = "assets/${RelictClient.MOD_ID}/html"

    val dir: Path = FabricLoader.getInstance().gameDir.resolve(RelictClient.MOD_ID).resolve("ui")

    fun extract() {
        val container = FabricLoader.getInstance().getModContainer(RelictClient.MOD_ID).get()
        val root = container.findPath(RESOURCE_ROOT).orElseThrow { IllegalStateException("Missing $RESOURCE_ROOT in jar") }

        if (Files.exists(dir)) dir.toFile().deleteRecursively()
        Files.createDirectories(dir)
        Files.walk(root).use { paths ->
            paths.forEach { src ->
                val target = dir.resolve(root.relativize(src).toString())
                if (Files.isDirectory(src)) Files.createDirectories(target)
                else Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        RelictClient.logger.info("UI extracted to {}", dir)
    }

    /** Every page of the bundled UI starts with this. The bridge refuses calls from anywhere else. */
    val baseUrl: String get() = dir.toUri().toString()

    fun url(page: String): String = dir.resolve(page).toUri().toString()
}
