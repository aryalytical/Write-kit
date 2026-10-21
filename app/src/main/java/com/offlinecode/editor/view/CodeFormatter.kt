package com.offlinecode.editor.view

import com.offlinecode.editor.lang.IndentStyle
import com.offlinecode.editor.lang.LanguageSupport

/**
 * A pragmatic RE-INDENTER and whitespace normaliser — NOT a full pretty printer.
 *  - Brace languages (JS, JSX, CSS, JSON, Java, C, C++, PHP): re-indents by bracket depth.
 *  - HTML: re-indents by tag depth (script/style/pre/textarea bodies are left as is).
 *  - Python and everything else: whitespace normalisation only (trailing spaces, blank lines).
 * It never reflows or reorders code, so it cannot change program meaning.
 */
object CodeFormatter {

    fun format(text: String, lang: LanguageSupport, tabSize: Int): String {
        val unit = " ".repeat(tabSize.coerceIn(1, 8))
        val lf = text.replace("\r\n", "\n").replace('\r', '\n')
        val body = when (lang.indentStyle) {
            IndentStyle.BRACE ->
                if (lang.id == "php" && lf.contains("?>")) lf else reindentBraces(lf, unit, lang)
            IndentStyle.MARKUP -> reindentMarkup(lf, unit)
            else -> lf
        }
        return normalize(body, if (lang.indentStyle == IndentStyle.PYTHON) 2 else 1)
    }

    private fun normalize(text: String, maxBlank: Int): String {
        val out = ArrayList<String>()
        var blanks = 0
        for (raw in text.split("\n")) {
            val line = raw.trimEnd()
            if (line.isEmpty()) {
                blanks++
                if (blanks <= maxBlank) out.add("")
            } else {
                blanks = 0
                out.add(line)
            }
        }
        while (out.isNotEmpty() && out.first().isEmpty()) out.removeAt(0)
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
        return if (out.isEmpty()) "" else out.joinToString("\n") + "\n"
    }

    // ---------- brace languages ----------

    private class Scan(val delta: Int, val leadingClosers: Int, val inBlock: Boolean, val inRaw: Boolean)

