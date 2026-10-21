package com.offlinecode.editor.run

import android.content.Context
import com.offlinecode.editor.util.FileUtil
import org.json.JSONObject
import java.io.File

/**
 * Minimal offline "bundler" for React previews. It rewrites ES-module import/export statements
 * with regular expressions into a tiny CommonJS-style registry, then Babel-standalone (bundled
 * by YOU into assets/vendor, see scripts/fetch_react_vendor.sh) compiles JSX in the WebView.
 *
 * Supported: relative imports of .js/.jsx/.mjs/.json/.css, default/named/namespace imports,
 * `export default`, `export const|let|var|function|class`, `export { a, b as c }`, and imports
 * of react, react-dom, react-dom/client. NOT supported: npm packages, TypeScript, dynamic
 * import(), `export * from`, `export const {a} = ...`, images/assets imports.
 */
object ReactBundler {
    class Result(val html: String?, val error: String?)

    private class BundleException(msg: String) : Exception(msg)

    private val VENDOR_FILES = listOf("react.production.min.js", "react-dom.production.min.js", "babel.min.js")
    private val BUILTIN = setOf("react", "react-dom", "react-dom/client")
    private val IMPORT_FROM = Regex("""^[ \t]*import\s+([^'";]+?)\s+from\s+['"]([^'"]+)['"][ \t]*;?""", RegexOption.MULTILINE)
    private val IMPORT_SIDE = Regex("""^[ \t]*import\s+['"]([^'"]+)['"][ \t]*;?""", RegexOption.MULTILINE)
    private val EXPORT_DEFAULT_NAMED = Regex("""\bexport\s+default\s+((?:async\s+)?function\s*\*?|class)\s+([A-Za-z_][\w]*)""")
    private val EXPORT_DEFAULT = Regex("""\bexport\s+default\s+""")
    private val EXPORT_NAMED = Regex("""\bexport\s+((?:async\s+)?function|class|const|let|var)\s+([A-Za-z_][\w]*)""")
    private val EXPORT_LIST = Regex("""\bexport\s*\{([^}]*)\}\s*;?""")
    private val NS_RE = Regex("""\*\s+as\s+([A-Za-z_][\w]*)""")
    private val BRACE_RE = Regex("""\{([^}]*)\}""")

    fun missingVendorFiles(ctx: Context): List<String> {
        val have = ctx.assets.list("vendor")?.toSet() ?: emptySet()
        return VENDOR_FILES.filter { it !in have }
    }

    /** Chooses the entry module: the given file if it mounts a root, else a conventional entry. */
    fun findEntry(projectRoot: File, current: File?): File? {
        fun mounts(f: File) = f.isFile && f.readText().let { it.contains("createRoot") || it.contains("ReactDOM.render") }
        if (current != null && mounts(current)) return current
        val names = listOf("src/main.jsx", "src/index.jsx", "src/main.js", "src/index.js", "main.jsx", "index.jsx", "index.js")
        for (n in names) File(projectRoot, n).takeIf { mounts(it) }?.let { return it }
        return projectRoot.walkTopDown().firstOrNull { it.extension in setOf("jsx", "js") && mounts(it) }
    }

    fun build(ctx: Context, projectRoot: File, entry: File): Result {
        val missing = missingVendorFiles(ctx)
        if (missing.isNotEmpty()) {
            return Result(
                null,
                "React preview needs these files in app/src/main/assets/vendor/ (rebuild the app after adding them):\n" +
                    missing.joinToString("\n") { "  - $it" } +
                    "\nRun scripts/fetch_react_vendor.sh once on a machine with internet, then rebuild."
            )
        }
        val modules = LinkedHashMap<String, String>()
        val css = StringBuilder()
        return try {
            val root = projectRoot.canonicalFile
            val entryId = collect(root, entry.canonicalFile, modules, css)
            Result(page(css.toString(), modules, entryId), null)
        } catch (e: BundleException) {
            Result(null, e.message)
        }
    }

    private fun moduleId(root: File, f: File) = FileUtil.relativePath(root, f)

    private fun resolve(root: File, from: File, spec: String): File {
        val base = File(from.parentFile, spec).canonicalFile
        val tries = listOf(base, File(base.path + ".jsx"), File(base.path + ".js"), File(base.path + ".mjs"),
            File(base, "index.jsx"), File(base, "index.js"))
        val hit = tries.firstOrNull { it.isFile }
            ?: throw BundleException("Cannot resolve '$spec' imported from ${moduleId(root, from)}")
        if (!hit.path.startsWith(root.canonicalPath + File.separator)) throw BundleException("Import escapes project: $spec")
        return hit
    }

