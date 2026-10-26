package relict.installer

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LaunchersTest {
    private val home: Path = createTempDirectory("relict-launchers")
    private val support: Path = home.resolve("Library/Application Support")
    private val env = LauncherEnv("Mac OS X", home, null, null)

    @AfterTest
    fun cleanUp() {
        home.toFile().deleteRecursively()
    }

    private fun prismInstance(dir: Path, folder: String, name: String, mc: String, loader: String?, loaderVersion: String = "0.20.1", gameFolder: String = "minecraft") {
        val inst = dir.resolve(folder).also { it.createDirectories() }
        inst.resolve("instance.cfg").writeText("InstanceType=OneSix\nname=$name\n")
        inst.resolve(gameFolder).createDirectories()
        val loaderComponent = when (loader) {
            "fabric" -> """,{"uid":"net.fabricmc.fabric-loader","version":"$loaderVersion"}"""
            "forge" -> """,{"uid":"net.minecraftforge","version":"47.4.10"}"""
            else -> ""
        }
        inst.resolve("mmc-pack.json").writeText("""{"formatVersion":1,"components":[{"uid":"org.lwjgl3","version":"3.3.3"},{"uid":"net.minecraft","version":"$mc"}$loaderComponent]}""")
    }

    @Test
    fun findsTheOfficialLauncher() {
        support.resolve("minecraft").createDirectories()
        support.resolve("minecraft/launcher_profiles.json").writeText("{}")
        val t = Launchers.detect(env).single()
        assertEquals(LauncherKind.VANILLA, t.kind)
        assertEquals(support.resolve("minecraft"), t.dir)
        assertNull(t.minecraft) // any version can be chosen
    }

    @Test
    fun readsThePrismLauncherInstances() {
        val prism = support.resolve("PrismLauncher").also { it.createDirectories() }
        prism.resolve("prismlauncher.cfg").writeText("[General]\nInstanceDir=instances\n")
        val instances = prism.resolve("instances")
        prismInstance(instances, "1.21.10", "My 1.21.10", "1.21.10", "fabric")
        prismInstance(instances, "forge", "Forge pack", "1.20.1", "forge")
        prismInstance(instances, "old", "Old", "1.21.11", "fabric", gameFolder = ".minecraft")
        instances.resolve("instgroups.json").writeText("{}") // a file, not an instance
        instances.resolve("empty").createDirectories() // a folder that isn't an instance

        val found = Launchers.detect(env).filter { it.kind == LauncherKind.PRISM }
        assertEquals(listOf("My 1.21.10", "Forge pack", "Old").sorted(), found.map { it.name }.sorted())
        val fabric = found.first { it.name == "My 1.21.10" }
        assertEquals("1.21.10", fabric.minecraft)
        assertEquals("fabric", fabric.loader)
        assertEquals("0.20.1", fabric.loaderVersion)
        assertEquals(instances.resolve("1.21.10/minecraft"), fabric.dir)
        assertEquals("forge", found.first { it.name == "Forge pack" }.loader)
        assertEquals(instances.resolve("old/.minecraft"), found.first { it.name == "Old" }.dir)
    }

    @Test
    fun followsAMovedInstancesFolder() {
        val prism = support.resolve("PrismLauncher").also { it.createDirectories() }
        val elsewhere = home.resolve("games/instances")
        prism.resolve("prismlauncher.cfg").writeText("InstanceDir=${elsewhere}\n")
        prismInstance(elsewhere, "a", "A", "26.1.2", "fabric")
        assertEquals(listOf("A"), Launchers.detect(env).filter { it.kind == LauncherKind.PRISM }.map { it.name })
    }

    @Test
    fun readsModrinthProfilesFromItsDatabase() {
        val data = support.resolve("com.modrinth.theseus").also { it.createDirectories() }
        data.resolve("profiles/Cool Pack").createDirectories()
        data.resolve("profiles/Other").createDirectories()
        DriverManager.getConnection("jdbc:sqlite:${data.resolve("app.db")}").use { c ->
            c.createStatement().use {
                it.execute("CREATE TABLE profiles (path TEXT PRIMARY KEY, name TEXT, game_version TEXT, mod_loader TEXT, mod_loader_version TEXT)")
                it.execute("INSERT INTO profiles VALUES ('Cool Pack', 'Cool Pack', '26.1', 'fabric', '0.20.1')")
                it.execute("INSERT INTO profiles VALUES ('Other', 'Other', '1.20.1', 'Forge', '47.4.10')")
            }
        }
        val found = Launchers.detect(env).filter { it.kind == LauncherKind.MODRINTH }.associateBy { it.name }
        assertEquals("26.1", found.getValue("Cool Pack").minecraft)
        assertEquals("fabric", found.getValue("Cool Pack").loader)
        assertEquals(data.resolve("profiles/Cool Pack"), found.getValue("Cool Pack").dir)
        assertEquals("forge", found.getValue("Other").loader) // lower-cased
    }

    @Test
    fun stillListsModrinthProfilesWhenTheDatabaseIsUnreadable() {
        val data = support.resolve("ModrinthApp").also { it.createDirectories() }
        data.resolve("profiles/Pack").createDirectories()
        data.resolve("app.db").writeText("this is not a database")
        val t = Launchers.detect(env).single { it.kind == LauncherKind.MODRINTH }
        assertEquals("Pack", t.name)
        assertNull(t.minecraft)
        assertNull(t.loader)
    }

    @Test
    fun findsNothingOnAnEmptyComputer() {
        assertTrue(Launchers.detect(env).isEmpty())
    }

    @Test
    fun usesTheRightFoldersPerOperatingSystem() {
        val appData = home.resolve("AppData/Roaming")
        appData.resolve(".minecraft").createDirectories()
        appData.resolve("PrismLauncher/instances").createDirectories()
        prismInstance(appData.resolve("PrismLauncher/instances"), "x", "X", "1.21.11", "fabric")
        val windows = LauncherEnv("Windows 11", home, appData, null)
        assertEquals(setOf(LauncherKind.VANILLA, LauncherKind.PRISM), Launchers.detect(windows).map { it.kind }.toSet())

        val linuxShare = home.resolve(".local/share")
        linuxShare.resolve("PrismLauncher/instances").createDirectories()
        prismInstance(linuxShare.resolve("PrismLauncher/instances"), "y", "Y", "1.21.11", "fabric")
        assertEquals(listOf("Y"), Launchers.detect(LauncherEnv("Linux", home, null, null)).filter { it.kind == LauncherKind.PRISM }.map { it.name })
    }

    @Test
    fun remembersTheLastPlayedVersion() {
        val mc = support.resolve("minecraft").also { it.createDirectories() }
        mc.resolve("launcher_profiles.json").writeText(
            """{"profiles":{"a":{"lastUsed":"2026-01-01T00:00:00.000Z","lastVersionId":"1.21.4"},
                "b":{"lastUsed":"2026-03-01T00:00:00.000Z","lastVersionId":"fabric-loader-0.20.1-1.21.11"},
                "c":{"lastUsed":"2026-05-01T00:00:00.000Z","lastVersionId":"latest-release"}}}"""
        )
        assertEquals("1.21.11", Launchers.lastPlayedVersion(mc))
        assertNull(Launchers.lastPlayedVersion(support.resolve("nowhere")))
    }

    @Test
    fun saysWhatIsWrongWithAnInstance() {
        fun target(loader: String?, version: String? = "0.20.1") = Target(LauncherKind.PRISM, "x", "1.21.11", loader, version, home)
        assertNull(target("fabric").fabricProblem())
        assertNull(target(null).fabricProblem()) // unknown: no complaint
        assertNotNull(target("forge").fabricProblem())
        assertTrue("older" in target("fabric", "0.16.0").fabricProblem()!!)
        assertNull(Target(LauncherKind.VANILLA, "v", null, null, null, home).fabricProblem())
    }

    @Test
    fun matchesMinecraftVersionsToBuilds() {
        fun entry(mc: String) = VersionEntry(mc, "0.20.1", "relict-mc$mc-0.1.0.jar", emptyList())
        val manifest = Manifest("0.1.0", listOf(entry("1.21.11"), entry("26.1.2"), entry("26.2")))
        assertEquals("1.21.11", manifest.entryFor("1.21.11")?.minecraft)
        assertEquals("26.1.2", manifest.entryFor("26.1")?.minecraft)
        assertEquals("26.1.2", manifest.entryFor("26.1.1")?.minecraft)
        assertEquals("26.2", manifest.entryFor("26.2")?.minecraft)
        assertNull(manifest.entryFor("1.21.1"))
        assertNull(manifest.entryFor("26.3"))
        assertNull(manifest.entryFor("1.20.1"))
    }
}
