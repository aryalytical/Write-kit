package com.offlinecode.editor.lang

/** Regex building blocks. These are Kotlin string literals holding *regex* text. */
private object R {
    const val DQ = "\"(?:[^\"\\\\\\n]+|\\\\.)*\"?"
    const val SQ = "'(?:[^'\\\\\\n]+|\\\\.)*'?"
    const val TEMPLATE = "`(?:[^`\\\\]+|\\\\[\\s\\S])*`?"
    const val C_COMMENT = "//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|\\z)"
    const val NUMBER = "\\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)[uUlLfFn]*\\b"
    const val PY_COMMENT = "#[^\\n]*"
    const val PY_STRING =
        "(?:[rRbBfFuU]{0,2})(?:'''[\\s\\S]*?(?:'''|\\z)|\"\"\"[\\s\\S]*?(?:\"\"\"|\\z))" +
            "|(?:[rRbBfFuU]{0,2})(?:\"(?:[^\"\\\\\\n]+|\\\\.)*\"?|'(?:[^'\\\\\\n]+|\\\\.)*'?)"
    const val JAVA_TEXTBLOCK = "\"\"\"[\\s\\S]*?(?:\"\"\"|\\z)"
}

private fun words(s: String): Set<String> = s.trim().split(Regex("\\s+")).toSet()

object BuiltinLanguages {

    val plain = SimpleLanguage(
        id = "plaintext", displayName = "Plain Text",
        extensions = listOf("txt", "text", "log", "md", "gitignore"),
        pairs = emptyMap()
    )

    val html: LanguageSupport = object : SimpleLanguage(
        id = "html", displayName = "HTML", extensions = listOf("html", "htm", "xhtml"),
        keywords = words(
            """html head body div span p a img ul ol li table thead tbody tr td th form input button label select
            option textarea h1 h2 h3 h4 h5 h6 header footer nav main section article aside script style link meta
            title br hr canvas video audio svg iframe pre code strong em class id href src alt type name value
            placeholder style onclick rel charset content lang width height target action method disabled checked"""
        ),
        blockComment = "<!--" to "-->", indentStyle = IndentStyle.MARKUP, runKind = RunKind.WEB_PREVIEW,
        identifierWithDash = true
    ) {
        override fun tokenize(text: String): List<Token> = HtmlTokenizer.tokenize(text)
    }

    val css = SimpleLanguage(
        id = "css", displayName = "CSS", extensions = listOf("css"),
        keywords = words(
            """color background background-color margin margin-top margin-bottom margin-left margin-right padding
            padding-top padding-bottom padding-left padding-right border border-radius display flex flex-direction
            flex-wrap grid grid-template-columns position width height max-width min-width max-height min-height
            font-size font-family font-weight text-align justify-content align-items gap overflow opacity transition
            transform z-index top left right bottom content cursor box-shadow line-height text-decoration
            block none inline-block inline absolute relative fixed sticky center auto inherit solid hidden"""
        ),
        blockComment = "/*" to "*/", indentStyle = IndentStyle.BRACE, runKind = RunKind.WEB_PREVIEW,
        identifierWithDash = true,
        rules = listOf(
            TokenRule(TokenType.COMMENT, "/\\*[\\s\\S]*?(?:\\*/|\\z)"),
            TokenRule(TokenType.STRING, "${R.DQ}|${R.SQ}"),
            TokenRule(TokenType.NUMBER, "#[0-9a-fA-F]{3,8}\\b|(?<![\\w-])\\d+(?:\\.\\d+)?(?:px|em|rem|%|vh|vw|s|ms|deg|fr|pt)?"),
            TokenRule(TokenType.ANNOTATION, "@[\\w-]+"),
            TokenRule(TokenType.PROPERTY, "--[\\w-]+|[a-zA-Z-]+(?=\\s*:(?!:))"),
            TokenRule(TokenType.TAG, "[.#][A-Za-z_][\\w-]*")
        )
    )

