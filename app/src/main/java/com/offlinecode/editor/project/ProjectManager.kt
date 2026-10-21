package com.offlinecode.editor.project

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.offlinecode.editor.util.FileUtil
import java.io.File
import java.security.MessageDigest

/** All projects live in the app's private storage: <filesDir>/projects/<name>. */
class ProjectManager(private val ctx: Context) {
    val root = File(ctx.filesDir, "projects").apply { mkdirs() }

    private fun isWithin(parent: File, child: File): Boolean {
        val p = parent.canonicalFile.path
        val c = child.canonicalFile.path
        return c == p || c.startsWith(p + File.separator)
    }

    fun list(): List<File> =
        (root.listFiles { f -> f.isDirectory } ?: emptyArray()).sortedBy { it.name.lowercase() }

    fun find(name: String): File? {
        if (FileUtil.validateName(name) != null) return null
        return File(root, name.trim()).takeIf { it.isDirectory && isWithin(root, it) }
    }

    fun create(name: String, template: ProjectTemplate?): File {
        FileUtil.validateName(name)?.let { throw IllegalArgumentException(it) }
        val dir = FileUtil.uniqueChild(root, name.trim())
        check(dir.mkdirs()) { "Could not create project folder" }
        try {
            template?.files?.forEach { (path, content) ->
                val f = File(dir, path).canonicalFile
                require(isWithin(dir, f) && f != dir) { "Invalid template path: $path" }
                f.parentFile?.mkdirs()
                f.writeText(content)
            }
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        return dir
    }

    fun rename(dir: File, newName: String): File {
        FileUtil.validateName(newName)?.let { throw IllegalArgumentException(it) }
        require(isWithin(root, dir) && dir.parentFile?.canonicalFile == root.canonicalFile) { "Invalid project directory" }
        val target = File(root, newName.trim())
        require(!target.exists()) { "A project named \"$newName\" already exists" }
        check(dir.renameTo(target)) { "Rename failed" }
        return target
    }

    /** Picks a sensible file to open first. */
    fun defaultFile(dir: File): File? {
        val candidates = listOf(
            "index.html", "src/main.jsx", "main.py", "main.c", "main.cpp", "Main.java", "index.php", "main.js", "index.js"
        )
        for (c in candidates) File(dir, c).takeIf { it.isFile }?.let { return it }
        return dir.walkTopDown().firstOrNull { it.isFile && !it.name.startsWith(".") }
    }

    /** Finds an HTML page to preview for [file] (itself, a sibling index.html, or the project index). */
    fun htmlFor(projectRoot: File, file: File): File? {
        if (file.extension.lowercase() in setOf("html", "htm")) return file
        File(file.parentFile, "index.html").takeIf { it.isFile }?.let { return it }
        File(projectRoot, "index.html").takeIf { it.isFile }?.let { return it }
        return projectRoot.walkTopDown().firstOrNull { it.isFile && it.extension.lowercase() in setOf("html", "htm") }
    }

    // ---- file operations (all take/return java.io.File inside a project) ----

    fun createFile(parent: File, name: String): File {
        FileUtil.validateName(name)?.let { throw IllegalArgumentException(it) }
        require(parent.canonicalFile != root.canonicalFile && isWithin(root, parent) && parent.isDirectory) { "Invalid parent folder" }
        val f = File(parent, name.trim())
        require(!f.exists()) { "\"${f.name}\" already exists" }
        f.parentFile?.mkdirs()
        check(f.createNewFile()) { "Could not create file" }
        return f
    }

    fun createFolder(parent: File, name: String): File {
        FileUtil.validateName(name)?.let { throw IllegalArgumentException(it) }
        require(parent.canonicalFile != root.canonicalFile && isWithin(root, parent) && parent.isDirectory) { "Invalid parent folder" }
        val f = File(parent, name.trim())
        require(!f.exists()) { "\"${f.name}\" already exists" }
        check(f.mkdirs()) { "Could not create folder" }
        return f
    }

    fun renameFile(file: File, newName: String): File {
        FileUtil.validateName(newName)?.let { throw IllegalArgumentException(it) }
        require(isWithin(root, file) && file != root && file.parentFile?.canonicalFile != root.canonicalFile) { "Invalid source file" }
        val target = File(file.parentFile, newName.trim())
        require(!target.exists()) { "\"${target.name}\" already exists" }
        check(file.renameTo(target)) { "Rename failed" }
        return target
    }

    fun move(file: File, destDir: File): File {
        require(isWithin(root, file) && file != root && file.parentFile?.canonicalFile != root.canonicalFile) { "Invalid source file" }
        require(destDir.canonicalFile != root.canonicalFile && isWithin(root, destDir) && destDir.isDirectory) { "Invalid destination folder" }
        require(!(file.isDirectory && isWithin(file, destDir))) { "Cannot move a folder into itself" }
        val target = File(destDir, file.name)
        require(!target.exists()) { "\"${file.name}\" already exists in the destination" }
        if (file.renameTo(target)) return target

        // Copy first and retain the source until the copied tree is verified.
        file.copyRecursively(target, overwrite = false)
        if (!target.exists() || !sameTree(file, target)) {
            target.deleteRecursively()
            throw IllegalStateException("Copy verification failed; original was preserved")
        }
        if (!file.deleteRecursively()) {
            throw IllegalStateException("Copied successfully, but the original could not be removed; both copies were preserved")
        }
        return target
    }

    private fun sameTree(source: File, target: File): Boolean {
        if (source.isFile) {
            if (!target.isFile || source.length() != target.length()) return false
            fun digest(file: File): ByteArray {
                val md = MessageDigest.getInstance("SHA-256")
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        md.update(buffer, 0, count)
                    }
                }
                return md.digest()
            }
            return digest(source).contentEquals(digest(target))
        }
        if (!source.isDirectory || !target.isDirectory) return false
        val src = source.listFiles() ?: return false
        val dst = target.listFiles() ?: return false
        if (src.size != dst.size) return false
        return src.all { child ->
            val copy = File(target, child.name)
            copy.exists() && sameTree(child, copy)
        }
    }

    fun delete(file: File) {
        require(isWithin(root, file) && file != root) { "Invalid source path" }
        check(file.deleteRecursively()) { "Delete failed" }
    }

    fun foldersUnder(projectRoot: File): List<File> =
        listOf(projectRoot) + projectRoot.walkTopDown().filter { it.isDirectory && it != projectRoot }.toList()

    // ---- Android file access (Storage Access Framework) ----

    fun importFile(uri: Uri, destDir: File): File {
        require(destDir.canonicalFile != root.canonicalFile && isWithin(root, destDir) && destDir.isDirectory) { "Invalid destination folder" }
        val displayName = FileUtil.displayName(ctx, uri)
        FileUtil.validateName(displayName)?.let { throw IllegalArgumentException("Invalid imported filename: $it") }
        val target = FileUtil.uniqueChild(destDir, displayName.trim())
        val stream = ctx.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open selected file")
        try {
            stream.use { input -> target.outputStream().use { input.copyTo(it) } }
            return target
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    fun importTree(treeUri: Uri): File {
        val tree = DocumentFile.fromTreeUri(ctx, treeUri) ?: error("Cannot open folder")
        val requestedName = tree.name ?: "Imported"
        val safeName = if (FileUtil.validateName(requestedName) == null) requestedName.trim() else "Imported"
        val dir = FileUtil.uniqueChild(root, safeName)
        check(dir.mkdirs()) { "Could not create import folder" }
        try {
            copyDoc(tree, dir)
            return dir
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    private fun copyDoc(src: DocumentFile, dest: File) {
        for (child in src.listFiles()) {
            val name = child.name ?: continue
            if (FileUtil.validateName(name) != null) continue
            val target = File(dest, name)
            if (child.isDirectory) {
                target.mkdirs()
                copyDoc(child, target)
            } else if (child.isFile) {
                ctx.contentResolver.openInputStream(child.uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    fun importZipAsProject(uri: Uri): File {
        val displayName = FileUtil.displayName(ctx, uri)
        val withoutExtension = displayName.replace(Regex("(?i)\\.zip$"), "")
        val name = if (FileUtil.validateName(withoutExtension) == null) withoutExtension.trim() else "Imported"
        val dir = FileUtil.uniqueChild(root, name.ifBlank { "Imported" })
        try {
            ctx.contentResolver.openInputStream(uri)!!.use { FileUtil.unzip(it, dir) }
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        return dir
    }

    fun exportFile(file: File, uri: Uri) {
        ctx.contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    fun exportProjectZip(dir: File, uri: Uri) {
        ctx.contentResolver.openOutputStream(uri)!!.use { FileUtil.zipDirectory(dir, it) }
    }
}
