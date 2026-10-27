package relict.installer

import com.formdev.flatlaf.extras.FlatSVGIcon
import javafx.application.Application
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.system.exitProcess

const val LOGO_RESOURCE = "relict-logo.svg"

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

    // --screenshot <file.png> [--update] [--select <index>]: opens the window, writes it to an image and quits.
    // --update pretends a newer build was found, to see that state.
    if (args.firstOrNull() == "--screenshot") {
        FxLaunch.screenshot = Path.of(args[1])
        FxLaunch.preview = "--update" in args
        FxLaunch.select = args.indexOf("--select").takeIf { it >= 0 }?.let { args[it + 1].toInt() }
    }
    Application.launch(FxApp::class.java)
}
