package relict.installer

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdatesTest {
    private val tmp: Path = createTempDirectory("relict-update-test")
    private val mc: Path = tmp.resolve("minecraft")

    private fun sha512(bytes: ByteArray) = MessageDigest.getInstance("SHA-512").digest(bytes).joinToString("") { "%02x".format(it) }

    private val fabricApi = "fabric-api".toByteArray()
    private val oldJar = "relict 0.1.0".toByteArray()
    private val newJar = "relict 0.2.0".toByteArray()
    private val feedUrl = "https://updates.example.com/update.json"

    private fun mods() = listOf(ModFile("fabric-api", "fabric-api-1.0.jar", "https://cdn.modrinth.com/data/a/fabric-api.jar", sha512(fabricApi), fabricApi.size.toLong()))

    private fun entry(version: String, url: String? = null, hash: String? = null) =
        VersionEntry("1.21.11", "0.20.1", "relict-mc1.21.11-$version.jar", mods(), url, hash)

    private fun feedJson(version: String = "0.2.0", jarUrl: String = "https://updates.example.com/relict-mc1.21.11-$version.jar", hash: String = sha512(newJar)) = """
        {"relictVersion":"$version","notes":"Faster scans","versions":[{"minecraft":"1.21.11","fabricLoader":"0.20.1",
        "relictJar":"relict-mc1.21.11-$version.jar","relictUrl":"$jarUrl","relictSha512":"$hash",
        "mods":[{"id":"fabric-api","file":"fabric-api-1.0.jar","url":"https://cdn.modrinth.com/data/a/fabric-api.jar","sha512":"${sha512(fabricApi)}","size":10}]}]}
    """.trimIndent()

    private val server = mutableMapOf<String, ByteArray>()

    private val downloader = Downloader { url ->
        server[url]
            ?: if ("meta.fabricmc.net" in url) """{"id":"fabric-loader-0.20.1-1.21.11","inheritsFrom":"1.21.11"}""".toByteArray() else throw IOException("404 $url")
    }

    @AfterTest
    fun cleanUp() {
        tmp.toFile().deleteRecursively()
    }

    private fun setUpMinecraft() {
        Files.createDirectories(mc)
        mc.resolve("launcher_profiles.json").writeText("""{"profiles":{},"version":3}""")
        server["https://cdn.modrinth.com/data/a/fabric-api.jar"] = fabricApi
    }

    // ---- versions ---------------------------------------------------------------------------

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(Versions.compare("0.10.0", "0.9.5") > 0)
        assertTrue(Versions.compare("0.1.0", "0.1.1") < 0)
        assertEquals(0, Versions.compare("1.0", "1.0.0"))
        assertTrue(Versions.compare("2.0.0", "1.99.99") > 0)
    }

    // ---- the feed ---------------------------------------------------------------------------

    @Test
    fun findsANewerBuild() {
        server[feedUrl] = feedJson().toByteArray()
        val result = assertIs<UpdateCheck.Available>(UpdateChecker(feedUrl, downloader, "0.1.0").check())
        assertEquals("0.2.0", result.feed.relictVersion)
        assertEquals("Faster scans", result.feed.notes)
        assertEquals(setOf("updates.example.com"), result.hosts)
    }

    @Test
    fun saysUpToDateForTheSameOrAnOlderFeed() {
        server[feedUrl] = feedJson("0.1.0").toByteArray()
        assertIs<UpdateCheck.UpToDate>(UpdateChecker(feedUrl, downloader, "0.1.0").check())
        assertIs<UpdateCheck.UpToDate>(UpdateChecker(feedUrl, downloader, "0.3.0").check())
    }

    @Test
    fun reportsWhenNoFeedIsConfigured() {
        assertEquals(UpdateCheck.NotConfigured, UpdateChecker(null, downloader, "0.1.0").check())
    }

    @Test
    fun failsClearlyWhenTheServerIsDownOrTheFeedIsBroken() {
        assertIs<UpdateCheck.Failed>(UpdateChecker(feedUrl, downloader, "0.1.0").check())
        server[feedUrl] = "not json".toByteArray()
        assertIs<UpdateCheck.Failed>(UpdateChecker(feedUrl, downloader, "0.1.0").check())
    }

    @Test
    fun onlyTrustsAnHttpsFeed() {
        val http = "http://updates.example.com/update.json"
        server[http] = feedJson().toByteArray()
        assertIs<UpdateCheck.Failed>(UpdateChecker(http, downloader, "0.1.0").check())
    }

    @Test
    fun aFeedCannotPointJarsAtOtherHosts() {
        server[feedUrl] = feedJson(jarUrl = "https://evil.example.com/relict.jar").toByteArray()
        assertIs<UpdateCheck.Failed>(UpdateChecker(feedUrl, downloader, "0.1.0").check())
    }

    @Test
    fun aFeedMustSayWhereEachJarIs() {
        val noUrls = feedJson().replace(Regex(""""relictUrl":"[^"]*","relictSha512":"[^"]*","""), "")
        server[feedUrl] = noUrls.toByteArray()
        assertIs<UpdateCheck.Failed>(UpdateChecker(feedUrl, downloader, "0.1.0").check())
    }

    @Test
    fun theBundledManifestStillParsesWithoutJarUrls() {
        assertNull(Manifest.bundled().versions.first().relictUrl)
    }

    // ---- installing an update ---------------------------------------------------------------

    private fun installOld(layout: Layout = Layout.SEPARATE_INSTANCE) {
        setUpMinecraft()
        Installer(Manifest("0.1.0", listOf(entry("0.1.0"))), downloader, { oldJar }).install(entry("0.1.0"), mc, layout)
    }

    private fun feedInstaller(): Pair<Installer, VersionEntry> {
        server[feedUrl] = feedJson().toByteArray()
        server["https://updates.example.com/relict-mc1.21.11-0.2.0.jar"] = newJar
        val feed = assertIs<UpdateCheck.Available>(UpdateChecker(feedUrl, downloader, "0.1.0").check())
        return Installer(feed.feed, downloader, { error("not bundled") }, extraHosts = feed.hosts) to feed.feed.versions.first()
    }

    @Test
    fun updatesTheInstalledJarFromTheFeed() {
        installOld()
        val gameDir = mc.resolve("relict-instances/1.21.11")
        assertEquals("relict 0.1.0", gameDir.resolve("mods/relict-mc1.21.11-0.1.0.jar").readText())

        val (installer, entry) = feedInstaller()
        assertTrue(installer.status(entry, mc).needsUpdate)
        assertTrue(installer.update(entry, mc))

        assertFalse(gameDir.resolve("mods/relict-mc1.21.11-0.1.0.jar").exists())
        assertEquals("relict 0.2.0", gameDir.resolve("mods/relict-mc1.21.11-0.2.0.jar").readText())
        assertFalse(installer.status(entry, mc).needsUpdate)
        assertEquals("0.2.0", installer.status(entry, mc).installedVersion)
    }

    @Test
    fun anUpdateKeepsTheMainFolderLayout() {
        installOld(Layout.MAIN_FOLDER)
        val (installer, entry) = feedInstaller()
        assertTrue(installer.update(entry, mc))
        assertTrue(mc.resolve("mods/relict-mc1.21.11-0.2.0.jar").exists())
        assertFalse(mc.resolve("relict-instances").exists())
    }

    @Test
    fun updateDoesNothingWhereRelictIsNotInstalled() {
        setUpMinecraft()
        val (installer, entry) = feedInstaller()
        assertFalse(installer.update(entry, mc))
        assertFalse(mc.resolve("relict-instances").exists())
    }

    @Test
    fun aTamperedUpdateJarChangesNothing() {
        installOld()
        val (installer, entry) = feedInstaller()
        server["https://updates.example.com/relict-mc1.21.11-0.2.0.jar"] = "tampered".toByteArray()
        val e = assertFailsWith<InstallException> { installer.update(entry, mc) }
        assertTrue("integrity" in e.message!!)
        // The failed download was caught before the working install was touched.
        val gameDir = mc.resolve("relict-instances/1.21.11")
        assertEquals("relict 0.1.0", gameDir.resolve("mods/relict-mc1.21.11-0.1.0.jar").readText())
        assertFalse(gameDir.resolve("mods/relict-mc1.21.11-0.2.0.jar").exists())
        assertEquals("0.1.0", Installer(Manifest("0.1.0", listOf(entry("0.1.0"))), downloader, { oldJar }).status(entry("0.1.0"), mc).installedVersion)
    }


    // ---- the installer app's own update -------------------------------------------------------

    private val installerBytes = "new installer".toByteArray()

    private fun feedWithInstaller(relict: String = "0.1.0", installerVersion: String = "9.0.0") =
        feedJson(relict).dropLast(1) + ""","installer":{"version":"$installerVersion","files":{
            "mac":{"file":"Relict Installer-$installerVersion.dmg","url":"https://updates.example.com/Relict%20Installer-$installerVersion.dmg","sha512":"${sha512(installerBytes)}"},
            "windows":{"file":"Relict-Installer-windows.zip","url":"https://updates.example.com/Relict-Installer-windows.zip","sha512":"${sha512(installerBytes)}"}}}}"""

    @Test
    fun offersANewerInstallerForThisPlatformEvenWhenRelictIsCurrent() {
        server[feedUrl] = feedWithInstaller().toByteArray()
        val mac = assertIs<UpdateCheck.Available>(UpdateChecker(feedUrl, downloader, "0.1.0", installerVersion = "1.2.0", os = "mac").check())
        assertFalse(mac.newRelict)
        assertEquals("Relict Installer-9.0.0.dmg", mac.installerFile?.file)
        val windows = assertIs<UpdateCheck.Available>(UpdateChecker(feedUrl, downloader, "0.1.0", installerVersion = "1.2.0", os = "windows").check())
        assertEquals("Relict-Installer-windows.zip", windows.installerFile?.file)
    }

    @Test
    fun noInstallerOfferWhenItIsCurrentOrHasNoFileForThisPlatform() {
        server[feedUrl] = feedWithInstaller().toByteArray()
        assertIs<UpdateCheck.UpToDate>(UpdateChecker(feedUrl, downloader, "0.1.0", installerVersion = "9.0.0", os = "mac").check())
        assertIs<UpdateCheck.UpToDate>(UpdateChecker(feedUrl, downloader, "0.1.0", installerVersion = "1.2.0", os = "linux").check())
    }

    @Test
    fun reportsBothUpdatesTogether() {
        server[feedUrl] = feedWithInstaller("0.2.0").toByteArray()
        val result = assertIs<UpdateCheck.Available>(UpdateChecker(feedUrl, downloader, "0.1.0", installerVersion = "1.2.0", os = "mac").check())
        assertTrue(result.newRelict)
        assertTrue(result.installerFile != null)
    }

    @Test
    fun aFeedCannotHideAnInstallerOnAnotherHostOrWithAPathInItsName() {
        val evilHost = feedWithInstaller().replace("https://updates.example.com/Relict%20Installer", "https://evil.example.com/Relict%20Installer")
        server[feedUrl] = evilHost.toByteArray()
        assertIs<UpdateCheck.Failed>(UpdateChecker(feedUrl, downloader, "0.1.0", os = "mac").check())
        val traversal = feedWithInstaller().replace("Relict Installer-9.0.0.dmg\"", "../../evil.dmg\"")
        server[feedUrl] = traversal.toByteArray()
        assertIs<UpdateCheck.Failed>(UpdateChecker(feedUrl, downloader, "0.1.0", os = "mac").check())
    }

    @Test
    fun savesAVerifiedInstallerDownload() {
        server[feedUrl] = feedWithInstaller().toByteArray()
        server["https://updates.example.com/Relict%20Installer-9.0.0.dmg"] = installerBytes
        val result = assertIs<UpdateCheck.Available>(UpdateChecker(feedUrl, downloader, "0.1.0", installerVersion = "1.2.0", os = "mac").check())
        val saved = InstallerDownload.download(result.installerFile!!, result.hosts, downloader, tmp.resolve("Downloads"))
        assertEquals("Relict Installer-9.0.0.dmg", saved.fileName.toString())
        assertEquals("new installer", saved.readText())
    }

    @Test
    fun rejectsATamperedInstallerDownload() {
        server[feedUrl] = feedWithInstaller().toByteArray()
        server["https://updates.example.com/Relict%20Installer-9.0.0.dmg"] = "tampered".toByteArray()
        val result = assertIs<UpdateCheck.Available>(UpdateChecker(feedUrl, downloader, "0.1.0", installerVersion = "1.2.0", os = "mac").check())
        assertFailsWith<InstallException> { InstallerDownload.download(result.installerFile!!, result.hosts, downloader, tmp.resolve("Downloads")) }
        assertFalse(tmp.resolve("Downloads").exists())
    }

    @Test
    fun knowsWhichPlatformItIsOn() {
        assertEquals("mac", InstallerInfo.osKey("Mac OS X"))
        assertEquals("windows", InstallerInfo.osKey("Windows 11"))
        assertEquals("linux", InstallerInfo.osKey("Linux"))
        assertEquals("mac", InstallerInfo.platformKey("Mac OS X", "aarch64"))
        assertEquals("mac-intel", InstallerInfo.platformKey("Mac OS X", "x86_64"))
        assertEquals("windows", InstallerInfo.platformKey("Windows 11", "amd64"))
        assertTrue(InstallerInfo.version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
    }

    // ---- settings ---------------------------------------------------------------------------

    @Test
    fun settingsRoundTripAndSurviveADamagedFile() {
        val file = tmp.resolve("settings.json")
        assertEquals(Settings(), Settings.load(file))
        Settings(checkOnLaunch = false, autoUpdate = false).save(file)
        assertEquals(Settings(checkOnLaunch = false, autoUpdate = false), Settings.load(file))
        file.writeText("{ nope")
        assertEquals(Settings(), Settings.load(file))
    }
}
