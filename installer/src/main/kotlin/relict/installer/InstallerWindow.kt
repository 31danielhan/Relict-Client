package relict.installer

import com.formdev.flatlaf.extras.FlatSVGIcon
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.SwingWorker

const val LOGO_RESOURCE = "relict-logo.svg"

class InstallerWindow(
    private val bundled: Manifest,
    /** Show the "update available" state without a network (for screenshots). */
    private val preview: Boolean = false,
    private val settingsFile: Path = Settings.defaultFile(),
    private val feedUrl: String? = UpdateConfig.feedUrl(),
    detected: List<Target> = Launchers.detect(),
    /** Pick this launcher in the list at the start (for screenshots). */
    initialSelection: Int? = null,
) : JFrame("Relict Installer") {
    /** The bundled manifest, or the newer one from the update feed once an update was found. */
    private var manifest = bundled
    private var installer = newInstaller(bundled, emptySet())
    private var settings = Settings.load(settingsFile)

    private var found: List<Target> = detected
    private val custom = mutableListOf<Target>()
    private val allTargets get() = found + custom

    private val targetBox = JComboBox<Target>().apply { renderer = TargetRenderer() }
    private val rescan = JButton("Rescan")
    private val targetNote = JLabel(" ")

    private val versions = JComboBox<String>()
    private val versionCard: JPanel
    private val statusPill = RoundedPanel(Theme.BACKGROUND, Theme.BORDER, 20)
    private val statusText = JLabel(" ")

    private val separate = JRadioButton("Separate Relict instance", true)
    private val main = JRadioButton("Main Minecraft folder")
    private val separateTile = RoundedPanel()
    private val mainTile = RoundedPanel()
    private val layoutCard: JPanel
    private val group = ButtonGroup().apply { add(separate); add(main) }

    private val install = JButton("Install")
    private val uninstall = JButton("Uninstall")
    private val progress = JProgressBar().apply { isIndeterminate = true; isVisible = false; preferredSize = Dimension(10, 4) }
    private val log = JTextArea().apply {
        isEditable = false; font = Font(Font.MONOSPACED, Font.PLAIN, 12); lineWrap = true; wrapStyleWord = true
        border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
    }

    private val updateBanner = RoundedPanel(Theme.CARD_SELECTED, Theme.ACCENT)
    private val updateTitle = JLabel(" ").apply { font = font.deriveFont(Font.BOLD, 13f) }
    private val updateDetail = JLabel(" ").apply { foreground = Theme.MUTED }
    private val updateNow = JButton("Update Relict")
    private val installerNow = JButton("Get new installer")
    private val autoUpdate = JCheckBox("Update automatically", settings.autoUpdate)
    private val checkNow = JButton("Check for updates")

    private val target: Target? get() = targetBox.selectedItem as Target?

    /** The build that goes with the chosen target: fixed by an instance's Minecraft version, chosen for the vanilla launcher. */
    private val entry: VersionEntry?
        get() {
            val t = target ?: return null
            t.minecraft?.let { return manifest.entryFor(it) }
            return versions.selectedIndex.takeIf { it >= 0 }?.let { manifest.versions[it] }
        }

    private val layout get() = if (separate.isSelected) Layout.SEPARATE_INSTANCE else Layout.MAIN_FOLDER

    private var busy = false
    private var pendingUpdate: UpdateCheck.Available? = null

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        runCatching { iconImages = listOf(32, 64, 128, 256).map { FlatSVGIcon(LOGO_RESOURCE, it, it).image } }
        versionCard = spaced(card("Minecraft version", row(versions, statusPill)))
        layoutCard = spaced(card("Where to install",
            tile(separateTile, separate, "Recommended. Keeps your current mods, worlds and settings untouched."),
            Box.createVerticalStrut(8),
            tile(mainTile, main, "Shares your existing mods. Older copies of Fabric API are moved aside, not deleted."),
        ))
        contentPane = buildContent()
        minimumSize = Dimension(620, 760)
        pack()
        setLocationRelativeTo(null)
        loadVersions()
        loadTargets(selectBest = true)
        initialSelection?.let { targetBox.selectedIndex = it }

        install.addActionListener { val t = target; val e = entry; if (t != null && e != null) run("Installing Relict ${e.minecraft}") { installer.install(e, t, layout) { line(it) } } }
        uninstall.addActionListener {
            val t = target
            val e = entry
            if (t == null || e == null) return@addActionListener
            val ok = JOptionPane.showConfirmDialog(this, "Remove Relict from ${t.label}? Your own worlds and mods are not touched.", "Uninstall", JOptionPane.OK_CANCEL_OPTION)
            if (ok == JOptionPane.OK_OPTION) run("Uninstalling Relict") { installer.uninstall(e, t) { line(it) } }
        }
        targetBox.addActionListener { refresh() }
        versions.addActionListener { refresh() }
        rescan.addActionListener { rescanLaunchers() }
        separate.addActionListener { refreshTiles() }
        main.addActionListener { refreshTiles() }
        autoUpdate.addActionListener {
            settings = settings.copy(autoUpdate = autoUpdate.isSelected)
            settings.save(settingsFile)
        }
        checkNow.addActionListener { checkForUpdates(manual = true) }
        updateNow.addActionListener { applyUpdate() }
        installerNow.addActionListener { downloadInstaller() }

        refreshTiles()
        refresh()
        if (preview) showPreviewUpdate() else if (settings.checkOnLaunch && feedUrl != null) checkForUpdates(manual = false)
    }

    /** Paints the window to a PNG (used to check the layout without a display). */
    fun screenshot(file: Path) {
        pack()
        contentPane.validate()
        val image = BufferedImage(contentPane.width, contentPane.height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        contentPane.paint(g)
        g.dispose()
        ImageIO.write(image, "png", file.toFile())
    }

    // ---- layout -----------------------------------------------------------------------------

    private fun buildContent(): JPanel {
        val root = JPanel(BorderLayout(0, 14)).apply { border = BorderFactory.createEmptyBorder(22, 26, 22, 26) }

        val header = JPanel(BorderLayout(14, 0)).apply {
            isOpaque = false
            add(JLabel(FlatSVGIcon(LOGO_RESOURCE, 60, 60)), BorderLayout.WEST)
            add(JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(Box.createVerticalGlue())
                add(JLabel("Relict Client").apply { font = font.deriveFont(Font.BOLD, 26f) })
                add(JLabel("Finds your launchers and sets up Fabric and Relict.").apply { foreground = Theme.MUTED })
                add(Box.createVerticalGlue())
            }, BorderLayout.CENTER)
            add(JLabel("Installer ${InstallerInfo.version}").apply { foreground = Theme.MUTED; font = font.deriveFont(11f) }, BorderLayout.EAST)
        }

        updateBanner.apply {
            layout = BorderLayout(14, 0)
            border = BorderFactory.createEmptyBorder(12, 16, 12, 14)
            isVisible = false
            add(JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(updateTitle)
                add(updateDetail)
            }, BorderLayout.CENTER)
            add(JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(accentButton(updateNow).apply { alignmentX = RIGHT_ALIGNMENT })
                add(Box.createVerticalStrut(6))
                add(installerNow.apply { alignmentX = RIGHT_ALIGNMENT })
            }, BorderLayout.EAST)
        }

        statusPill.apply {
            layout = BorderLayout()
            border = BorderFactory.createEmptyBorder(3, 12, 3, 12)
            add(statusText.apply { font = font.deriveFont(Font.BOLD, 12f) })
        }

        val body = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(spaced(card("Launcher",
                row(targetBox, JPanel(BorderLayout(8, 0)).apply {
                    isOpaque = false
                    add(rescan, BorderLayout.WEST)
                    add(JButton("Browse…").apply { addActionListener { browse() } }, BorderLayout.EAST)
                }),
                targetNote.apply { border = BorderFactory.createEmptyBorder(6, 0, 0, 0) },
            )))
            add(versionCard)
            add(layoutCard)
            add(Box.createVerticalStrut(4))
            add(JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                alignmentX = LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, 42)
                add(accentButton(install).apply { preferredSize = Dimension(170, 42); maximumSize = Dimension(170, 42) })
                add(Box.createHorizontalStrut(10))
                add(uninstall.apply { preferredSize = Dimension(120, 42); maximumSize = Dimension(120, 42) })
                add(Box.createHorizontalGlue())
            })
            add(Box.createVerticalStrut(16))
            add(card("Updates",
                JPanel(BorderLayout(10, 0)).apply {
                    isOpaque = false
                    add(autoUpdate, BorderLayout.WEST)
                    add(checkNow, BorderLayout.EAST)
                },
            ))
            add(Box.createVerticalGlue()) // when cards are hidden the spare room goes here, not between the others
        }

        val bottom = JPanel(BorderLayout(0, 8)).apply {
            isOpaque = false
            add(progress, BorderLayout.NORTH)
            add(JScrollPane(log).apply {
                preferredSize = Dimension(540, 100)
                border = BorderFactory.createLineBorder(Theme.BORDER)
            }, BorderLayout.CENTER)
        }

        root.add(JPanel(BorderLayout(0, 12)).apply {
            isOpaque = false
            add(header, BorderLayout.NORTH)
            add(updateBanner, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        root.add(body, BorderLayout.CENTER)
        root.add(bottom, BorderLayout.SOUTH)
        return root
    }

    /** A rounded card with a small caps heading and the given content stacked under it. */
    private fun card(title: String, vararg content: java.awt.Component): RoundedPanel = RoundedPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = LEFT_ALIGNMENT
        border = BorderFactory.createEmptyBorder(14, 16, 14, 16)
        add(JLabel(title.uppercase()).apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Theme.MUTED
            alignmentX = LEFT_ALIGNMENT
            border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
        })
        content.forEach { (it as? JComponent)?.alignmentX = LEFT_ALIGNMENT; add(it) }
    }

    /** Space under a card that disappears together with it. */
    private fun spaced(card: JComponent): JPanel = object : JPanel(BorderLayout()) {
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }.apply {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = BorderFactory.createEmptyBorder(0, 0, 12, 0)
        add(card, BorderLayout.CENTER)
    }

    private fun row(main: JComponent, side: JComponent) = JPanel(BorderLayout(10, 0)).apply {
        isOpaque = false
        maximumSize = Dimension(Int.MAX_VALUE, 36)
        add(main, BorderLayout.CENTER)
        add(side, BorderLayout.EAST)
    }

    /** A clickable option: the radio button, its explanation, and a highlighted outline while chosen. */
    private fun tile(panel: RoundedPanel, button: JRadioButton, description: String): JComponent = panel.apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        fill = Theme.BACKGROUND
        border = BorderFactory.createEmptyBorder(10, 12, 10, 12)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        add(button.apply { alignmentX = LEFT_ALIGNMENT; isOpaque = false; font = font.deriveFont(Font.BOLD) })
        add(JLabel(description).apply {
            foreground = Theme.MUTED
            alignmentX = LEFT_ALIGNMENT
            font = font.deriveFont(12f)
            border = BorderFactory.createEmptyBorder(2, 24, 0, 0)
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                button.isSelected = true
                refreshTiles()
            }
        })
        maximumSize = Dimension(Int.MAX_VALUE, 64)
    }

    private fun accentButton(button: JButton): JButton = button.apply {
        background = Theme.ACCENT
        foreground = Color.WHITE
        font = font.deriveFont(Font.BOLD)
        putClientProperty("JButton.buttonType", "roundRect")
        isFocusPainted = false
    }

    private fun refreshTiles() {
        separateTile.outline = if (separate.isSelected) Theme.ACCENT else Theme.BORDER
        separateTile.fill = if (separate.isSelected) Theme.CARD_SELECTED else Theme.BACKGROUND
        mainTile.outline = if (main.isSelected) Theme.ACCENT else Theme.BORDER
        mainTile.fill = if (main.isSelected) Theme.CARD_SELECTED else Theme.BACKGROUND
        separateTile.repaint()
        mainTile.repaint()
    }

    /** One line per launcher in the drop-down: where it is, then what it runs in grey. */
    private class TargetRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): java.awt.Component {
            val t = value as? Target
            val detail = t?.let { listOfNotNull(it.minecraft?.let { v -> "Minecraft $v" }, it.loader?.replaceFirstChar(Char::uppercase)?.let { l -> l + (it.loaderVersion?.let { v -> " $v" } ?: "") }).joinToString(" · ") }.orEmpty()
            val text = if (t == null) "No launcher found" else "${t.label} <font color='#8b94a5'>&nbsp; $detail</font>"
            return super.getListCellRendererComponent(list, "<html>$text</html>", index, selected, focus)
        }
    }

    // ---- targets ----------------------------------------------------------------------------

    private fun newInstaller(source: Manifest, hosts: Set<String>) = Installer(source, HttpDownloader(), Resources::bundledJar, extraHosts = hosts)

    private fun loadVersions() {
        val previous = versions.selectedItem as String?
        versions.model = DefaultComboBoxModel(manifest.versions.map { it.minecraft }.toTypedArray())
        manifest.versions.indexOfFirst { it.minecraft == previous }.takeIf { it >= 0 }?.let { versions.selectedIndex = it }
    }

    /** Fills the launcher list, then picks the place the player most likely wants: where Relict already is, else the official launcher. */
    private fun loadTargets(selectBest: Boolean) {
        val previous = target
        val targets = allTargets
        targetBox.model = DefaultComboBoxModel(targets.toTypedArray())
        val keep = targets.indexOfFirst { it.dir == previous?.dir && it.name == previous.name }.takeIf { it >= 0 }
        val best = keep
            ?: targets.indexOfFirst { t -> entryOf(t)?.let { installer.status(it, t).installed } == true }.takeIf { it >= 0 }
            ?: targets.indexOfFirst { it.kind == LauncherKind.VANILLA }.takeIf { it >= 0 }
            ?: 0
        if (targets.isNotEmpty() && (selectBest || keep != null)) targetBox.selectedIndex = best
        suggestVersion()
    }

    /** For the official launcher, preselect the Minecraft version the player used last when Relict has a build for it. */
    private fun suggestVersion() {
        val t = target ?: return
        if (t.kind != LauncherKind.VANILLA) return
        val wanted = Launchers.lastPlayedVersion(t.dir)?.let { manifest.entryFor(it) } ?: return
        versions.selectedItem = wanted.minecraft
    }

    private fun entryOf(t: Target): VersionEntry? = t.minecraft?.let { manifest.entryFor(it) } ?: if (t.isInstance) null else manifest.versions.firstOrNull()

    private fun rescanLaunchers() {
        rescan.isEnabled = false
        object : SwingWorker<List<Target>, Unit>() {
            override fun doInBackground() = Launchers.detect()
            override fun done() {
                rescan.isEnabled = true
                found = runCatching { get() }.getOrDefault(found)
                loadTargets(selectBest = false)
                line("Found ${found.size} place(s) to install: ${found.map { it.label }.distinct().joinToString()}")
            }
        }.execute()
    }

    /** Lets the player point at a folder the detection didn't find: a launcher's data folder, or any game folder with mods in it. */
    private fun browse() {
        val chooser = JFileChooser(target?.dir?.toFile()).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Choose a Minecraft folder or an instance's game folder" }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val dir = chooser.selectedFile.toPath()
        val picked = if (MinecraftDir.isValid(dir)) Target(LauncherKind.VANILLA, LauncherKind.VANILLA.label, null, null, null, dir)
        else Target(LauncherKind.CUSTOM, dir.fileName?.toString() ?: dir.toString(), null, null, null, dir)
        custom.removeAll { it.dir == picked.dir }
        found = found.filterNot { it.dir == picked.dir && it.kind == picked.kind }
        custom += picked
        loadTargets(selectBest = false)
        targetBox.selectedItem = allTargets.first { it.dir == picked.dir && it.kind == picked.kind }
    }

    // ---- state ------------------------------------------------------------------------------

    /** Re-reads what is installed and enables the buttons that make sense. */
    private fun refresh() {
        val t = target
        val e = entry
        versionCard.isVisible = t != null && t.minecraft == null
        layoutCard.isVisible = t?.kind == LauncherKind.VANILLA

        val problem = problemWith(t, e)
        targetNote.text = when {
            t == null -> "No launcher was found. Use Browse… to pick a Minecraft folder."
            problem != null -> problem
            t.kind == LauncherKind.VANILLA -> "Found the Minecraft launcher data."
            else -> "Ready: Relict goes into this instance's mods folder."
        }
        targetNote.foreground = if (t != null && problem == null) Theme.GOOD else Theme.WARN

        val s = if (t != null && e != null && problem == null) installer.status(e, t) else Status(null, false)
        val (text, color) = when {
            !s.installed -> "Not installed" to Theme.MUTED
            s.needsUpdate -> "v${s.installedVersion} · update available" to Theme.WARN
            else -> "v${s.installedVersion} · up to date" to Theme.GOOD
        }
        statusText.text = text
        statusText.foreground = color
        statusPill.outline = color
        statusPill.repaint()

        val blocked = t == null || e == null || problem?.startsWith("Open the Minecraft Launcher") == true || (t.minecraft != null && e == null)
        install.text = if (s.installed) "Update / Repair" else "Install"
        install.isEnabled = !blocked && !busy
        uninstall.isEnabled = !blocked && s.installed && !busy
        updateNow.isEnabled = !busy
        installerNow.isEnabled = !busy
        checkNow.isEnabled = !busy
        rescan.isEnabled = !busy
    }

    /** What is known to be wrong with installing here, or null. */
    private fun problemWith(t: Target?, e: VersionEntry?): String? {
        t ?: return null
        if (t.kind == LauncherKind.VANILLA && !runCatching { MinecraftDir.isValid(t.dir) }.getOrDefault(false)) {
            return "Open the Minecraft Launcher once, then press Rescan."
        }
        if (t.minecraft != null && e == null) return "Relict doesn't support Minecraft ${t.minecraft}. It runs on ${manifest.versions.joinToString { it.minecraft }}."
        return t.fabricProblem()
    }

    private fun line(text: String) = SwingUtilities.invokeLater {
        log.append(text + "\n")
        log.caretPosition = log.document.length
    }

    /** Runs [work] off the UI thread and reports the outcome. */
    private fun run(title: String, onDone: () -> Unit = {}, work: () -> Unit) {
        busy = true
        progress.isVisible = true
        refresh()
        line("$title…")
        object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() = work()
            override fun done() {
                busy = false
                this@InstallerWindow.progress.isVisible = false // (SwingWorker has its own `progress`)
                try {
                    get()
                    onDone()
                } catch (e: Exception) {
                    val message = e.cause?.message ?: e.message ?: "Unknown error"
                    line("Failed: $message")
                    JOptionPane.showMessageDialog(this@InstallerWindow, message, "$title failed", JOptionPane.ERROR_MESSAGE)
                }
                refresh()
            }
        }.execute()
    }

    // ---- updates ----------------------------------------------------------------------------

    private fun banner(title: String, detail: String, updateAction: Boolean = false, installerAction: Boolean = false) {
        updateTitle.text = title
        updateDetail.text = detail
        updateNow.isVisible = updateAction
        installerNow.isVisible = installerAction
        updateBanner.isVisible = true
        updateBanner.revalidate()
    }

    /** Asks the update feed for a newer build. Quietly does nothing on failure unless the user asked ([manual]). */
    private fun checkForUpdates(manual: Boolean) {
        if (feedUrl == null) {
            if (manual) banner("Updates aren't set up", "This build of the installer has no update address, so it only installs the Relict it came with.")
            return
        }
        checkNow.isEnabled = false
        if (manual) banner("Checking for updates…", " ")
        object : SwingWorker<UpdateCheck, Unit>() {
            override fun doInBackground() = UpdateChecker(feedUrl, HttpDownloader(), bundled.relictVersion).check()
            override fun done() {
                checkNow.isEnabled = !busy
                val result = try { get() } catch (e: Exception) { UpdateCheck.Failed(e.message ?: "Update check failed") }
                onUpdateChecked(result, manual)
            }
        }.execute()
    }

    private fun onUpdateChecked(result: UpdateCheck, manual: Boolean) {
        when (result) {
            is UpdateCheck.Available -> {
                pendingUpdate = result
                if (result.newRelict) {
                    manifest = result.feed
                    installer = newInstaller(result.feed, result.hosts)
                    loadVersions()
                    loadTargets(selectBest = false)
                }
                refresh()
                val outdated = outdated().size
                if (result.newRelict && settings.autoUpdate && outdated > 0) {
                    applyUpdate()
                } else {
                    val what = listOfNotNull(
                        "Relict ${result.feed.relictVersion}".takeIf { result.newRelict },
                        "installer ${result.feed.installer?.version}".takeIf { result.installerFile != null },
                    ).joinToString(" and ")
                    banner("A new $what is available", result.feed.notes ?: "You have Relict ${bundled.relictVersion}.", result.newRelict, result.installerFile != null)
                }
            }
            is UpdateCheck.UpToDate -> if (manual) banner("You're up to date", "Relict ${result.version} is the newest build.") else updateBanner.isVisible = false
            is UpdateCheck.Failed -> if (manual) banner("Couldn't check for updates", result.message)
            UpdateCheck.NotConfigured -> Unit
        }
    }

    /** Every install the installer can see, on any launcher, that is older than the manifest. */
    private fun outdated(): List<Pair<Target, VersionEntry>> = allTargets.flatMap { t ->
        val entries = if (t.isInstance) listOfNotNull(t.minecraft?.let { manifest.entryFor(it) }) else manifest.versions
        entries.filter { installer.status(it, t).let { s -> s.installed && s.needsUpdate } }.map { t to it }
    }

    /** Installs the newer build everywhere Relict is installed and behind. */
    private fun applyUpdate() {
        val update = pendingUpdate ?: return
        val work = outdated()
        if (work.isEmpty()) {
            banner("Relict ${update.feed.relictVersion} is ready", "Press Install to get it.", installerAction = update.installerFile != null)
            return
        }
        banner("Updating to Relict ${update.feed.relictVersion}…", work.joinToString { "${it.first.name} ${it.second.minecraft}" })
        run("Updating Relict", onDone = {
            banner("Updated to Relict ${update.feed.relictVersion}", "Restart Minecraft to use it.", installerAction = update.installerFile != null)
        }) {
            work.forEach { (t, e) -> installer.update(e, t) { text -> line(text) } }
        }
    }

    /** The installer app can't replace itself while it runs, so the new one is saved to Downloads and opened. */
    private fun downloadInstaller() {
        val update = pendingUpdate ?: return
        val file = update.installerFile ?: return
        run("Downloading installer ${update.feed.installer?.version}", onDone = {
            banner("Installer saved to Downloads", "Open ${file.file} to finish: it replaces this installer.")
        }) {
            val saved = InstallerDownload.download(file, update.hosts, HttpDownloader(), Path.of(System.getProperty("user.home"), "Downloads"))
            line("Saved $saved")
            runCatching { Desktop.getDesktop().open(saved.toFile()) }
        }
    }

    /** What the window looks like when a newer build is waiting (used by --screenshot --update). */
    private fun showPreviewUpdate() = banner("A new Relict 0.2.0 and installer 1.3.0 is available", "You have Relict ${bundled.relictVersion}. Adds Donut ESP improvements.", true, true)
}
