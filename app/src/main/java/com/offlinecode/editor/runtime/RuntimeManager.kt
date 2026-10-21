package com.offlinecode.editor.runtime

import android.content.Context
import android.os.Build
import com.offlinecode.editor.run.ExecStep
import com.offlinecode.editor.run.ExecutionPlan
import com.offlinecode.editor.util.FileUtil
import java.io.File
import java.io.InputStream

class InstalledRuntime(val manifest: RuntimeManifest, val dir: File)

/** Installs, lists and resolves language packages stored in <filesDir>/runtimes/<id>. */
class RuntimeManager(private val ctx: Context) {
    val root = File(ctx.filesDir, "runtimes").apply { mkdirs() }

    fun installed(): List<InstalledRuntime> =
        (root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray()).mapNotNull { d ->
            val mf = File(d, "runtime.json")
            if (!mf.isFile || mf.length() > 1_000_000) null
            else runCatching { InstalledRuntime(RuntimeManifest.parse(mf.readText()), d) }.getOrNull()
        }

    fun findFor(languageId: String): InstalledRuntime? = installed().firstOrNull { languageId in it.manifest.run.keys }

    fun isAbiCompatible(abi: String): Boolean = abi == "any" || Build.SUPPORTED_ABIS.contains(abi)

    /** Installs a .zip package archive. Blocking: call from a background thread. */
    fun install(input: InputStream, progress: (String) -> Unit): RuntimeManifest {
        val stamp = System.currentTimeMillis()
        val tmp = File(root, ".tmp-$stamp")
        var stage: File? = null
        var backup: File? = null
        var destination: File? = null
        var installed = false
        try {
            progress("Extracting…")
            FileUtil.unzip(input, tmp) { bytes -> progress("Extracting… ${FileUtil.humanSize(bytes)}") }
            val pkgDir = when {
                File(tmp, "runtime.json").isFile -> tmp
                else -> tmp.listFiles()?.singleOrNull { it.isDirectory && File(it, "runtime.json").isFile }
                    ?: throw IllegalArgumentException("Archive has no runtime.json at its root. See docs/RUNTIME_PACKAGES.md")
            }
            val manifestFile = File(pkgDir, "runtime.json")
            if (manifestFile.length() > 1_000_000) throw IllegalArgumentException("runtime.json is too large")
            val manifest = RuntimeManifest.parse(manifestFile.readText())
            if (!isAbiCompatible(manifest.abi)) {
                throw IllegalArgumentException(
                    "Package ABI '${manifest.abi}' is not supported by this device (${Build.SUPPORTED_ABIS.joinToString()})"
                )
            }
            progress("Validating package…")
            for (bin in manifest.binDirs) {
                val dir = File(pkgDir, bin).canonicalFile
                require(isWithin(pkgDir, dir) && dir.isDirectory) { "Package is missing bin directory '$bin'" }
            }
            for (exe in manifest.executables) {
                val file = File(pkgDir, exe).canonicalFile
                require(isWithin(pkgDir, file) && file.isFile) { "Package is missing executable '$exe'" }
            }

            val dest = File(root, manifest.id)
            destination = dest
            val stageDir = File(root, ".stage-${manifest.id}-$stamp")
            val backupDir = File(root, ".backup-${manifest.id}-$stamp")
            stage = stageDir
            backup = backupDir
            pkgDir.copyRecursively(stageDir, overwrite = false)
            markExecutables(stageDir, manifest)

            // Swap in the new package only after extraction, validation and staging succeed.
            if (dest.exists()) {
                if (!dest.renameTo(backupDir)) throw IllegalStateException("Could not preserve the existing package during update")
            }
            if (!stageDir.renameTo(dest)) {
                if (backupDir.exists()) backupDir.renameTo(dest)
                throw IllegalStateException("Could not activate the installed package")
            }
            installed = true
            backupDir.deleteRecursively()
            progress("Package installed")
            return manifest
        } finally {
            tmp.deleteRecursively()
            stage?.deleteRecursively()
            val backupFile = backup
            if (backupFile != null && backupFile.exists()) {
                val destinationFile = destination
                if (!installed && destinationFile != null && !destinationFile.exists()) {
                    // Best-effort rollback for an interrupted replacement.
                    backupFile.renameTo(destinationFile)
                } else {
                    backupFile.deleteRecursively()
                }
            }
        }
    }

