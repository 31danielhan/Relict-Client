package relict.installer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManifestTest {
    private val hash = "a".repeat(128)

    private fun json(file: String = "fabric-api-1.0.jar", url: String = "https://cdn.modrinth.com/data/x/y.jar", mc: String = "1.21.11", sha: String = hash) = """
        {"relictVersion":"0.1.0","versions":[{"minecraft":"$mc","fabricLoader":"0.20.1","relictJar":"relict-mc$mc-0.1.0.jar",
        "mods":[{"id":"fabric-api","file":"$file","url":"$url","sha512":"$sha","size":10}]}]}
    """.trimIndent()

    @Test
    fun parsesAValidManifest() {
        val m = Manifest.parse(json())
        assertEquals("0.1.0", m.relictVersion)
        assertEquals("fabric-loader-0.20.1-1.21.11", m.versions[0].fabricVersionId)
    }

    @Test
    fun rejectsPathTraversalInFileNames() {
        assertFailsWith<IllegalArgumentException> { Manifest.parse(json(file = "../../evil.jar")) }
        assertFailsWith<IllegalArgumentException> { Manifest.parse(json(file = "a/b.jar")) }
        assertFailsWith<IllegalArgumentException> { Manifest.parse(json(file = "a\\b.jar")) }
        assertFailsWith<IllegalArgumentException> { Manifest.parse(json(file = "tool.exe")) }
    }

    @Test
    fun rejectsOddVersionsHashesAndHosts() {
        assertFailsWith<IllegalArgumentException> { Manifest.parse(json(mc = "../1.21")) }
        assertFailsWith<IllegalArgumentException> { Manifest.parse(json(sha = "abc")) }
        assertFailsWith<IllegalArgumentException> { Manifest.parse(json(url = "https://evil.example.com/a.jar")) }
        assertFailsWith<IllegalArgumentException> { Manifest.parse(json(url = "http://cdn.modrinth.com/a.jar")) }
    }

    @Test
    fun rejectsGarbage() {
        assertFailsWith<IllegalArgumentException> { Manifest.parse("not json") }
        assertFailsWith<IllegalArgumentException> { Manifest.parse("{}") }
    }

    @Test
    fun onlyHttpsUrlsOnKnownHostsAreAllowed() {
        assertTrue(Manifest.isAllowedUrl("https://meta.fabricmc.net/v2/versions/loader/1.21.11/0.20.1/profile/json"))
        assertTrue(Manifest.isAllowedUrl("https://cdn.modrinth.com/data/a/b.jar"))
        assertFalse(Manifest.isAllowedUrl("https://cdn.modrinth.com.evil.com/a.jar"))
        assertFalse(Manifest.isAllowedUrl("https://evil.com/cdn.modrinth.com/a.jar"))
        assertFalse(Manifest.isAllowedUrl("file:///etc/passwd"))
        assertFalse(Manifest.isAllowedUrl("not a url"))
    }
}
