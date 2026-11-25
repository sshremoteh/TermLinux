package com.termlinux.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class DownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long
)

class ResumableDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    suspend fun download(
        url: String,
        destFile: File,
        onProgress: (DownloadProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) {
            "Only HTTPS URLs are permitted for rootfs downloads: $url"
        }
        destFile.parentFile?.mkdirs()

        val existingBytes = if (destFile.exists()) destFile.length() else 0L
        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val call = client.newCall(requestBuilder.build())
        call.execute().use { response ->
            if (response.code == 416 && existingBytes > 0L) {
                // Range not satisfiable: file may already be complete or server changed; restart clean
                destFile.delete()
                throw IOException("HTTP 416 Range Not Satisfiable; partial file cleared for retry")
            }

            if (!response.isSuccessful && response.code != 206) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty HTTP response body")
            val isResumed = response.code == 206 && existingBytes > 0L
            val appendOffset = if (isResumed) existingBytes else 0L
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) appendOffset + contentLength else -1L

            if (!isResumed && destFile.exists()) {
                destFile.delete()
            }

            var totalWritten = appendOffset
            onProgress(DownloadProgress(totalWritten, totalBytes))

            body.byteStream().use { input ->
                FileOutputStream(destFile, isResumed).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        totalWritten += read
                        onProgress(DownloadProgress(totalWritten, totalBytes))
                    }
                    output.fd.sync()
                }
            }
        }
    }
}

object Sha256Verifier {
    suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buf)
                if (read == -1) break
                digest.update(buf, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun verify(file: File, expectedHex: String): Boolean {
        if (!file.exists() || !file.isFile) return false
        val actual = computeSha256(file)
        return actual.equals(expectedHex.trim(), ignoreCase = true)
    }
}
