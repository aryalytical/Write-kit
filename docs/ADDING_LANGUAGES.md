# Adding a language (editor support)

Editor support (highlighting, indentation, comments, autocomplete words) is a plug-in object.

1. Create a `LanguageSupport` — usually a `SimpleLanguage(...)` — in `lang/BuiltinLanguages.kt`
   (or your own file):

```kotlin
val ruby = SimpleLanguage(
    id = "ruby", displayName = "Ruby", extensions = listOf("rb"),
    keywords = setOf("def", "end", "class", "if", "else", "elsif", "do", "return"),
    lineComment = "#",
    indentStyle = IndentStyle.BRACE,          // BRACE | PYTHON | MARKUP | NONE
    runKind = RunKind.NATIVE_RUNTIME, runtimeId = "ruby",
    rules = listOf(
        TokenRule(TokenType.COMMENT, "#[^\\n]*"),
        TokenRule(TokenType.STRING, "\"(?:[^\"\\\\\\n]+|\\\\.)*\"?|'(?:[^'\\\\\\n]+|\\\\.)*'?"),
        TokenRule(TokenType.NUMBER, "\\b\\d+(?:\\.\\d+)?\\b")
    )
)
```

2. Register it: add it to `BuiltinLanguages.all()` or call `LanguageRegistry.register(ruby)` at startup.

**Rules of thumb**
- `TokenRule.regex` must NOT contain capturing groups — use `(?:...)`. Rules are joined into one alternation.
- Order matters: put comments and strings before numbers.
- Identifiers are classified in code: keywords → KEYWORD, builtins → BUILTIN, `name(` → FUNCTION, Capitalised → TYPE.
- For languages that need real state (like HTML with embedded script/style), override `tokenize(text)` — see `HtmlTokenizer`.
- Use `identifierWithDash = true` for CSS-like languages so autocomplete accepts `-` in words.
- Add a test in `app/src/test/kotlin/.../CoreLogicTest.kt`.

**Running the language**: set `runKind`:
- `WEB_PREVIEW` – opens the local browser preview
- `JS_SCRIPT` – runs in the WebView JS engine
- `REACT_PREVIEW` – bundles and previews with React
- `NATIVE_RUNTIME` – needs an installed language package whose `runtime.json` has a `run` entry named `runtimeId`
  (see `RUNTIME_PACKAGES.md`)
- `NONE` – not runnable
