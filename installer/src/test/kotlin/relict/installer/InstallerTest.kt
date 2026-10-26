package relict.installer

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

class InstallerTest {
    private val tmp: Path = createTempDirectory("relict-installer-test")
    private val mc: Path = tmp.resolve("minecraft")

    private val fabricApi = "fabric-api bytes".toByteArray()
    private val kotlinLib = "kotlin bytes".toByteArray()
    private val relictJar = "relict jar".toByteArray()

    private fun sha512(bytes: ByteArray) = MessageDigest.getInstance("SHA-512").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun entry(mcVersion: String = "1.21.11", fabricApiHash: String = sha512(fabricApi)) = VersionEntry(
        minecraft = mcVersion,
        fabricLoader = "0.20.1",
        relictJar = "relict-mc$mcVersion-0.1.0.jar",
        mods = listOf(
            ModFile("fabric-api", "fabric-api-0.141.6+$mcVersion.jar", "https://cdn.modrinth.com/data/a/fabric-api.jar", fabricApiHash, fabricApi.size.toLong()),
            ModFile("fabric-language-kotlin", "fabric-language-kotlin-1.14.2.jar", "https://cdn.modrinth.com/data/b/flk.jar", sha512(kotlinLib), kotlinLib.size.toLong()),
        ),
    )

    private fun fabricProfile(mcVersion: String) =
        """{"id":"fabric-loader-0.20.1-$mcVersion","inheritsFrom":"$mcVersion","mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient","libraries":[]}"""

    private val requested = mutableListOf<String>()

    private val downloader = Downloader { url ->
        requested += url
        when {
            url.endsWith("fabric-api.jar") -> fabricApi
            url.endsWith("flk.jar") -> kotlinLib
            url.contains("meta.fabricmc.net") -> fabricProfile(Regex("loader/([^/]+)/").find(url)!!.groupValues[1]).toByteArray()
            else -> error("unexpected url $url")
        }
    }

    private var version = "0.1.0"