    private fun collect(root: File, file: File, modules: MutableMap<String, String>, css: StringBuilder): String {
        val id = moduleId(root, file)
        if (modules.containsKey(id)) return id
        modules[id] = "" // placeholder for cycles
        if (file.extension.lowercase() == "json") {
            modules[id] = "exports.default = " + file.readText() + ";"
            return id
        }
        var counter = 0
        val trailer = StringBuilder()

        fun requireFor(spec: String): String? { // returns module expression id or null for css
            if (spec in BUILTIN) return spec
            if (!spec.startsWith(".")) throw BundleException(
                "Unsupported import '$spec' in ${moduleId(root, file)}: only relative files and react / react-dom are available offline."
            )
            val target = resolve(root, file, spec)
            return when (target.extension.lowercase()) {
                "css" -> { css.append(target.readText()).append('\n'); null }
                "js", "jsx", "mjs", "json" -> collect(root, target, modules, css)
                else -> throw BundleException("Unsupported import type '$spec' in ${moduleId(root, file)}")
            }
        }

        fun bindings(clause: String, mod: String): String {
            val sb = StringBuilder()
            var rest = clause.trim()
            BRACE_RE.find(rest)?.let { m ->
                val items = m.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    .joinToString(", ") { it.replace(Regex("\\s+as\\s+"), ": ") }
                if (items.isNotEmpty()) sb.append("const { $items } = $mod; ")
                rest = rest.replace(m.value, "")
            }
            NS_RE.find(rest)?.let { m ->
                sb.append("const ${m.groupValues[1]} = $mod; ")
                rest = rest.replace(m.value, "")
            }
            val def = rest.replace(",", " ").trim()
            if (def.isNotEmpty()) sb.append("const $def = $mod.default; ")
            return sb.toString()
        }

        var src = file.readText()
        src = IMPORT_FROM.replace(src) { m ->
            val id2 = requireFor(m.groupValues[2])
            if (id2 == null) "" else {
                val v = "__m${counter++}"
                "const $v = __require(${JSONObject.quote(id2)}); " + bindings(m.groupValues[1], v)
            }
        }
        src = IMPORT_SIDE.replace(src) { m ->
            val id2 = requireFor(m.groupValues[1])
            if (id2 == null) "" else "__require(${JSONObject.quote(id2)});"
        }
        src = EXPORT_DEFAULT_NAMED.replace(src) { m ->
            trailer.append("exports.default = ${m.groupValues[2]};\n")
            m.groupValues[1] + " " + m.groupValues[2]
        }
        src = EXPORT_DEFAULT.replace(src, "exports.default = ")
        src = EXPORT_NAMED.replace(src) { m ->
            trailer.append("exports.${m.groupValues[2]} = ${m.groupValues[2]};\n")
            m.groupValues[1] + " " + m.groupValues[2]
        }
        src = EXPORT_LIST.replace(src) { m ->
            m.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ") { item ->
                val parts = item.split(Regex("\\s+as\\s+"))
                "exports.${parts.last()} = ${parts.first()};"
            }
        }
        modules[id] = src + "\n" + trailer
        return id
    }

    private const val PRELUDE = """(function(){
var defs={},cache={};
window.__define=function(id,fn){defs[id]=fn;};
window.__require=function(id){
 if(cache[id])return cache[id].exports;
 var fn=defs[id]; if(!fn)throw new Error('Module not found: '+id);
 var m=cache[id]={exports:{}}; fn(m.exports,window.__require); return m.exports;};
var R=window.React,D=window.ReactDOM;
cache['react']={exports:Object.assign({default:R},R)};
cache['react-dom']={exports:Object.assign({default:D},D)};
cache['react-dom/client']={exports:{default:D,createRoot:D.createRoot,hydrateRoot:D.hydrateRoot}};
})();"""

    private fun page(css: String, modules: Map<String, String>, entryId: String): String {
        val bundle = StringBuilder()
        for ((id, code) in modules) {
            bundle.append("__define(").append(JSONObject.quote(id)).append(", function(exports, __require){\n")
                .append(code).append("\n});\n")
        }
        bundle.append("__require(").append(JSONObject.quote(entryId)).append(");\n")
        val safe = bundle.toString().replace(Regex("(?i)</script"), "<\\\\/script")
        return "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>" + css.replace("</style", "<\\/style") + "</style></head><body>" +
            "<div id=\"root\"></div><div id=\"app\"></div>" +
            "<script src=\"/vendor/react.production.min.js\"></script>" +
            "<script src=\"/vendor/react-dom.production.min.js\"></script>" +
            "<script src=\"/vendor/babel.min.js\"></script>" +
            "<script>" + PRELUDE + "</script>" +
            "<script type=\"text/babel\" data-presets=\"react\">" + safe + "</script>" +
            "</body></html>"
    }
}
