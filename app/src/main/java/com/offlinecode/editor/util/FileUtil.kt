package com.offlinecode.editor.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtil {
    fun writeAtomic(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, ".${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    fun isBinary(bytes: ByteArray): Boolean {
        val n = minOf(bytes.size, 8000)
        for (i in 0 until n) if (bytes[i].toInt() == 0) return true
        return false
    }

    /** Returns an error message, or null if [name] is a valid single path segment. */
    fun validateName(name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Name cannot be empty"
            n == "." || n == ".." -> "Invalid name"
            n.contains('/') || n.contains('\\') || n.contains('\u0000') -> "Name cannot contain slashes"
            n.length > 120 -> "Name is too long"
            else -> null
        }
    }

    fun uniqueChild(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (f.exists()) {
            f = File(dir, "$base ($i)$ext")
            i++
        }
        return f
    }

    fun relativePath(root: File, file: File): String =
        file.relativeTo(root).path.replace(File.separatorChar, '/')

    fun sizeOf(file: File): Long =
        if (file.isFile) file.length() else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun humanSize(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes / gb)
            bytes >= mb -> String.format("%.1f MB", bytes / mb)
            bytes >= kb -> String.format("%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun displayName(ctx: Context, uri: Uri): String {
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    fun zipDirectory(dir: File, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zos ->
            dir.walkTopDown().forEach { f ->
                if (f == dir) return@forEach
                val rel = relativePath(dir, f)
                if (f.isDirectory) {
                    zos.putNextEntry(ZipEntry("$rel/"))
                    zos.closeEntry()
                } else {
                    zos.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    /**
     * Extracts a ZIP while preventing path traversal and archive resource-exhaustion attacks.
     * Defaults accommodate large compiler packages while bounding untrusted archives.
     */
    fun unzip(
        input: InputStream,
        destDir: File,
        maxTotalBytes: Long = 1024L * 1024 * 1024,
        maxEntryBytes: Long = 512L * 1024 * 1024,
        maxEntries: Int = 50_000,
        onBytes: ((Long) -> Unit)? = null
    ) {
        require(maxTotalBytes > 0 && maxEntryBytes > 0 && maxEntries > 0) { "Invalid ZIP extraction limits" }
        check(destDir.exists() || destDir.mkdirs()) { "Cannot create extraction directory" }
        val canonicalRoot = destDir.canonicalFile
        val base = canonicalRoot.path + File.separator
        ZipInputStream(input.buffered()).use { zis ->
            var total = 0L
            var entries = 0
            var e = zis.nextEntry
            while (e != null) {
                val entry = e
                entries++
                if (entries > maxEntries) throw IOException("Archive contains too many entries (limit $maxEntries)")
                if (entry.name.isBlank() || entry.name.contains('\u0000')) throw SecurityException("Invalid archive entry name")
                val target = File(canonicalRoot, entry.name).canonicalFile
                val check = if (entry.isDirectory) target.path + File.separator else target.path
                if (!check.startsWith(base)) throw SecurityException("Blocked unsafe path in archive: ${entry.name}")
                if (entry.isDirectory) {
                    if (!target.isDirectory && !target.mkdirs()) throw IOException("Cannot create directory for archive entry: ${entry.name}")
                } else {
                    target.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("Cannot create parent directory for archive entry: ${entry.name}") }
                    var entryBytes = 0L
                    target.outputStream().buffered().use { o ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val n = zis.read(buf)
                            if (n < 0) break
                            entryBytes += n
                            total += n
                            if (entryBytes > maxEntryBytes) throw IOException("Archive entry exceeds size limit: ${entry.name}")
                            if (total > maxTotalBytes) throw IOException("Archive exceeds total extracted size limit")
                            o.write(buf, 0, n)
                            onBytes?.invoke(total)
                        }
                    }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
    }
}