    private fun installer(entry: VersionEntry = entry(), dl: Downloader = downloader, jar: (String) -> ByteArray = { relictJar }) =
        Installer(Manifest(version, listOf(entry)), dl, jar, Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC))

    private fun setUpMinecraft(extraProfiles: Boolean = true) {
        Files.createDirectories(mc)
        val profiles = if (extraProfiles) """"other":{"name":"My modpack","lastVersionId":"1.20.1"}""" else ""
        mc.resolve("launcher_profiles.json").writeText("""{"profiles":{$profiles},"settings":{"keep":true},"version":3}""")
    }

    private fun profiles() = JsonParser.parseString(mc.resolve("launcher_profiles.json").readText()).asJsonObject

    @AfterTest
    fun cleanUp() {
        tmp.toFile().deleteRecursively()
    }

    @Test
    fun installsIntoItsOwnInstance() {
        setUpMinecraft()
        installer().install(entry(), mc, Layout.SEPARATE_INSTANCE)

        val gameDir = mc.resolve("relict-instances/1.21.11")
        assertTrue(gameDir.resolve("mods/fabric-api-0.141.6+1.21.11.jar").exists())
        assertTrue(gameDir.resolve("mods/fabric-language-kotlin-1.14.2.jar").exists())
        assertEquals("relict jar", gameDir.resolve("mods/relict-mc1.21.11-0.1.0.jar").readText())
        assertTrue(mc.resolve("versions/fabric-loader-0.20.1-1.21.11/fabric-loader-0.20.1-1.21.11.json").exists())

        val profile = profiles().getAsJsonObject("profiles").getAsJsonObject("relict-1.21.11")
        assertEquals("fabric-loader-0.20.1-1.21.11", profile["lastVersionId"].asString)
        assertEquals("Relict 1.21.11", profile["name"].asString)
        assertEquals(gameDir.toString(), profile["gameDir"].asString)
        assertEquals("2026-01-02T03:04:05.000Z", profile["created"].asString)
    }

    @Test
    fun keepsTheLaunchersOtherProfilesAndSettings() {
        setUpMinecraft()
        installer().install(entry(), mc, Layout.SEPARATE_INSTANCE)
        val root = profiles()
        assertEquals("My modpack", root.getAsJsonObject("profiles").getAsJsonObject("other")["name"].asString)
        assertTrue(root.getAsJsonObject("settings")["keep"].asBoolean)
        assertEquals(3, root["version"].asInt)
        assertTrue(mc.resolve("launcher_profiles.json.relict-backup").exists())
    }

    @Test
    fun mainFolderLayoutUsesTheDefaultGameDir() {
        setUpMinecraft()
        installer().install(entry(), mc, Layout.MAIN_FOLDER)
        assertTrue(mc.resolve("mods/relict-mc1.21.11-0.1.0.jar").exists())
        assertFalse(profiles().getAsJsonObject("profiles").getAsJsonObject("relict-1.21.11").has("gameDir"))
    }

    @Test
    fun aTamperedDownloadInstallsNothing() {
        setUpMinecraft()
        val bad = entry(fabricApiHash = sha512("something else".toByteArray()))
        val e = assertFailsWith<InstallException> { installer(bad).install(bad, mc, Layout.SEPARATE_INSTANCE) }
        assertTrue("integrity" in e.message!!)
        assertFalse(mc.resolve("relict-instances").exists())
        assertFalse(mc.resolve("versions").exists())
        assertFalse(profiles().getAsJsonObject("profiles").has("relict-1.21.11"))
    }

    @Test
    fun refusesToDownloadFromOtherHosts() {
        setUpMinecraft()
        val evil = entry().let { it.copy(mods = it.mods.map { m -> m.copy(url = "https://evil.example.com/x.jar") }) }
        val e = assertFailsWith<InstallException> { installer(evil).install(evil, mc, Layout.SEPARATE_INSTANCE) }
        assertTrue("Refusing" in e.message!!)
        assertTrue(requested.none { "evil" in it })
    }

    @Test
    fun rejectsAFabricProfileForTheWrongVersion() {
        setUpMinecraft()
        val wrong = Downloader { url -> if ("meta.fabricmc.net" in url) fabricProfile("1.20.1").toByteArray() else downloader.get(url) }
        assertFailsWith<InstallException> { installer(dl = wrong).install(entry(), mc, Layout.SEPARATE_INSTANCE) }
        assertFalse(mc.resolve("versions").exists())
    }

    @Test
    fun explainsWhenTheLauncherHasNeverRun() {
        Files.createDirectories(mc)
        val e = assertFailsWith<InstallException> { installer().install(entry(), mc, Layout.SEPARATE_INSTANCE) }
        assertTrue("Launcher once" in e.message!!)
    }

    @Test
    fun networkFailuresBecomeReadableErrors() {
        setUpMinecraft()
        val offline = Downloader { throw java.io.IOException("Connection refused") }
        val e = assertFailsWith<InstallException> { installer(dl = offline).install(entry(), mc, Layout.SEPARATE_INSTANCE) }
        assertTrue("internet" in e.message!!)
    }

    @Test
    fun installingTwiceDoesNotDuplicateAnything() {
        setUpMinecraft()
        val i = installer()
        i.install(entry(), mc, Layout.SEPARATE_INSTANCE)
        i.install(entry(), mc, Layout.SEPARATE_INSTANCE)
        val mods = Files.list(mc.resolve("relict-instances/1.21.11/mods")).use { it.map { p -> p.fileName.toString() }.toList() }
        assertEquals(3, mods.size, mods.toString())
        assertEquals(1, profiles().getAsJsonObject("profiles").keySet().count { it.startsWith("relict-") })
    }

    @Test
    fun statusTracksInstallAndUpdates() {
        setUpMinecraft()
        assertFalse(installer().status(entry(), mc).installed)

        installer().install(entry(), mc, Layout.SEPARATE_INSTANCE)
        assertEquals(Status("0.1.0", needsUpdate = false), installer().status(entry(), mc))

        version = "0.2.0" // a newer installer is run
        assertEquals(Status("0.1.0", needsUpdate = true), installer().status(entry(), mc))
    }

    @Test
    fun updateReplacesTheOldRelictJar() {
        setUpMinecraft()
        installer().install(entry(), mc, Layout.SEPARATE_INSTANCE)

        version = "0.2.0"
        val newer = entry().copy(relictJar = "relict-mc1.21.11-0.2.0.jar")
        installer(newer).install(newer, mc, Layout.SEPARATE_INSTANCE)

        val mods = Files.list(mc.resolve("relict-instances/1.21.11/mods")).use { it.map { p -> p.fileName.toString() }.toList() }
        assertTrue("relict-mc1.21.11-0.2.0.jar" in mods)
        assertFalse("relict-mc1.21.11-0.1.0.jar" in mods)
        assertEquals("0.2.0", installer(newer).status(newer, mc).installedVersion)
    }

    @Test
    fun aMissingFileMeansRepairIsNeeded() {
        setUpMinecraft()
        installer().install(entry(), mc, Layout.SEPARATE_INSTANCE)
        Files.delete(mc.resolve("relict-instances/1.21.11/mods/fabric-api-0.141.6+1.21.11.jar"))
        assertTrue(installer().status(entry(), mc).needsUpdate)
    }

    @Test
    fun olderCopiesOfDependenciesAreSetAsideAndRestoredOnUninstall() {
        setUpMinecraft()
        val mods = mc.resolve("mods").also { Files.createDirectories(it) }
        mods.resolve("fabric-api-0.100.0+1.21.jar").writeText("old api")
        mods.resolve("sodium-fabric-0.6.jar").writeText("users own mod")

        installer().install(entry(), mc, Layout.MAIN_FOLDER)
        assertFalse(mods.resolve("fabric-api-0.100.0+1.21.jar").exists(), "the old Fabric API would clash with the new one")
        assertTrue(mods.resolve(".relict-replaced/fabric-api-0.100.0+1.21.jar").exists())
        assertTrue(mods.resolve("sodium-fabric-0.6.jar").exists(), "unrelated mods are never touched")

        installer().uninstall(entry(), mc)
        assertEquals("old api", mods.resolve("fabric-api-0.100.0+1.21.jar").readText())
        assertTrue(mods.resolve("sodium-fabric-0.6.jar").exists())
        assertFalse(mods.resolve("relict-mc1.21.11-0.1.0.jar").exists())
        assertFalse(mods.resolve(".relict-replaced").exists())
    }

    @Test
    fun uninstallRemovesOnlyWhatWasInstalled() {
        setUpMinecraft()
        installer().install(entry(), mc, Layout.SEPARATE_INSTANCE)
        val gameDir = mc.resolve("relict-instances/1.21.11")
        gameDir.resolve("options.txt").writeText("players own settings")

        installer().uninstall(entry(), mc)

        assertFalse(gameDir.resolve("mods/relict-mc1.21.11-0.1.0.jar").exists())
        assertTrue(gameDir.resolve("options.txt").exists(), "the player's own files in the instance survive")
        assertFalse(profiles().getAsJsonObject("profiles").has("relict-1.21.11"))
        assertTrue(profiles().getAsJsonObject("profiles").has("other"))
        assertFalse(mc.resolve("versions/fabric-loader-0.20.1-1.21.11").exists())
        assertFalse(installer().status(entry(), mc).installed)
    }

    @Test
    fun uninstallLeavesNothingBehindWhenTheInstanceWasOnlyRelicts() {
        setUpMinecraft()
        installer().install(entry(), mc, Layout.SEPARATE_INSTANCE)
        installer().uninstall(entry(), mc)
        assertFalse(mc.resolve("relict-instances/1.21.11").exists())
    }

    @Test
    fun uninstallingSomethingNotInstalledIsAnError() {
        setUpMinecraft()
        assertFailsWith<InstallException> { installer().uninstall(entry(), mc) }
    }

    @Test
    fun switchingLayoutCleansUpThePreviousInstall() {
        setUpMinecraft()
        installer().install(entry(), mc, Layout.SEPARATE_INSTANCE)
        installer().install(entry(), mc, Layout.MAIN_FOLDER)
        assertFalse(mc.resolve("relict-instances/1.21.11").exists())
        assertTrue(mc.resolve("mods/relict-mc1.21.11-0.1.0.jar").exists())
    }

    @Test
    fun aCorruptLauncherProfileFileIsLeftAlone() {
        Files.createDirectories(mc)
        mc.resolve("launcher_profiles.json").writeText("{ not json")
        assertFailsWith<InstallException> { installer().install(entry(), mc, Layout.SEPARATE_INSTANCE) }
        assertEquals("{ not json", mc.resolve("launcher_profiles.json").readText())
    }
}
