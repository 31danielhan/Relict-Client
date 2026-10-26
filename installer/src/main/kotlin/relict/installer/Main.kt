package relict.installer

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatSVGIcon
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--cli") Cli.run(args.drop(1))

    // --export-icon <file.png> <size>: draws the logo at that size (the app icons are made from it by scripts/make-icons.sh).
    if (args.firstOrNull() == "--export-icon") {
        val size = args[2].toInt()
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        FlatSVGIcon(LOGO_RESOURCE, size, size).paintIcon(null, g, 0, 0)
        g.dispose()
        ImageIO.write(image, "png", Path.of(args[1]).toFile())
        exitProcess(0)
    }

    FlatLaf.setGlobalExtraDefaults(Theme.defaults)
    FlatDarkLaf.setup()

    // --screenshot <file.png> [--update] [--select <index>]: paints the window to an image without showing it.
    // --update pretends a newer build was found, to see that state.
    if (args.firstOrNull() == "--screenshot") {
        SwingUtilities.invokeAndWait { InstallerWindow(Manifest.bundled(), preview = "--update" in args, initialSelection = args.indexOf("--select").takeIf { it >= 0 }?.let { args[it + 1].toInt() }).screenshot(Path.of(args[1])) }
        exitProcess(0)
    }
    SwingUtilities.invokeLater { InstallerWindow(Manifest.bundled()).isVisible = true }
}