    private val jsKeywords = words(
        """break case catch class const continue debugger default delete do else export extends finally for function
        if import in instanceof let new return super switch this throw try typeof var void while with yield async
        await of static get set null true false undefined"""
    )
    private val jsBuiltins = words(
        """console document window Math JSON Object Array String Number Boolean Promise Map Set WeakMap Date Error
        RegExp parseInt parseFloat setTimeout setInterval clearTimeout clearInterval require module exports fetch
        Symbol localStorage sessionStorage navigator location isNaN"""
    )
    private val jsRules = listOf(
        TokenRule(TokenType.COMMENT, R.C_COMMENT),
        TokenRule(TokenType.STRING, "${R.TEMPLATE}|${R.DQ}|${R.SQ}"),
        TokenRule(TokenType.NUMBER, R.NUMBER)
    )

    val javascript = SimpleLanguage(
        id = "javascript", displayName = "JavaScript", extensions = listOf("js", "mjs", "cjs"),
        keywords = jsKeywords, builtins = jsBuiltins, lineComment = "//", blockComment = "/*" to "*/",
        indentStyle = IndentStyle.BRACE, runKind = RunKind.JS_SCRIPT, rules = jsRules
    )

    val jsx = SimpleLanguage(
        id = "jsx", displayName = "React (JSX)", extensions = listOf("jsx"),
        keywords = jsKeywords, builtins = jsBuiltins + words("React ReactDOM useState useEffect useRef useMemo useCallback useContext useReducer Fragment"),
        lineComment = "//", blockComment = "/*" to "*/",
        indentStyle = IndentStyle.BRACE, runKind = RunKind.REACT_PREVIEW,
        rules = jsRules + TokenRule(TokenType.TAG, "</?[A-Za-z][\\w.]*|/>")
    )

    val json = SimpleLanguage(
        id = "json", displayName = "JSON", extensions = listOf("json", "webmanifest"),
        keywords = words("true false null"), indentStyle = IndentStyle.BRACE,
        rules = listOf(
            TokenRule(TokenType.PROPERTY, "\"(?:[^\"\\\\\\n]+|\\\\.)*\"(?=\\s*:)"),
            TokenRule(TokenType.STRING, R.DQ),
            TokenRule(TokenType.NUMBER, "-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")
        )
    )

    val python = SimpleLanguage(
        id = "python", displayName = "Python", extensions = listOf("py", "pyw"),
        keywords = words(
            """False None True and as assert async await break class continue def del elif else except finally for
            from global if import in is lambda nonlocal not or pass raise return try while with yield match case"""
        ),
        builtins = words(
            """print len range int str float list dict set tuple bool input open type isinstance enumerate zip map
            filter sum min max abs sorted reversed super self cls Exception ValueError TypeError KeyError
            IndexError object bytes repr round any all id hash iter next format"""
        ),
        lineComment = "#", indentStyle = IndentStyle.PYTHON, runKind = RunKind.NATIVE_RUNTIME, runtimeId = "python",
        rules = listOf(
            TokenRule(TokenType.COMMENT, R.PY_COMMENT),
            TokenRule(TokenType.STRING, R.PY_STRING),
            TokenRule(TokenType.NUMBER, R.NUMBER),
            TokenRule(TokenType.ANNOTATION, "@[A-Za-z_][\\w.]*")
        )
    )

    val php = SimpleLanguage(
        id = "php", displayName = "PHP", extensions = listOf("php", "phtml"),
        keywords = words(
            """abstract and array as break callable case catch class clone const continue declare default do echo
            else elseif empty extends final finally fn for foreach function global if implements include
            include_once instanceof interface isset list match namespace new or print private protected public
            readonly require require_once return static switch throw trait try unset use var while xor yield
            true false null"""
        ),
        builtins = words(
            """strlen count array_map array_filter array_merge in_array str_replace explode implode json_encode
            json_decode var_dump print_r sprintf printf substr strpos trim date time PHP_EOL"""
        ),
        lineComment = "//", blockComment = "/*" to "*/", indentStyle = IndentStyle.BRACE,
        runKind = RunKind.NATIVE_RUNTIME, runtimeId = "php", caseInsensitiveKeywords = true,
        rules = listOf(
            TokenRule(TokenType.PREPROCESSOR, "<\\?php|<\\?=|\\?>"),
            TokenRule(TokenType.COMMENT, "${R.C_COMMENT}|#[^\\n]*"),
            TokenRule(TokenType.STRING, "${R.DQ}|${R.SQ}"),
            TokenRule(TokenType.NUMBER, R.NUMBER),
            TokenRule(TokenType.VARIABLE, "\\$[A-Za-z_]\\w*")
        )
    )

