package com.offlinecode.editor.runtime

import org.json.JSONArray
import org.json.JSONObject

class RunStep(val cmd: List<String>)

/** Parsed and validated runtime.json found at the root of a language package archive. */
class RuntimeManifest(
    val id: String,
    val name: String,
    val version: String,
    val abi: String,
    val languages: List<String>,
    val binDirs: List<String>,
    val executables: List<String>,
    val env: Map<String, String>,
    val run: Map<String, List<RunStep>>
) {
    companion object {
        private val ID_RE = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        private val LANG_RE = Regex("[a-z][a-z0-9_-]{0,31}")
        private val ENV_RE = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val ALLOWED_ABIS = setOf("any", "arm64-v8a", "armeabi-v7a", "x86_64", "x86")

        private fun strings(a: JSONArray?): List<String> =
            if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

        private fun validateRelativePath(path: String, field: String) {
            require(path.isNotBlank() && path.length <= 512 && !path.contains('\u0000')) { "runtime.json: invalid $field path" }
            require(!path.startsWith('/') && !path.startsWith('\\') && !Regex("^[A-Za-z]:").containsMatchIn(path)) {
                "runtime.json: $field must be a relative path"
            }
            val parts = path.replace('\\', '/').split('/')
            require(parts.none { it == ".." || it == "." || it.isBlank() }) { "runtime.json: unsafe $field path '$path'" }
        }

        fun parse(json: String): RuntimeManifest {
            require(json.length <= 1_000_000) { "runtime.json is too large" }
            val o = try { JSONObject(json) } catch (e: Exception) {
                throw IllegalArgumentException("runtime.json is not valid JSON: ${e.message}")
            }
            val id = o.optString("id")
            require(ID_RE.matches(id)) { "runtime.json: 'id' must match [a-z0-9][a-z0-9._-]*" }
            val abi = o.optString("abi", "any")
            require(abi in ALLOWED_ABIS) { "runtime.json: unsupported ABI '$abi'" }
            val runObj = o.optJSONObject("run") ?: throw IllegalArgumentException("runtime.json: missing 'run'")
            require(runObj.length() in 1..32) { "runtime.json: 'run' must contain 1–32 languages" }
            val run = HashMap<String, List<RunStep>>()
            for (lang in runObj.keys()) {
                require(LANG_RE.matches(lang)) { "runtime.json: invalid language id '$lang'" }
                val steps = runObj.getJSONArray(lang)
                require(steps.length() in 1..32) { "runtime.json: '$lang' must contain 1–32 run steps" }
                run[lang] = (0 until steps.length()).map { i ->
                    val cmd = strings(steps.getJSONObject(i).getJSONArray("cmd"))
                    require(cmd.isNotEmpty() && cmd.size <= 256) { "runtime.json: command for '$lang' cannot be empty or too large" }
                    require(cmd.all { it.isNotEmpty() && it.length <= 4096 && !it.contains('\u0000') }) {
                        "runtime.json: command for '$lang' contains an invalid argument"
                    }
                    RunStep(cmd)
                }
            }
            val envObj = o.optJSONObject("env")
            val env = HashMap<String, String>()
            envObj?.keys()?.forEach { key ->
                require(ENV_RE.matches(key)) { "runtime.json: invalid environment variable '$key'" }
                val value = envObj.getString(key)
                require(value.length <= 4096 && !value.contains('\u0000')) { "runtime.json: invalid value for '$key'" }
                env[key] = value
            }
            val binDirs = strings(o.optJSONArray("binDirs"))
            val executables = strings(o.optJSONArray("executables"))
            binDirs.forEach { validateRelativePath(it, "binDirs") }
            executables.forEach { validateRelativePath(it, "executables") }
            val langs = strings(o.optJSONArray("languages")).ifEmpty { run.keys.toList() }
            require(langs.size <= 32 && langs.all { LANG_RE.matches(it) }) { "runtime.json: invalid language list" }
            val name = o.optString("name", id)
            val version = o.optString("version", "?")
            require(name.isNotBlank() && name.length <= 120 && version.length <= 80) { "runtime.json: invalid name or version" }
            return RuntimeManifest(id, name, version, abi, langs, binDirs, executables, env, run)
        }
    }
}
