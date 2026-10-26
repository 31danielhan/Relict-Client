package relict.installer

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class MinecraftDirTest {
    @Test
    fun windowsUsesAppData() {
        assertEquals(Path.of("C:\\Users\\a\\AppData\\Roaming", ".minecraft"), MinecraftDir.default("Windows 11", "C:\\Users\\a", "C:\\Users\\a\\AppData\\Roaming"))
    }

    @Test
    fun macUsesApplicationSupport() {
        assertEquals(Path.of("/Users/a/Library/Application Support/minecraft"), MinecraftDir.default("Mac OS X", "/Users/a", null))
    }

    @Test
    fun linuxUsesTheDotFolder() {
        assertEquals(Path.of("/home/a/.minecraft"), MinecraftDir.default("Linux", "/home/a", null))
    }
}