    val java = SimpleLanguage(
        id = "java", displayName = "Java", extensions = listOf("java"),
        keywords = words(
            """abstract assert boolean break byte case catch char class const continue default do double else enum
            extends final finally float for goto if implements import instanceof int interface long native new
            package private protected public return short static strictfp super switch synchronized this throw
            throws transient try void volatile while var record sealed permits yield true false null"""
        ),
        builtins = words(
            """String System Math List ArrayList Map HashMap Set HashSet Integer Long Double Boolean Object Exception
            Scanner Arrays Collections Optional StringBuilder Thread Runnable"""
        ),
        lineComment = "//", blockComment = "/*" to "*/", indentStyle = IndentStyle.BRACE,
        runKind = RunKind.NATIVE_RUNTIME, runtimeId = "java",
        rules = listOf(
            TokenRule(TokenType.COMMENT, R.C_COMMENT),
            TokenRule(TokenType.STRING, "${R.JAVA_TEXTBLOCK}|${R.DQ}|${R.SQ}"),
            TokenRule(TokenType.NUMBER, R.NUMBER),
            TokenRule(TokenType.ANNOTATION, "@[A-Za-z_][\\w.]*")
        )
    )

    private val cKeywords = """auto break case char const continue default do double else enum extern float for goto
        if inline int long register restrict return short signed sizeof static struct switch typedef union unsigned
        void volatile while bool true false NULL"""
    private val cBuiltins = """printf scanf malloc free calloc realloc strlen strcpy strcmp strcat memcpy memset fopen
        fclose fgets fprintf puts getchar size_t FILE stdin stdout stderr main exit"""
    private val cRules = listOf(
        TokenRule(TokenType.PREPROCESSOR, "^[ \\t]*#[ \\t]*[a-z_]+[^\\n]*"),
        TokenRule(TokenType.COMMENT, R.C_COMMENT),
        TokenRule(TokenType.STRING, "${R.DQ}|${R.SQ}"),
        TokenRule(TokenType.NUMBER, R.NUMBER)
    )

    val c = SimpleLanguage(
        id = "c", displayName = "C", extensions = listOf("c", "h"),
        keywords = words(cKeywords), builtins = words(cBuiltins),
        lineComment = "//", blockComment = "/*" to "*/", indentStyle = IndentStyle.BRACE,
        runKind = RunKind.NATIVE_RUNTIME, runtimeId = "c", rules = cRules
    )

    val cpp = SimpleLanguage(
        id = "cpp", displayName = "C++", extensions = listOf("cpp", "cc", "cxx", "hpp", "hh", "hxx"),
        keywords = words(
            cKeywords + """ alignas class constexpr const_cast decltype delete dynamic_cast explicit export friend
            mutable namespace new noexcept nullptr operator private protected public reinterpret_cast
            static_assert static_cast template this thread_local throw try catch typeid typename using virtual
            override final concept requires co_await co_return co_yield"""
        ),
        builtins = words(
            cBuiltins + """ std cout cin cerr endl string vector map unordered_map set unique_ptr shared_ptr
            make_unique make_shared array pair tuple optional"""
        ),
        lineComment = "//", blockComment = "/*" to "*/", indentStyle = IndentStyle.BRACE,
        runKind = RunKind.NATIVE_RUNTIME, runtimeId = "cpp", rules = cRules
    )

    fun all(): List<LanguageSupport> = listOf(plain, html, css, javascript, jsx, json, python, php, java, c, cpp)
}
