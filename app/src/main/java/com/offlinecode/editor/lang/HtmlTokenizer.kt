package com.offlinecode.editor.lang

import java.util.regex.Pattern

/** State-light HTML tokenizer with embedded <script>/<style> highlighting. */
object HtmlTokenizer {
    private val TAG = Pattern.compile(
        "<!--[\\s\\S]*?(?:-->|\\z)|<![A-Za-z][^>]*>?|<(/?)([A-Za-z][\\w:-]*)((?:\"[^\"]*\"|'[^']*'|[^'\">])*)(>?)"
    )
    private val ATTR = Pattern.compile(
        "([^\\s=\"'<>/]+)(?:\\s*=\\s*(\"[^\"]*\"?|'[^']*'?|[^\\s\"'>]+))?"
    )

    fun tokenize(text: String, offset: Int = 0): List<Token> {
        val out = ArrayList<Token>()
        val m = TAG.matcher(text)
        var pos = 0
        while (pos <= text.length && m.find(pos)) {
            pos = m.end()
            val whole = m.group()
            if (whole.startsWith("<!--")) {
                out.add(Token(m.start() + offset, m.end() + offset, TokenType.COMMENT)); continue
            }
            if (whole.startsWith("<!")) {
                out.add(Token(m.start() + offset, m.end() + offset, TokenType.PREPROCESSOR)); continue
            }
            val closing = m.group(1) == "/"
            val name = m.group(2)
            val attrs = m.group(3) ?: ""
            val hasGt = m.group(4) == ">"
            out.add(Token(m.start() + offset, m.end(2) + offset, TokenType.TAG))
            if (attrs.isNotEmpty()) {
                val am = ATTR.matcher(attrs)
                val base = m.start(3)
                while (am.find()) {
                    out.add(Token(base + am.start(1) + offset, base + am.end(1) + offset, TokenType.ATTRIBUTE))
                    if (am.start(2) >= 0) out.add(Token(base + am.start(2) + offset, base + am.end(2) + offset, TokenType.STRING))
                }
            }
            if (hasGt) out.add(Token(m.end(4) - 1 + offset, m.end(4) + offset, TokenType.TAG))
            if (hasGt && !closing && !attrs.trimEnd().endsWith("/")) {
                val lower = name.lowercase()
                if (lower == "script" || lower == "style") {
                    val close = text.indexOf("</$lower", pos, ignoreCase = true)
                    val end = if (close < 0) text.length else close
                    if (end > pos) {
                        val inner = text.substring(pos, end)
                        val lang = LanguageRegistry.byId(if (lower == "script") "javascript" else "css")
                        if (lang != null) out.addAll(lang.tokenize(inner).map { Token(it.start + pos + offset, it.end + pos + offset, it.type) })
                    }
                    pos = end
                }
            }
        }
        return out
    }
}