    private fun isWithin(parent: File, child: File): Boolean {
        val p = parent.canonicalFile.path
        val c = child.canonicalFile.path
        return c == p || c.startsWith(p + File.separator)
    }

    private fun markExecutables(pkgDir: File, m: RuntimeManifest) {
        for (b in m.binDirs) {
            val dir = File(pkgDir, b).canonicalFile
            require(isWithin(pkgDir, dir) && dir.isDirectory) { "Invalid runtime bin directory '$b'" }
            dir.walkTopDown().filter { it.isFile }.forEach {
                if (!it.setExecutable(true, false) && !it.canExecute()) {
                    throw IllegalStateException("Could not mark runtime file executable: ${it.name}")
                }
            }
        }
        for (e in m.executables) {
            val file = File(pkgDir, e).canonicalFile
            require(isWithin(pkgDir, file) && file.isFile) { "Invalid runtime executable '$e'" }
            if (!file.setExecutable(true, false) && !file.canExecute()) {
                throw IllegalStateException("Could not mark runtime file executable: ${file.name}")
            }
        }
    }

    fun uninstall(id: String) {
        require(Regex("[a-z0-9][a-z0-9._-]{0,63}").matches(id)) { "Invalid runtime id" }
        val target = File(root, id)
        require(isWithin(root, target) && target.parentFile?.canonicalFile == root.canonicalFile) { "Invalid runtime path" }
        check(!target.exists() || target.deleteRecursively()) { "Could not remove runtime package" }
    }

    fun sizeOf(rt: InstalledRuntime): Long = FileUtil.sizeOf(rt.dir)

    fun totalInstalledBytes(): Long = installed().sumOf { sizeOf(it) }

    /** Expands ${PKG} ${FILE} ${DIR} ${PROJECT} ${NAME} ${BUILD} ${OUT} in the manifest's commands. */
    fun buildPlan(rt: InstalledRuntime, languageId: String, file: File, projectRoot: File): ExecutionPlan {
        val steps = rt.manifest.run[languageId]
            ?: throw IllegalArgumentException("Package '${rt.manifest.name}' has no run steps for $languageId")
        val build = File(ctx.cacheDir, "build/${projectRoot.name}").apply { mkdirs() }
        val vars = mapOf(
            "PKG" to rt.dir.absolutePath,
            "FILE" to file.absolutePath,
            "DIR" to (file.parentFile ?: projectRoot).absolutePath,
            "PROJECT" to projectRoot.absolutePath,
            "NAME" to file.nameWithoutExtension,
            "BUILD" to build.absolutePath,
            "OUT" to File(build, file.nameWithoutExtension).absolutePath
        )
        fun sub(s: String): String = vars.entries.fold(s) { acc, (k, v) -> acc.replace("\${$k}", v) }
        val env = HashMap<String, String>()
        rt.manifest.env.forEach { (k, v) -> env[k] = sub(v) }
        val bins = rt.manifest.binDirs.joinToString(":") { File(rt.dir, it).absolutePath }
        if (bins.isNotEmpty()) env["PATH"] = bins + ":" + (System.getenv("PATH") ?: "/system/bin")
        env["HOME"] = ctx.filesDir.absolutePath
        env["TMPDIR"] = ctx.cacheDir.absolutePath
        return ExecutionPlan(steps.map { st -> ExecStep(st.cmd.map(::sub)) }, file.parentFile ?: projectRoot, env)
    }
}
