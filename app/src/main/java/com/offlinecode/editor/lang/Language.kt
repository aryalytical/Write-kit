package com.offlinecode.editor.lang

import java.util.regex.Pattern

enum class IndentStyle { BRACE, PYTHON, MARKUP, NONE }

/** How the Run button treats files of this language. */
enum class RunKind { WEB_PREVIEW, JS_SCRIPT, REACT_PREVIEW, NATIVE_RUNTIME, NONE }

enum class TokenType {
    COMMENT, STRING, NUMBER, KEYWORD, BUILTIN, TYPE, FUNCTION,
    TAG, ATTRIBUTE, VARIABLE, PREPROCESSOR, PROPERTY, ANNOTATION
}

data class Token(val start: Int, val end: Int, val type: TokenType)

/**
 * One highlighting rule. [regex] MUST NOT contain capturing groups (use `(?:...)`),
 * because rules are joined into one big alternation and matched by group index.
 */
data class TokenRule(val type: TokenType, val regex: String)

val DEFAULT_PAIRS: Map<Char, Char> = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'')

/**
 * A language plug-in. To add a language, implement this (or use [SimpleLanguage]) and call
 * `LanguageRegistry.register(...)`. See docs/ADDING_LANGUAGES.md.
 */
interface LanguageSupport {
    val id: String
    val displayName: String
    val extensions: List<String>
    val keywords: Set<String>
    val builtins: Set<String> get() = emptySet()
    val lineComment: String?
    val blockComment: Pair<String, String>? get() = null
    val indentStyle: IndentStyle
    val runKind: RunKind
    /** Key used to look up an installed runtime package that can run this language. */
    val runtimeId: String? get() = null
    val pairs: Map<Char, Char> get() = DEFAULT_PAIRS
    val rules: List<TokenRule>
    val caseInsensitiveKeywords: Boolean get() = false
    /** Characters allowed inside identifiers when computing autocomplete prefixes. */
    val identifierWithDash: Boolean get() = false

    fun tokenize(text: String): List<Token> = RegexTokenizer.tokenize(this, text)
}

open class SimpleLanguage(
    override val id: String,
    override val displayName: String,
    override val extensions: List<String>,
    override val keywords: Set<String> = emptySet(),
    override val builtins: Set<String> = emptySet(),
    override val lineComment: String? = null,
    override val blockComment: Pair<String, String>? = null,
    override val indentStyle: IndentStyle = IndentStyle.NONE,
    override val runKind: RunKind = RunKind.NONE,
    override val runtimeId: String? = null,
    override val pairs: Map<Char, Char> = DEFAULT_PAIRS,
    override val rules: List<TokenRule> = emptyList(),
    override val caseInsensitiveKeywords: Boolean = false,
    override val identifierWithDash: Boolean = false
) : LanguageSupport

/** Generic regex tokenizer. Identifiers are classified in code (fast) instead of regex. */
object RegexTokenizer {
    private const val IDENT = "[A-Za-z_\$][\\w\$]*"
    private val cache = HashMap<String, Pattern>()

    private fun patternFor(lang: LanguageSupport): Pattern = synchronized(cache) {
        cache.getOrPut(lang.id) {
            val sb = StringBuilder()
            for (r in lang.rules) sb.append('(').append(r.regex).append(")|")
            sb.append('(').append(IDENT).append(')')
            Pattern.compile(sb.toString(), Pattern.MULTILINE)
        }
    }

    fun tokenize(lang: LanguageSupport, text: String, offset: Int = 0): List<Token> {
        if (lang.rules.isEmpty() && lang.keywords.isEmpty() && lang.builtins.isEmpty()) return emptyList()
        val out = ArrayList<Token>()
        val m = patternFor(lang).matcher(text)
        val n = lang.rules.size
        val kw = if (lang.caseInsensitiveKeywords) lang.keywords.map { it.lowercase() }.toSet() else lang.keywords
        val bi = if (lang.caseInsensitiveKeywords) lang.builtins.map { it.lowercase() }.toSet() else lang.builtins
        while (m.find()) {
            var group = 1
            while (group <= n + 1 && m.start(group) < 0) group++
            val s = m.start()
            val e = m.end()
            if (group <= n) {
                out.add(Token(s + offset, e + offset, lang.rules[group - 1].type))
                continue
            }
            val word = text.substring(s, e)
            val key = if (lang.caseInsensitiveKeywords) word.lowercase() else word
            val type = when {
                key in kw -> TokenType.KEYWORD
                key in bi -> TokenType.BUILTIN
                nextNonBlank(text, e) == '(' -> TokenType.FUNCTION
                word.length > 1 && word[0].isUpperCase() && word.any { it.isLowerCase() } -> TokenType.TYPE
                else -> null
            }
            if (type != null) out.add(Token(s + offset, e + offset, type))
        }
        return out
    }

    private fun nextNonBlank(t: String, from: Int): Char? {
        var i = from
        while (i < t.length && (t[i] == ' ' || t[i] == '\t')) i++
        return if (i < t.length) t[i] else null
    }
}
