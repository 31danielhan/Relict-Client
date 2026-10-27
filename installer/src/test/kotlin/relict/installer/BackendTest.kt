package relict.installer

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendTest {
    private val tmp: Path = createTempDirectory("relict-backend-test")
    private val game: Path = tmp.resolve("instances/1.21.10/minecraft")
    private val events = mutableListOf<Pair<String, JsonObject>>()

    private fun sha512(bytes: ByteArray) = MessageDigest.getInstance("SHA-512").digest(bytes).joinToString("") { "%02x".format(it) }
    private val fabricApi = "fabric-api".toByteArray()
    private val entry = VersionEntry(
        "1.21.10", "0.20.1", "relict-mc1.21.10-0.1.0.jar",
        listOf(ModFile("fabric-api", "fabric-api-0.2.jar", "https://cdn.modrinth.com/data/a/fabric-api.jar", sha512(fabricApi), fabricApi.size.toLong())),
    )
    private val prism = Target(LauncherKind.PRISM, "1.21.10", "1.21.10", "fabric", "0.20.1", game)
    private val oldForge = Target(LauncherKind.PRISM, "Old Forge", "1.20.1", "forge", "47.2", tmp.resolve("instances/forge/minecraft"))
    private val noFabric = Target(LauncherKind.PRISM, "No Fabric", "1.21.10", "neoforge", "21.0", tmp.resolve("instances/neo/minecraft"))

    private fun backend(feed: String? = null, targets: List<Target> = listOf(prism)) = InstallerBackend(
        Manifest("0.1.0", listOf(entry)),
        settingsFile = tmp.resolve("settings.json"),
        feedUrl = feed,
        detected = targets,
        downloader = { url -> if (url.endsWith("fabric-api.jar")) fabricApi else throw IOException("404 $url") },
        bundledJar = { "relict".toByteArray() },
        emit = { name, payload: JsonElement -> events += name to payload.asJsonObject },
    )

    private fun InstallerBackend.call(method: String, vararg params: Pair<String, Any>): JsonObject =
        handle(method, JsonObject().also { o -> params.forEach { (k, v) -> if (v is Boolean) o.addProperty(k, v) else o.addProperty(k, v.toString()) } }).asJsonObject

    @AfterTest
    fun cleanUp() {
        tmp.toFile().deleteRecursively()
    }

    @Test
    fun listsTheInstancesWithTheirState() {
        game.createDirectories()
        val snap = backend().call("init")
        assertEquals("1.21.10", snap["versions"].asJsonArray.single().asString)
        val t = snap["targets"].asJsonArray.single().asJsonObject
        assertEquals("Prism Launcher", t["launcher"].asString)
        assertEquals("Minecraft 1.21.10 · Fabric 0.20.1", t["detail"].asString)
        assertEquals("none", t["state"].asString)
        assertEquals(t["key"].asString, snap["selected"].asString)
    }

    @Test
    fun installsAndUninstallsThroughTheSameCallsThePageMakes() {
        game.createDirectories()
        val b = backend()
        val key = b.call("init")["selected"].asString

        val before = b.call("status", "target" to key)
        assertFalse(before["installed"].asBoolean)
        assertFalse(before["blocked"].asBoolean)
        assertFalse(before["pickVersion"].asBoolean)
        assertFalse(before["pickLayout"].asBoolean)

        val after = b.call("install", "target" to key)
        assertTrue(after["installed"].asBoolean)
        assertEquals("0.1.0", after["installedVersion"].asString)
        assertTrue(game.resolve("mods/relict-mc1.21.10-0.1.0.jar").exists())
        // The page was told it was busy, then free, and saw the progress lines.
        assertEquals(listOf(true, false), events.filter { it.first == "busy" }.map { it.second["value"].asBoolean })
        assertTrue(events.any { it.first == "log" })
        assertEquals("installed", b.call("init")["targets"].asJsonArray.single().asJsonObject["state"].asString)

        assertFalse(b.call("uninstall", "target" to key)["installed"].asBoolean)
        assertFalse(game.resolve("mods/relict-mc1.21.10-0.1.0.jar").exists())
    }

    @Test
    fun explainsWhyAPlaceCannotBeUsed() {
        val b = backend(targets = listOf(prism, oldForge, noFabric))
        val states = b.call("init")["targets"].asJsonArray.map { it.asJsonObject }.associate { it["name"].asString to it["state"].asString }
        assertEquals("unsupported", states["Old Forge"])

        val forge = b.call("status", "target" to "${oldForge.kind.name}|${oldForge.dir}|${oldForge.name}")
        assertTrue(forge["blocked"].asBoolean)
        assertTrue(forge["note"].asString.startsWith("Relict doesn't support Minecraft 1.20.1"))

        val neo = b.call("status", "target" to "${noFabric.kind.name}|${noFabric.dir}|${noFabric.name}")
        assertTrue(neo["problem"].asBoolean)
        assertTrue("doesn't use Fabric" in neo["note"].asString)
    }

    @Test
    fun rejectsAPlaceThatIsNotInTheList() {
        val e = assertFailsWith<IllegalArgumentException> { backend().call("status", "target" to "PRISM|nowhere|x") }
        assertTrue("Rescan" in e.message!!)
    }

    @Test
    fun aFailedInstallIsReportedAndLeavesTheBackendUsable() {
        game.createDirectories()
        val failing = InstallerBackend(
            Manifest("0.1.0", listOf(entry)), settingsFile = tmp.resolve("s.json"), feedUrl = null, detected = listOf(prism),
            downloader = { throw IOException("offline") }, bundledJar = { "relict".toByteArray() },
            emit = { name, payload -> events += name to payload.asJsonObject },
        )
        val key = failing.call("init")["selected"].asString
        assertFailsWith<InstallException> { failing.call("install", "target" to key) }
        assertTrue(events.any { it.first == "log" && it.second["text"].asString.startsWith("Failed:") })
        assertEquals(false, events.last { it.first == "busy" }.second["value"].asBoolean)
        assertFalse(failing.call("status", "target" to key)["installed"].asBoolean)
    }

    @Test
    fun remembersTheAutoUpdateChoice() {
        assertTrue(backend().call("init")["autoUpdate"].asBoolean)
        backend().call("autoUpdate", "value" to false)
        assertFalse(backend().call("init")["autoUpdate"].asBoolean)
    }

    @Test
    fun saysSoWhenUpdatesAreNotSetUp() {
        val b = backend(feed = null)
        assertFalse(b.call("init")["updatesConfigured"].asBoolean)
        b.call("updates.check")
        val banner = events.last { it.first == "banner" }.second
        assertEquals("Updates aren't set up", banner["title"].asString)
    }

    @Test
    fun respondWrapsResultsAndErrorsAsJson() {
        val b = backend()
        assertTrue(b.respond("init", JsonObject()).startsWith("{\"ok\":"))
        assertTrue(b.respond("nope", JsonObject()).contains("\"error\":\"Unknown method 'nope'\""))
    }
}
