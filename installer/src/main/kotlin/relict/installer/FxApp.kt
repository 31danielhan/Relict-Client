package relict.installer

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import javafx.application.Application
import javafx.application.ConditionalFeature
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.layout.HeaderBar
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.stage.StageStyle
import netscape.javascript.JSObject
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/** What the window is started with; set by [main] before JavaFX takes over. */
object FxLaunch {
    var preview = false
    var screenshot: Path? = null
    var select: Int? = null
}

/**
 * The installer window: one web page (built from ui/, see ui/src/installer) shown in a WebView.
 * The page calls [Bridge.invoke]; the work happens on a background thread and the answer goes back through
 * `window.relictBridge.resolve`. News from the backend (progress lines, update banners) goes through `window.relictBridge.emit`.
 */
class FxApp : Application() {
    private val workers = Executors.newCachedThreadPool { r -> Thread(r, "installer-worker").also { it.isDaemon = true } }
    private lateinit var engine: WebEngine
    private lateinit var stage: Stage
    private lateinit var backend: InstallerBackend
    private lateinit var bridge: Bridge // held so JavaScript's reference to it isn't garbage collected

    /** The object the page sees as `window.backend`. Its public methods are what JavaScript can call. */
    inner class Bridge {
        fun invoke(id: Int, method: String, params: String) {
            workers.execute {
                val reply = backend.respond(method, runCatching { JsonParser.parseString(params).asJsonObject }.getOrDefault(JsonObject()))
                script("window.relictBridge.resolve($id, ${JsonPrimitive(reply)})")
            }
        }
    }

    private fun script(code: String) = Platform.runLater { runCatching { engine.executeScript(code) } }

    private fun emit(event: String, payload: JsonElement) = script("window.relictBridge.emit(${JsonPrimitive(event)}, $payload)")

    override fun start(stage: Stage) {
        this.stage = stage
        val view = WebView().apply { isContextMenuEnabled = false }
        engine = view.engine
        backend = InstallerBackend(
            Manifest.bundled(),
            preview = FxLaunch.preview,
            open = { path -> Platform.runLater { hostServices.showDocument(path.toUri().toString()) } },
            chooseFolder = ::chooseFolder,
            emit = ::emit,
            initialSelection = FxLaunch.select,
        )
        bridge = Bridge()

        // Once the page has loaded, give it the bridge and tell it to start (it waits for this: see ui/src/installer/main.ts).
        engine.loadWorker.stateProperty().addListener { _, _, state ->
            if (state == Worker.State.SUCCEEDED) {
                (engine.executeScript("window") as JSObject).setMember("backend", bridge)
                engine.executeScript("window.relictBoot && window.relictBoot()")
            }
        }
        engine.setOnError { println("[web] ${it.message}") }
        engine.setOnAlert { println("[web] alert: ${it.data}") }

        stage.title = "Relict Installer"
        runCatching { Resources.stream("relict-256.png")?.use { stage.icons.add(Image(it)) } }
        // No title bar of its own: the page reaches the top of the window and the system's window buttons sit on top of it.
        // The strip along the top stays draggable, so the window can still be moved.
        val extended = Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW) && runCatching { stage.initStyle(StageStyle.EXTENDED) }.isSuccess
        val root: Parent = if (extended) {
            val dragStrip = HeaderBar().apply { maxHeight = DRAG_STRIP_HEIGHT; prefHeight = DRAG_STRIP_HEIGHT; minHeight = DRAG_STRIP_HEIGHT }
            StackPane(view, dragStrip).also { StackPane.setAlignment(dragStrip, Pos.TOP_CENTER) }
        } else view
        stage.scene = Scene(root, 1040.0, 700.0, Color.web("#04051a"))
        stage.minWidth = 900.0
        stage.minHeight = 620.0
        stage.show()

        FxLaunch.screenshot?.let { takeScreenshot(it, view) }
        val html = Resources.stream("web/index.html")?.use { String(it.readBytes(), Charsets.UTF_8) }
            ?: error("The installer's window files are missing (web/index.html)")
        engine.loadContent(html)
    }

    private fun chooseFolder(start: Path?): Path? {
        val result = CompletableFuture<Path?>()
        Platform.runLater {
            val chooser = DirectoryChooser().apply {
                title = "Choose a Minecraft folder or an instance's game folder"
                start?.toFile()?.takeIf { it.isDirectory }?.let { initialDirectory = it }
            }
            result.complete(chooser.showDialog(stage)?.toPath())
        }
        return result.get()
    }

    /** Waits for the page to settle, writes it to a PNG, and quits (used to check the layout). */
    private fun takeScreenshot(file: Path, view: WebView) {
        engine.loadWorker.stateProperty().addListener { _, _, state ->
            if (state != Worker.State.SUCCEEDED) return@addListener
            val timer = java.util.Timer(true)
            timer.schedule(object : java.util.TimerTask() {
                override fun run() = Platform.runLater {
                    val image = view.snapshot(null, null)
                    val w = image.width.toInt()
                    val h = image.height.toInt()
                    val out = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    for (y in 0 until h) for (x in 0 until w) out.setRGB(x, y, image.pixelReader.getArgb(x, y))
                    javax.imageio.ImageIO.write(out, "png", file.toFile())
                    Platform.exit()
                    System.exit(0)
                }
            }, 2500)
        }
    }

    private companion object {
        const val DRAG_STRIP_HEIGHT = 34.0
    }
}
