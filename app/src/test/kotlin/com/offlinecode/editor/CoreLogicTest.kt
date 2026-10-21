package com.offlinecode.editor

import com.offlinecode.editor.lang.LanguageRegistry
import com.offlinecode.editor.lang.TokenType
import com.offlinecode.editor.view.BracketMatcher
import com.offlinecode.editor.view.CodeFormatter
import com.offlinecode.editor.view.UndoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests (no Android classes). Run with: ./gradlew testDebugUnitTest */
class CoreLogicTest {

    @Test
    fun bracketMatcherFindsPair() {
        assertEquals(0 to 6, BracketMatcher.find("(a[b]c)", 1))
        assertEquals(2 to 4, BracketMatcher.find("(a[b]c)", 3))
    }

    @Test
    fun undoMergesTyping() {
        val u = UndoManager()
        u.record(0, "", "a", 1000)
        u.record(1, "", "b", 1500)
        val e = u.popUndo()
        assertNotNull(e)
        assertEquals("ab", e!!.after)
        assertFalse(u.canUndo)
        assertTrue(u.canRedo)
    }

    @Test
    fun javascriptIsReindented() {
        val js = LanguageRegistry.byId("javascript")!!
        assertEquals("function f(){\n    return 1;\n}\n", CodeFormatter.format("function f(){\nreturn 1;\n}\n", js, 4))
    }

    @Test
    fun htmlIsReindented() {
        val html = LanguageRegistry.byId("html")!!
        assertEquals("<div>\n    <p>x</p>\n</div>\n", CodeFormatter.format("<div>\n<p>x</p>\n</div>\n", html, 4))
    }

    @Test
    fun javascriptTokens() {
        val js = LanguageRegistry.byId("javascript")!!
        val types = js.tokenize("const x = 'a'; // hi").map { it.type }
        assertTrue(TokenType.KEYWORD in types)
        assertTrue(TokenType.STRING in types)
        assertTrue(TokenType.COMMENT in types)
    }

    @Test
    fun htmlTokens() {
        val html = LanguageRegistry.byId("html")!!
        val types = html.tokenize("<div class=\"a\"></div>").map { it.type }
        assertTrue(TokenType.TAG in types)
        assertTrue(TokenType.ATTRIBUTE in types)
    }

    @Test
    fun languageDetection() {
        assertEquals("python", LanguageRegistry.forFileName("a.py").id)
        assertEquals("cpp", LanguageRegistry.forFileName("a.cpp").id)
        assertEquals("jsx", LanguageRegistry.forFileName("App.jsx").id)
        assertEquals("plaintext", LanguageRegistry.forFileName("README").id)
    }
}