    private fun scan(line: String, startBlock: Boolean, startRaw: Boolean, lang: LanguageSupport): Scan {
        var inBlock = startBlock
        var inRaw = startRaw
        var delta = 0
        var leading = 0
        var onlyClosers = true
        val backtick = lang.id == "javascript" || lang.id == "jsx"
        val textBlock = lang.id == "java"
        val lineC = lang.lineComment
        val hasBlock = lang.blockComment != null
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inBlock) {
                if (line.startsWith("*/", i)) { inBlock = false; i += 2 } else i++
                continue
            }
            if (inRaw) {
                when {
                    backtick && c == '`' -> { inRaw = false; i++ }
                    c == '\\' -> i += 2
                    textBlock && line.startsWith("\"\"\"", i) -> { inRaw = false; i += 3 }
                    else -> i++
                }
                continue
            }
            if (lineC != null && line.startsWith(lineC, i)) break
            if (hasBlock && line.startsWith("/*", i)) { inBlock = true; onlyClosers = false; i += 2; continue }
            if (!c.isWhitespace() && c !in "}])") onlyClosers = false
            when {
                textBlock && line.startsWith("\"\"\"", i) -> { inRaw = true; i += 3 }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < line.length && line[j] != c) { if (line[j] == '\\') j++; j++ }
                    i = j + 1
                }
                c == '`' && backtick -> { inRaw = true; i++ }
                c == '{' || c == '[' || c == '(' -> { delta++; i++ }
                c == '}' || c == ']' || c == ')' -> {
                    if (onlyClosers) leading++
                    delta--; i++
                }
                else -> i++
            }
        }
        return Scan(delta, leading, inBlock, inRaw)
    }

    private fun reindentBraces(text: String, unit: String, lang: LanguageSupport): String {
        val sb = StringBuilder()
        var depth = 0
        var inBlock = false
        var inRaw = false
        val isC = lang.id == "c" || lang.id == "cpp"
        for (raw in text.split("\n")) {
            val startBlock = inBlock
            val startRaw = inRaw
            val s = scan(raw, inBlock, inRaw, lang)
            inBlock = s.inBlock
            inRaw = s.inRaw
            val t = raw.trim()
            when {
                startRaw -> sb.append(raw)
                t.isEmpty() -> {}
                startBlock -> {
                    if (t.startsWith("*")) sb.append(unit.repeat(maxOf(0, depth))).append(' ').append(t)
                    else sb.append(raw)
                }
                isC && t.startsWith("#") -> sb.append(t)
                else -> sb.append(unit.repeat(maxOf(0, depth - s.leadingClosers))).append(t)
            }
            sb.append('\n')
            depth = maxOf(0, depth + s.delta)
        }
        return sb.toString().removeSuffix("\n")
    }

    // ---------- HTML ----------

    private val TAG_RE = Regex("<(/?)([A-Za-z][\\w:-]*)((?:\"[^\"]*\"|'[^']*'|[^'\">])*)(>?)")
    private val COMMENT_RE = Regex("<!--[\\s\\S]*?-->")
    private val RAW_TAGS = setOf("script", "style", "pre", "textarea")

    private fun reindentMarkup(text: String, unit: String): String {
        val sb = StringBuilder()
        var depth = 0
        var rawTag: String? = null
        var inComment = false
        var pendingName: String? = null
        var pendingClosing = false
        for (raw in text.split("\n")) {
            val t = raw.trim()
            if (rawTag != null) {
                if (t.contains("</$rawTag", ignoreCase = true)) {
                    rawTag = null
                    depth = maxOf(0, depth - 1)
                    sb.append(unit.repeat(depth)).append(t)
                } else sb.append(raw)
                sb.append('\n'); continue
            }
            if (t.isEmpty()) { sb.append('\n'); continue }
            if (inComment) {
                sb.append(unit.repeat(depth)).append(t).append('\n')
                if (t.contains("-->")) inComment = false
                continue
            }
            if (pendingName != null) { // continuation of a multi-line start tag
                val gt = t.indexOf('>')
                val startsClose = t.startsWith(">") || t.startsWith("/>")
                sb.append(unit.repeat(if (startsClose) depth else depth + 1)).append(t).append('\n')
                if (gt >= 0) {
                    val selfClose = t.substring(0, gt).trimEnd().endsWith("/")
                    val name = pendingName!!.lowercase()
                    if (!pendingClosing && !selfClose && name !in Indenter.VOID_TAGS) {
                        depth++
                        if (name in RAW_TAGS && !t.contains("</$name", ignoreCase = true)) rawTag = name
                    }
                    pendingName = null
                }
                continue
            }
            var scanText = COMMENT_RE.replace(t, "")
            if (scanText.contains("<!--")) {
                inComment = true
                scanText = scanText.substringBefore("<!--")
            }
            var opens = 0
            var closes = 0
            val startsWithCloser = scanText.startsWith("</")
            var newRaw: String? = null
            for (m in TAG_RE.findAll(scanText)) {
                val closing = m.groupValues[1] == "/"
                val name = m.groupValues[2].lowercase()
                val hasGt = m.groupValues[4] == ">"
                val selfClose = m.groupValues[3].trimEnd().endsWith("/")
                if (!hasGt) { pendingName = name; pendingClosing = closing; break }
                if (closing) closes++
                else if (name !in Indenter.VOID_TAGS && !selfClose) {
                    opens++
                    if (name in RAW_TAGS && !scanText.substring(m.range.last + 1).contains("</$name", ignoreCase = true)) newRaw = name
                }
            }
            val level = maxOf(0, depth - (if (startsWithCloser) 1 else 0))
            sb.append(unit.repeat(level)).append(t).append('\n')
            depth = maxOf(0, depth + opens - closes)
            if (newRaw != null) rawTag = newRaw
        }
        return sb.toString().removeSuffix("\n")
    }
}
