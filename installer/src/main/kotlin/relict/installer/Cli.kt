package relict.installer

import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * A headless way to run the installer, for scripts and testing:
 *
 *     RelictInstaller --cli list                                   the launchers and instances it found, numbered
 *     RelictInstaller --cli install|uninstall|status --target 3    one of those (an instance knows its Minecraft version)
 *     RelictInstaller --cli install 1.21.11 [--dir <minecraft folder>] [--layout separate|main]
 *     RelictInstaller --cli install 1.21.11 --instance <game folder>     any folder that has (or gets) a mods folder
 *     RelictInstaller --cli uninstall 1.21.11 [--dir <minecraft folder>]
 *     RelictInstaller --cli status 1.21.11 [--dir <minecraft folder>]
 *     RelictInstaller --cli check                                  looks for a newer Relict or installer in the update feed
 *     RelictInstaller --cli update                                 installs the newer Relict wherever Relict is installed
 */
object Cli {
    fun run(args: List<String>): Nothing {
        val code = try {
            execute(args)
            0
        } catch (e: InstallException) {
            System.err.println("Error: ${e.message}")
            1
        } catch (e: IllegalArgumentException) {
            System.err.println("Error: ${e.message}")
            2
        }
        exitProcess(code)
    }

    private const val USAGE = "Usage: --cli <list|check|update>, or --cli <install|uninstall|status> (--target <n> | <minecraft version> [--dir <path> | --instance <path>] [--layout separate|main])"

    private fun execute(args: List<String>) {
        require(args.isNotEmpty()) { USAGE }
        val command = args[0]
        fun option(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }

        val manifest = Manifest.bundled()
        val installer = Installer(manifest, HttpDownloader(), Resources::bundledJar)
        val targets = Launchers.detect()

        when (command) {
            "list" -> return list(manifest, installer, targets)
            "check", "update" -> return updates(command, manifest, targets)
        }

        val (target, entry) = resolve(args, manifest, targets, ::option)
        val layout = when (option("--layout") ?: "separate") {
            "separate" -> Layout.SEPARATE_INSTANCE
            "main" -> Layout.MAIN_FOLDER
            else -> throw IllegalArgumentException("--layout must be 'separate' or 'main'")
        }
        when (command) {
            "install" -> installer.install(entry, target, layout, ::println)
            "uninstall" -> installer.uninstall(entry, target, ::println)
            "status" -> println(installer.status(entry, target))
            else -> throw IllegalArgumentException("Unknown command '$command'. $USAGE")
        }
    }

    /** Which place and which build the command is about. */
    private fun resolve(args: List<String>, manifest: Manifest, targets: List<Target>, option: (String) -> String?): Pair<Target, VersionEntry> {
        option("--target")?.let { n ->
            val target = targets.getOrNull(n.toIntOrNull()?.minus(1) ?: -1) ?: throw IllegalArgumentException("No target $n. Run --cli list.")
            val version = target.minecraft ?: args.getOrNull(1)?.takeUnless { it.startsWith("--") }
                ?: throw IllegalArgumentException("${target.label} has no fixed Minecraft version: add one, e.g. 1.21.11")
            return target to supported(manifest, version)
        }
        val version = args.getOrNull(1)?.takeUnless { it.startsWith("--") } ?: throw IllegalArgumentException(USAGE)
        val entry = supported(manifest, version)
        option("--instance")?.let { return Target(LauncherKind.CUSTOM, Path.of(it).fileName.toString(), null, null, null, Path.of(it)) to entry }
        return Target(LauncherKind.VANILLA, LauncherKind.VANILLA.label, null, null, null, option("--dir")?.let { Path.of(it) } ?: MinecraftDir.default()) to entry
    }

    private fun supported(manifest: Manifest, version: String) = manifest.entryFor(version)
        ?: throw IllegalArgumentException("Unsupported Minecraft version $version. Supported: ${manifest.versions.joinToString { it.minecraft }}")

    private fun list(manifest: Manifest, installer: Installer, targets: List<Target>) {
        if (targets.isEmpty()) println("No launchers found.")
        targets.forEachIndexed { i, t ->
            val entry = t.minecraft?.let { manifest.entryFor(it) }
            val state = when {
                t.minecraft != null && entry == null -> "unsupported Minecraft"
                entry != null -> installer.status(entry, t).let { if (it.installed) "installed ${it.installedVersion}" else "not installed" }
                else -> "any version"
            }
            val loader = t.loader?.let { l -> l + (t.loaderVersion?.let { " $it" } ?: "") } ?: "?"
            println("${i + 1}. ${t.label} | Minecraft ${t.minecraft ?: "any"} | $loader | $state | ${t.dir}")
        }
    }

    private fun updates(command: String, bundled: Manifest, targets: List<Target>) {
        when (val result = UpdateChecker(UpdateConfig.feedUrl(), HttpDownloader(), bundled.relictVersion).check()) {
            UpdateCheck.NotConfigured -> println("Updates aren't set up in this build (no feed address).")
            is UpdateCheck.UpToDate -> println("Up to date (Relict ${result.version}).")
            is UpdateCheck.Failed -> throw InstallException(result.message)
            is UpdateCheck.Available -> {
                if (result.newRelict) println("Relict ${result.feed.relictVersion} is available (this installer has ${bundled.relictVersion}).")
                result.installerFile?.let { println("A newer installer (${result.feed.installer?.version}) is available: ${it.file}") }
                if (command == "check" || !result.newRelict) return
                val installer = Installer(result.feed, HttpDownloader(), Resources::bundledJar, extraHosts = result.hosts)
                var updated = 0
                for (target in targets) {
                    val entries = if (target.isInstance) listOfNotNull(target.minecraft?.let { result.feed.entryFor(it) }) else result.feed.versions
                    for (entry in entries) if (installer.update(entry, target, ::println)) updated++
                }
                println(if (updated == 0) "Relict isn't installed anywhere the installer can see, so nothing was updated." else "Updated $updated install(s).")
            }
        }
    }
}
