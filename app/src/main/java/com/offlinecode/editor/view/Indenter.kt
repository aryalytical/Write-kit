package com.offlinecode.editor.view

import com.offlinecode.editor.lang.IndentStyle
import com.offlinecode.editor.lang.LanguageSupport

/** Language-aware indentation applied when Enter is pressed. */
object Indenter {
    class Result(val insert: String, val cursorBack: Int)

    val VOID_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"
    )
    private val OPEN_TAG = Regex("<([A-Za-z][\\w:-]*)(?:\\s(?:\"[^\"]*\"|'[^']*'|[^'\">])*)?>\\s*$")
    private val PY_DEDENT = setOf("return", "pass", "break", "continue", "raise")

    fun onNewline(lang: LanguageSupport, before: String, next: Char?, unit: String): Result {
        val base = before.takeWhile { it == ' ' || it == '\t' }
        val trimmed = before.trimEnd()
        return when (lang.indentStyle) {
            IndentStyle.BRACE -> {
                val last = trimmed.lastOrNull()
                if (last != null && last in "{[(") {
                    val closer = when (last) { '{' -> '}'; '[' -> ']'; else -> ')' }
                    if (next == closer) Result("\n$base$unit\n$base", 1 + base.length)
                    else Result("\n$base$unit", 0)
                } else Result("\n$base", 0)
            }
            IndentStyle.PYTHON -> {
                val hash = trimmed.indexOf('#')
                val code = (if (hash >= 0) trimmed.substring(0, hash) else trimmed).trimEnd()
                val firstWord = code.trimStart().takeWhile { it.isLetter() }
                when {
                    code.endsWith(":") -> Result("\n$base$unit", 0)
                    firstWord in PY_DEDENT && base.endsWith(unit) -> Result("\n" + base.dropLast(unit.length), 0)
                    else -> Result("\n$base", 0)
                }
            }
            IndentStyle.MARKUP -> {
                val m = OPEN_TAG.find(trimmed)
                if (m != null && m.groupValues[1].lowercase() !in VOID_TAGS && !trimmed.endsWith("/>")) {
                    if (next == '<') Result("\n$base$unit\n$base", 1 + base.length)
                    else Result("\n$base$unit", 0)
                } else Result("\n$base", 0)
            }
            IndentStyle.NONE -> Result("\n$base", 0)
        }
    }
}
