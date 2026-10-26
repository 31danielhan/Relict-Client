package relict.installer

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstanceInstallTest {
    private val tmp: Path = createTempDirectory("relict-instance-test")
    private val game: Path = tmp.resolve("instances/1.21.10/minecraft")

    private fun sha512(bytes: ByteArray) = MessageDigest.getInstance("SHA-512").digest(bytes).joinToString("") { "%02x".format(it) }

    private val fabricApi = "fabric-api new".toByteArray()
    private val relictJar = "relict jar".toByteArray()
    private val requested = mutableListOf<String>()

    private val entry = VersionEntry(
        "1.21.10", "0.20.1", "relict-mc1.21.10-0.1.0.jar",
        listOf(ModFile("fabric-api", "fabric-api-0.2.jar", "https://cdn.modrinth.com/data/a/fabric-api.jar", sha512(fabricApi), fabricApi.size.toLong())),
    )

    private val downloader = Downloader { url ->
        requested += url
        if (url.endsWith("fabric-api.jar")) fabricApi else throw IOException("404 $url")
    }

    private fun installer(version: String = "0.1.0", dl: Downloader = downloader) = Installer(Manifest(version, listOf(entry)), dl, { relictJar })

    @AfterTest
    fun cleanUp() {
        tmp.toFile().deleteRecursively()
    }

    @Test
    fun installsIntoTheInstancesModsFolder() {
        game.createDirectories()
        val target = Target(LauncherKind.PRISM, "1.21.10", "1.21.10", "fabric", "0.20.1", game)
        installer().install(entry, target, Layout.MAIN_FOLDER) // the layout is ignored for an instance

        assertEquals("relict jar", game.resolve("mods/relict-mc1.21.10-0.1.0.jar").readText())
        assertEquals("fabric-api new", game.resolve("mods/fabric-api-0.2.jar").readText())
        assertTrue(installer().status(entry, target).installed)
        // No Fabric profile was fetched and no launcher file exists: the launcher owns those.
        assertTrue(requested.none { "meta.fabricmc.net" in it })
        assertFalse(tmp.resolve("launcher_profiles.json").exists())
    }

    @Test
    fun setsAsideAnOlderFabricApiAndRestoresItOnUninstall() {
        game.resolve("mods").createDirectories()
        game.resolve("mods/fabric-api-0.1.jar").writeText("player's old fabric api")
        game.resolve("mods/sodium.jar").writeText("sodium")
        val target = Target(LauncherKind.PRISM, "x", "1.21.10", "fabric", "0.20.1", game)

        installer().install(entry, target, Layout.INSTANCE)
        assertFalse(game.resolve("mods/fabric-api-0.1.jar").exists())
        assertEquals("sodium", game.resolve("mods/sodium.jar").readText())

        installer().uninstall(entry, target)
        assertEquals("player's old fabric api", game.resolve("mods/fabric-api-0.1.jar").readText())
        assertEquals("sodium", game.resolve("mods/sodium.jar").readText())
        assertFalse(game.resolve("mods/relict-mc1.21.10-0.1.0.jar").exists())
        assertFalse(game.resolve("mods/fabric-api-0.2.jar").exists())
        assertFalse(game.resolve("relict").exists())
        assertFalse(installer().status(entry, target).installed)
    }

    @Test
    fun updatesAnInstanceAndLeavesItWorkingIfTheDownloadFails() {
        game.createDirectories()
        val target = Target(LauncherKind.MODRINTH, "p", "1.21.10", "fabric", "0.20.1", game)
        installer("0.1.0").install(entry, target, Layout.INSTANCE)

        val offline = Downloader { throw IOException("offline") }
        assertFailsWith<InstallException> { installer("0.2.0", offline).update(entry, target) }
        assertEquals("0.1.0", installer("0.1.0").status(entry, target).installedVersion)
        assertTrue(game.resolve("mods/relict-mc1.21.10-0.1.0.jar").exists())

        val newer = entry.copy(relictJar = "relict-mc1.21.10-0.2.0.jar")
        assertTrue(Installer(Manifest("0.2.0", listOf(newer)), downloader, { relictJar }).update(newer, target))
        assertTrue(game.resolve("mods/relict-mc1.21.10-0.2.0.jar").exists())
        assertFalse(game.resolve("mods/relict-mc1.21.10-0.1.0.jar").exists())
    }

    @Test
    fun updateDoesNothingWhereRelictIsNotInstalled() {
        game.createDirectories()
        val target = Target(LauncherKind.PRISM, "x", "1.21.10", "fabric", "0.20.1", game)
        assertFalse(installer().update(entry, target))
        assertFalse(game.resolve("mods").exists())
    }

    @Test
    fun refusesAFolderThatDoesNotExist() {
        val target = Target(LauncherKind.CUSTOM, "x", null, null, null, tmp.resolve("nope/deeper/minecraft"))
        assertFailsWith<InstallException> { installer().install(entry, target, Layout.INSTANCE) }
    }

    @Test
    fun aTamperedDependencyChangesNothingInTheInstance() {
        game.createDirectories()
        val target = Target(LauncherKind.PRISM, "x", "1.21.10", "fabric", "0.20.1", game)
        val bad = entry.copy(mods = listOf(entry.mods[0].copy(sha512 = sha512("other".toByteArray()))))
        assertFailsWith<InstallException> { installer().install(bad, target, Layout.INSTANCE) }
        assertFalse(game.resolve("mods").exists())
    }

    @Test
    fun anInstallMadeBeforeTheRenameIsReplaced() {
        // What the installer wrote when this client was still called Veyly.
        val mc = tmp.resolve("minecraft").also { it.createDirectories() }
        val oldInstance = mc.resolve("veyly-instances/1.21.10")
        oldInstance.resolve("mods").createDirectories()
        oldInstance.resolve("mods/veyly-mc1.21.10-0.1.0.jar").writeText("old veyly")
        oldInstance.resolve("saves").createDirectories()
        mc.resolve("launcher_profiles.json").writeText("""{"profiles":{"veyly-1.21.10":{"name":"Veyly 1.21.10"}},"version":3}""")
        mc.resolve("veyly-installer").createDirectories()
        mc.resolve("veyly-installer/1.21.10.json").writeText(
            """{"veylyVersion":"0.1.0","layout":"SEPARATE_INSTANCE","gameDir":"$oldInstance","fabricVersionId":"fabric-loader-0.20.1-1.21.10",
               "profileKey":"veyly-1.21.10","createdVersionDir":false,"files":["mods/veyly-mc1.21.10-0.1.0.jar"]}""".replace("\\", "/")
        )
        val dl = Downloader { url ->
            when {
                url.endsWith("fabric-api.jar") -> fabricApi
                else -> """{"id":"fabric-loader-0.20.1-1.21.10","inheritsFrom":"1.21.10"}""".toByteArray()
            }
        }
        val i = installer(dl = dl)
        assertEquals("0.1.0", i.status(entry, mc).installedVersion)
        i.install(entry, mc, Layout.SEPARATE_INSTANCE)

        assertFalse(oldInstance.resolve("mods/veyly-mc1.21.10-0.1.0.jar").exists())
        assertTrue(oldInstance.resolve("mods/relict-mc1.21.10-0.1.0.jar").exists()) // same folder: the saves carry on
        assertTrue(oldInstance.resolve("saves").exists())
        assertFalse(mc.resolve("veyly-installer/1.21.10.json").exists())
        assertTrue(mc.resolve("relict-installer/1.21.10.json").exists())
        val profiles = mc.resolve("launcher_profiles.json").readText()
        assertTrue("relict-1.21.10" in profiles && "veyly-1.21.10" !in profiles)
    }
}
