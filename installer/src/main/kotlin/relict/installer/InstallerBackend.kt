package relict.installer

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Everything the installer does, with no screen attached. The window is a web page that calls [handle] with a method
 * name and parameters and gets JSON back; progress and update news come the other way through [emit].
 *
 * Targets are named by a key (see [keyOf]) rather than a list position, so the page's choice survives a rescan.
 */
class InstallerBackend(
    private val bundled: Manifest,
    /** Show the "update available" state without a network (for screenshots). */
    private val preview: Boolean = false,
    private val settingsFile: Path = Settings.defaultFile(),
    private val feedUrl: String? = UpdateConfig.feedUrl(),
    detected: List<Target> = Launchers.detect(),
    private val downloader: Downloader = HttpDownloader(),
    /** Reads a Relict jar that ships inside the installer. */
    private val bundledJar: (String) -> ByteArray = Resources::bundledJar,
    private val downloads: Path = Path.of(System.getProperty("user.home"), "Downloads"),
    /** Opens a file with the system (used for the new installer once it is downloaded). */
    private val open: (Path) -> Unit = {},
    /** Asks the player for a folder, starting at the given one; null if they cancel. */
    private val chooseFolder: (Path?) -> Path? = { null },
    private val emit: (event: String, payload: JsonElement) -> Unit = { _, _ -> },
    /** Start on the place at this position in the list (for screenshots). */
    private val initialSelection: Int? = null,
) {
    /** The bundled manifest, or the newer one from the update feed once an update was found. */
    private var manifest = bundled
    private var installer = newInstaller(bundled, emptySet())
    private var settings = Settings.load(settingsFile)

    private var found: List<Target> = detected
    private val custom = mutableListOf<Target>()
    private val allTargets get() = found + custom

    private var pendingUpdate: UpdateCheck.Available? = null
    private val busy = AtomicBoolean(false)

    fun handle(method: String, params: JsonObject): JsonElement = when (method) {
        "init" -> snapshot(params.str("selected"))
        "rescan" -> {
            found = Launchers.detect()
            log("Found ${found.size} place(s) to install: ${found.map { it.label }.distinct().joinToString()}")
            snapshot(params.str("selected"))
        }
        "browse" -> browse(params.str("selected"))
        "status" -> status(target(params.str("target")), params.str("version"))
        "install" -> install(params)
        "uninstall" -> uninstall(params)
        "autoUpdate" -> {
            settings = settings.copy(autoUpdate = params["value"]?.asBoolean ?: true)
            settings.save(settingsFile)
            JsonObject()
        }
        "updates.startup" -> {
            if (preview) previewUpdate() else if (settings.checkOnLaunch && feedUrl != null) checkForUpdates(manual = false)
            JsonObject()
        }
        "updates.check" -> { checkForUpdates(manual = true); JsonObject() }
        "updates.apply" -> { applyUpdate(); JsonObject() }
        "updates.installer" -> { downloadInstaller(); JsonObject() }
        else -> throw IllegalArgumentException("Unknown method '$method'")
    }

    // ---- targets ---------------------------------------------------------------------------

    private fun newInstaller(source: Manifest, hosts: Set<String>) = Installer(source, downloader, bundledJar, extraHosts = hosts)

    private fun keyOf(t: Target) = "${t.kind.name}|${t.dir}|${t.name}"

    private fun target(key: String?): Target = allTargets.firstOrNull { keyOf(it) == key } ?: throw IllegalArgumentException("That place is no longer in the list. Press Rescan.")

    /** The build that goes with a place: fixed by an instance's Minecraft version (null if Relict has none for it), chosen for the official launcher. */
    private fun entryFor(t: Target, version: String?): VersionEntry? {
        t.minecraft?.let { return manifest.entryFor(it) }
        return manifest.versions.firstOrNull { it.minecraft == version } ?: manifest.versions.firstOrNull()
    }

    /** The build used when judging a place for the list: its own Minecraft version, or the first Relict build for a launcher that lets you pick. */
    private fun entryOf(t: Target): VersionEntry? = t.minecraft?.let { manifest.entryFor(it) } ?: if (t.isInstance) null else manifest.versions.firstOrNull()

    /** The places and builds, plus which place to start on: the one chosen before, else where Relict already is, else the official launcher. */
    private fun snapshot(previousKey: String?): JsonObject {
        val targets = allTargets
        val keep = targets.firstOrNull { keyOf(it) == previousKey } ?: if (previousKey == null) initialSelection?.let { targets.getOrNull(it) } else null
        val best = keep
            ?: targets.firstOrNull { t -> entryOf(t)?.let { installer.status(it, t).installed } == true }
            ?: targets.firstOrNull { it.kind == LauncherKind.VANILLA }
            ?: targets.firstOrNull()
        return JsonObject().apply {
            addProperty("installerVersion", InstallerInfo.version)
            addProperty("relictVersion", manifest.relictVersion)
            addProperty("autoUpdate", settings.autoUpdate)
            addProperty("updatesConfigured", feedUrl != null)
            add("versions", JsonArray().also { a -> manifest.versions.forEach { a.add(it.minecraft) } })
            add("targets", JsonArray().also { a -> targets.forEach { a.add(describe(it)) } })
            best?.let { addProperty("selected", keyOf(it)) }
        }
    }

    private fun describe(t: Target): JsonObject = JsonObject().apply {
        addProperty("key", keyOf(t))
        addProperty("kind", t.kind.name)
        addProperty("launcher", t.kind.label)
        addProperty("name", t.name)
        addProperty("label", t.label)
        addProperty("dir", t.dir.toString())
        addProperty("minecraft", t.minecraft)
        addProperty("loader", t.loader)
        addProperty("loaderVersion", t.loaderVersion)
        addProperty("instance", t.isInstance)
        addProperty("detail", listOfNotNull(
            t.minecraft?.let { "Minecraft $it" },
            t.loader?.replaceFirstChar(Char::uppercase)?.let { l -> l + (t.loaderVersion?.let { v -> " $v" } ?: "") },
        ).joinToString(" · "))
        // For the official launcher, the Minecraft version the player used last when Relict has a build for it.
        if (t.kind == LauncherKind.VANILLA) addProperty("suggestedVersion", Launchers.lastPlayedVersion(t.dir)?.let { manifest.entryFor(it) }?.minecraft)
        val e = entryOf(t)
        val s = if (e != null && problemWith(t, e) == null) installer.status(e, t) else null
        addProperty("state", when {
            t.minecraft != null && e == null -> "unsupported"
            s == null || !s.installed -> "none"
            s.needsUpdate -> "update"
            else -> "installed"
        })
    }

    /** Lets the player point at a folder the detection didn't find: a launcher's data folder, or any game folder with mods in it. */
    private fun browse(selected: String?): JsonElement {
        val dir = chooseFolder(target(selected).dir) ?: return snapshot(selected)
        val picked = if (MinecraftDir.isValid(dir)) Target(LauncherKind.VANILLA, LauncherKind.VANILLA.label, null, null, null, dir)
        else Target(LauncherKind.CUSTOM, dir.fileName?.toString() ?: dir.toString(), null, null, null, dir)
        custom.removeAll { it.dir == picked.dir }
        found = found.filterNot { it.dir == picked.dir && it.kind == picked.kind }
        custom += picked
        return snapshot(keyOf(picked))
    }

    // ---- state of one place ----------------------------------------------------------------

    /** What is known to be wrong with installing here, or null. */
    private fun problemWith(t: Target, e: VersionEntry?): String? {
        if (t.kind == LauncherKind.VANILLA && !runCatching { MinecraftDir.isValid(t.dir) }.getOrDefault(false)) {
            return "Open the Minecraft Launcher once, then press Rescan."
        }
        if (t.minecraft != null && e == null) return "Relict doesn't support Minecraft ${t.minecraft}. It runs on ${manifest.versions.joinToString { it.minecraft }}."
        return t.fabricProblem()
    }

    private fun status(t: Target, version: String?): JsonObject {
        val e = entryFor(t, version)
        val problem = problemWith(t, e)
        val s = if (e != null && problem == null) installer.status(e, t) else Status(null, false)
        val blocked = e == null || problem?.startsWith("Open the Minecraft Launcher") == true
        return JsonObject().apply {
            addProperty("note", problem ?: if (t.kind == LauncherKind.VANILLA) "Found the Minecraft launcher data." else "Ready: Relict goes into this instance's mods folder.")
            addProperty("problem", problem != null)
            addProperty("installed", s.installed)
            addProperty("installedVersion", s.installedVersion)
            addProperty("needsUpdate", s.needsUpdate)
            addProperty("blocked", blocked)
            addProperty("minecraft", e?.minecraft)
            addProperty("pickVersion", t.minecraft == null)
            addProperty("pickLayout", t.kind == LauncherKind.VANILLA)
            addProperty("busy", busy.get())
        }
    }

    // ---- install and uninstall -------------------------------------------------------------

    private fun layoutOf(params: JsonObject) = if (params.str("layout") == "main") Layout.MAIN_FOLDER else Layout.SEPARATE_INSTANCE

    private fun install(params: JsonObject): JsonElement {
        val t = target(params.str("target"))
        val e = entryFor(t, params.str("version")) ?: throw IllegalArgumentException("Relict doesn't support Minecraft ${t.minecraft}.")
        work("Installing Relict ${e.minecraft}") { installer.install(e, t, layoutOf(params)) { log(it) } }
        return status(t, params.str("version"))
    }

    private fun uninstall(params: JsonObject): JsonElement {
        val t = target(params.str("target"))
        val e = entryFor(t, params.str("version")) ?: throw IllegalArgumentException("Relict doesn't support Minecraft ${t.minecraft}.")
        work("Uninstalling Relict") { installer.uninstall(e, t) { log(it) } }
        return status(t, params.str("version"))
    }

    /** Runs [action] as the one thing happening right now: tells the page it is busy, logs a failure, and rethrows it. */
    private fun work(title: String, action: () -> Unit) {
        if (!busy.compareAndSet(false, true)) throw IllegalStateException("Something else is still running.")
        emit("busy", JsonObject().apply { addProperty("value", true); addProperty("title", title) })
        log("$title…")
        try {
            action()
        } catch (e: Exception) {
            log("Failed: ${e.message ?: "Unknown error"}")
            throw e
        } finally {
            busy.set(false)
            emit("busy", JsonObject().apply { addProperty("value", false) })
        }
    }

    private fun log(text: String) = emit("log", JsonObject().apply { addProperty("text", text) })

    // ---- updates ---------------------------------------------------------------------------

    private fun banner(title: String, detail: String, updateAction: Boolean = false, installerAction: Boolean = false) =
        emit("banner", JsonObject().apply {
            addProperty("visible", true)
            addProperty("title", title)
            addProperty("detail", detail)
            addProperty("updateAction", updateAction)
            addProperty("installerAction", installerAction)
        })

    private fun hideBanner() = emit("banner", JsonObject().apply { addProperty("visible", false) })

    /** Asks the update feed for a newer build. Quietly does nothing on failure unless the user asked ([manual]). */
    private fun checkForUpdates(manual: Boolean) {
        val url = feedUrl
        if (url == null) {
            if (manual) banner("Updates aren't set up", "This build of the installer has no update address, so it only installs the Relict it came with.")
            return
        }
        if (manual) banner("Checking for updates…", " ")
        val result = try {
            UpdateChecker(url, downloader, bundled.relictVersion).check()
        } catch (e: Exception) {
            UpdateCheck.Failed(e.message ?: "Update check failed")
        }
        onUpdateChecked(result, manual)
    }

    private fun onUpdateChecked(result: UpdateCheck, manual: Boolean) {
        when (result) {
            is UpdateCheck.Available -> {
                pendingUpdate = result
                if (result.newRelict) {
                    manifest = result.feed
                    installer = newInstaller(result.feed, result.hosts)
                    emit("snapshot", snapshot(null).also { it.remove("selected") })
                }
                if (result.newRelict && settings.autoUpdate && outdated().isNotEmpty()) {
                    applyUpdate()
                } else {
                    val what = listOfNotNull(
                        "Relict ${result.feed.relictVersion}".takeIf { result.newRelict },
                        "installer ${result.feed.installer?.version}".takeIf { result.installerFile != null },
                    ).joinToString(" and ")
                    banner("A new $what is available", result.feed.notes ?: "You have Relict ${bundled.relictVersion}.", result.newRelict, result.installerFile != null)
                }
            }
            is UpdateCheck.UpToDate -> if (manual) banner("You're up to date", "Relict ${result.version} is the newest build.") else hideBanner()
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
        val todo = outdated()
        if (todo.isEmpty()) {
            banner("Relict ${update.feed.relictVersion} is ready", "Press Install to get it.", installerAction = update.installerFile != null)
            return
        }
        banner("Updating to Relict ${update.feed.relictVersion}…", todo.joinToString { "${it.first.name} ${it.second.minecraft}" })
        try {
            work("Updating Relict") { todo.forEach { (t, e) -> installer.update(e, t) { text -> log(text) } } }
            banner("Updated to Relict ${update.feed.relictVersion}", "Restart Minecraft to use it.", installerAction = update.installerFile != null)
            emit("snapshot", snapshot(null).also { it.remove("selected") })
        } catch (e: Exception) {
            banner("Update failed", e.message ?: "Unknown error", updateAction = true, installerAction = update.installerFile != null)
        }
    }

    /** The installer app can't replace itself while it runs, so the new one is saved to Downloads and opened. */
    private fun downloadInstaller() {
        val update = pendingUpdate ?: return
        val file = update.installerFile ?: return
        work("Downloading installer ${update.feed.installer?.version}") {
            val saved = InstallerDownload.download(file, update.hosts, downloader, downloads)
            log("Saved $saved")
            runCatching { open(saved) }
        }
        banner("Installer saved to Downloads", "Open ${file.file} to finish: it replaces this installer.")
    }

    /** What the window looks like when a newer build is waiting (used by --screenshot --update). */
    private fun previewUpdate() = banner("A new Relict 0.2.0 and installer 1.3.0 is available", "You have Relict ${bundled.relictVersion}. Adds Donut ESP improvements.", true, true)

    private fun JsonObject.str(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString
}

/** For the web page: a result or error as one JSON string. */
fun InstallerBackend.respond(method: String, params: JsonObject): String =
    JsonObject().also { out ->
        try {
            out.add("ok", handle(method, params))
        } catch (e: Exception) {
            out.add("error", JsonPrimitive(e.message ?: e.javaClass.simpleName))
        }
    }.toString()
