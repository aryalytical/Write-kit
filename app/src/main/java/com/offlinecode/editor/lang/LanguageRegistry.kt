package com.offlinecode.editor.lang

/** Central registry. Register additional languages at startup (see docs/ADDING_LANGUAGES.md). */
object LanguageRegistry {
    private val languages = LinkedHashMap<String, LanguageSupport>()
    val PLAIN: LanguageSupport = BuiltinLanguages.plain

    init {
        BuiltinLanguages.all().forEach { register(it) }
    }

    @Synchronized
    fun register(lang: LanguageSupport) {
        languages[lang.id] = lang
    }

    @Synchronized
    fun all(): List<LanguageSupport> = languages.values.toList()

    @Synchronized
    fun byId(id: String): LanguageSupport? = languages[id]

    @Synchronized
    fun forFileName(name: String): LanguageSupport {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return PLAIN
        return languages.values.firstOrNull { ext in it.extensions } ?: PLAIN
    }
}
