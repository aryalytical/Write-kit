package com.offlinecode.editor.runtime

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads a package archive over HTTPS with redirect and size limits. */
object Downloader {
    fun download(
        urlStr: String,
        dest: File,
        onProgress: (done: Long, total: Long) -> Unit,
        maxBytes: Long = 1024L * 1024 * 1024
    ) {
        require(maxBytes > 0) { "Invalid download size limit" }
        var current = URL(urlStr)
        require(current.protocol.equals("https", ignoreCase = true) && current.userInfo == null) {
            "Only HTTPS URLs without embedded credentials are allowed"
        }
        var redirects = 0
        var connection: HttpURLConnection? = null
        val parent = dest.absoluteFile.parentFile ?: throw IOException("Invalid download destination")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create download directory")
        val temp = File(parent, ".${dest.name}.download-${System.currentTimeMillis()}")
        try {
            while (true) {
                val conn = current.openConnection() as HttpURLConnection
                connection = conn
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "OfflineCodeEditor/0.1")
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw IOException("Redirect has no Location header")
                    if (++redirects > 5) throw IOException("Too many redirects")
                    val next = URL(current, location)
                    if (!next.protocol.equals("https", ignoreCase = true) || next.userInfo != null) {
                        throw IOException("Redirects must remain on HTTPS without embedded credentials")
                    }
                    conn.disconnect()
                    connection = null
                    current = next
                    continue
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                val total = conn.contentLengthLong
                if (total > maxBytes) throw IOException("Download exceeds size limit (${maxBytes} bytes)")
                conn.inputStream.use { input ->
                    temp.outputStream().buffered().use { out ->
                        val buf = ByteArray(65536)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            done += n
                            if (done > maxBytes) throw IOException("Download exceeds size limit (${maxBytes} bytes)")
                            out.write(buf, 0, n)
                            onProgress(done, total)
                        }
                    }
                }
                if (dest.exists() && !dest.delete()) throw IOException("Cannot replace previous download")
                if (!temp.renameTo(dest)) throw IOException("Cannot finalize downloaded archive")
                return
            }
        } finally {
            connection?.disconnect()
            temp.delete()
        }
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i ->
            val buf = ByteArray(65536)
            while (true) {
                val n = i.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
