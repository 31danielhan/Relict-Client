package relict.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object Json {
    val compact: Gson = Gson()
    val pretty: Gson = GsonBuilder().setPrettyPrinting().create()

    /** Writes via a temp file and a rename so a crash mid-write never leaves a half-written file. */
    fun writeAtomic(path: Path, bytes: ByteArray) {
        Files.createDirectories(path.parent)
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.write(tmp, bytes)
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
