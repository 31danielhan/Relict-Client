package relict.core

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageTest {
    private val tmp: Path = createTempDirectory("relict-test")

    @AfterTest
    fun cleanUp() {
        tmp.toFile().deleteRecursively()
    }

    @Test
    fun profileKeysAreSafeDirectoryNames() {
        assertEquals("srv-mc.donutsmp.net", ProfileKey.server("MC.DonutSMP.net"))
        assertEquals("srv-host_25565", ProfileKey.server("host:25565"))
        assertEquals("srv-unnamed", ProfileKey.server("   "))
        assertEquals("sp-my_world", ProfileKey.singleplayer("My World"))
        // path traversal can't escape the profiles directory
        val evil = ProfileKey.server("../../etc/passwd")
        assertFalse(evil.contains('/') || evil.contains(".."))
        assertTrue(ProfileKey.server("a".repeat(500)).length <= 68)
    }

    @Test
    fun storageOpensAndClosesProfiles() {
        val storage = Storage(tmp)
        assertNull(storage.current)
        val p = storage.open("srv-test")
        assertTrue(Files.isDirectory(p.dir))
        assertEquals(p, storage.current)
        storage.close()
        assertNull(storage.current)
    }

    @Test
    fun jsonRoundTripAndDefaults() {
        val store = Storage(tmp).open("srv-test")
        assertEquals(listOf("fallback"), store.readJson<List<String>>("list.json") { listOf("fallback") })
        store.writeJson("list.json", listOf("a", "b"))
        assertEquals(listOf("a", "b"), store.readJson<List<String>>("list.json") { emptyList() })
    }

    @Test
    fun corruptDataFallsBackToDefault() {
        val store = Storage(tmp).open("srv-test")
        store.path("list.json").writeText("{not json")
        assertEquals(listOf("fallback"), store.readJson<List<String>>("list.json") { listOf("fallback") })
    }

    @Test
    fun bytesRoundTrip() {
        val store = Storage(tmp).open("srv-test")
        assertNull(store.readBytes("cov.bin"))
        store.writeBytes("cov.bin", byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), store.readBytes("cov.bin"))
    }

    @Test
    fun atomicWriteLeavesNoTempFile() {
        val file = tmp.resolve("a/b/c.json")
        Json.writeAtomic(file, "x".toByteArray())
        assertEquals("x", file.readText())
        assertFalse(Files.exists(file.resolveSibling("c.json.tmp")))
    }

    @Test
    fun configStoreRoundTrips() {
        val store = ConfigStore(tmp.resolve("relict.json"))
        assertTrue(store.load().entrySet().isEmpty())
        store.save(JsonParser.parseString("""{"version":1,"modules":{"x":{"enabled":true}}}""").asJsonObject)
        assertTrue(store.load().getAsJsonObject("modules").getAsJsonObject("x")["enabled"].asBoolean)
    }

    @Test
    fun corruptConfigIsMovedAside() {
        val file = tmp.resolve("relict.json")
        file.writeText("{{{ nope")
        val loaded = ConfigStore(file).load()
        assertTrue(loaded.entrySet().isEmpty())
        assertFalse(Files.exists(file))
        assertEquals("{{{ nope", tmp.resolve("relict.json.bad").readText())
    }
}
