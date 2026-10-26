package relict.installer

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Fetches a URL into memory. An interface so tests can serve files without a network. */
fun interface Downloader {
    @Throws(IOException::class)
    fun get(url: String): ByteArray
}

class HttpDownloader : Downloader {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    override fun get(url: String): ByteArray {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(120))
            .header("User-Agent", "RelictInstaller/1.0")
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Download was interrupted", e)
        }
        if (response.statusCode() != 200) throw IOException("Download failed (HTTP ${response.statusCode()}): $url")
        return response.body()
    }
}
