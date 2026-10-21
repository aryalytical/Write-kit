package com.offlinecode.editor.view

import com.offlinecode.editor.lang.LanguageSupport
import java.util.regex.Pattern

/**
 * Lightweight, offline completion: language keywords/builtins plus identifiers found in the
 * current document. This is NOT semantic completion (no type information, no member lookup).
 */
object Autocomplete {
    private val WORD = Pattern.compile("[A-Za-z_$][\\w$]*")
    private val WORD_DASH = Pattern.compile("[A-Za-z_$][\\w$-]*")

    private fun isIdent(c: Char, dash: Boolean) = c.isLetterOrDigit() || c == '_' || c == '$' || (dash && c == '-')

    fun prefixAt(t: CharSequence, pos: Int, dash: Boolean): String {
        var i = pos
        while (i > 0 && isIdent(t[i - 1], dash)) i--
        return t.subSequence(i, pos).toString()
    }

    fun suggest(t: CharSequence, pos: Int, lang: LanguageSupport, max: Int = 12): Pair<String, List<String>> {
        val dash = lang.identifierWithDash
        val prefix = prefixAt(t, pos, dash)
        if (prefix.length < 2 || prefix[0].isDigit()) return prefix to emptyList()
        val lower = prefix.lowercase()
        val found = LinkedHashSet<String>()
        fun consider(w: String) {
            if (w.length > prefix.length && w.lowercase().startsWith(lower)) found.add(w)
        }
        lang.keywords.forEach(::consider)
        lang.builtins.forEach(::consider)
        val m = (if (dash) WORD_DASH else WORD).matcher(t.subSequence(0, minOf(t.length, 120_000)))
        val seen = HashSet<String>()
        while (m.find() && found.size < 300) {
            val w = m.group()
            if (w.length > prefix.length && seen.add(w)) consider(w)
        }
        return prefix to found.sortedWith(compareBy({ it.length }, { it })).take(max)
    }
}
