package com.offlinecode.editor.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatEditText
import com.offlinecode.editor.lang.IndentStyle
import com.offlinecode.editor.lang.LanguageRegistry
import com.offlinecode.editor.lang.LanguageSupport
import com.offlinecode.editor.lang.Token
import java.util.Arrays
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Code editor built on a plain EditText:
 *  - viewport-limited syntax spans (tokenising happens off the UI thread, only visible tokens are applied)
 *  - gutter with line numbers, current-line highlight, bracket matching
 *  - language-aware auto-indent, auto-closing pairs, per-document undo/redo, comment toggling, formatting
 */
class CodeEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatEditText(context, attrs) {

    class SyntaxSpan(color: Int) : ForegroundColorSpan(color)
    class BracketSpan(color: Int) : BackgroundColorSpan(color)

    var language: LanguageSupport = LanguageRegistry.PLAIN
        set(value) {
            field = value
            scheduleHighlight(0)
        }

    var palette: EditorPalette = EditorPalette(true)
        set(value) {
            field = value
            applyPalette()
        }

    var undoManager: UndoManager = UndoManager()
    var tabSize: Int = 4

    var showLineNumbers: Boolean = true
        set(value) {
            field = value
            updateGutterPadding(true)
            invalidate()
        }

    var wordWrap: Boolean = false
        set(value) {
            field = value
            setHorizontallyScrolling(!value)
            scrollTo(0, scrollY)
            requestLayout()
        }

    /** Called after every user-originated or undo/redo text change. */
    var onUserEdit: (() -> Unit)? = null
    var onCursorChanged: (() -> Unit)? = null

    private var ready = false
    private var suppress = false
    private var editVersion = 0
    private var tokens: List<Token> = emptyList()
    private var tokensVersion = -1
    private var appliedStart = 0
    private var appliedEnd = 0

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(null, r, "highlighter", 8L * 1024 * 1024).apply { isDaemon = true }
    }
    private val highlightRunnable = Runnable { startHighlight() }
    private val applyRunnable = Runnable { applyVisibleTokens() }
    private val bracketRunnable = Runnable { updateBracketMatch() }

    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val fillPaint = Paint()
    private var gutterWidthPx = 0
    private var paraStarts = IntArray(1)
    private var paraVersion = -1

    private val watcher = object : TextWatcher {
        private var beforeStart = 0
        private var beforeText = ""
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            if (suppress) return
            beforeStart = start
            beforeText = s.subSequence(start, start + count).toString()
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (suppress) return
            undoManager.record(start, beforeText, s.subSequence(start, start + count).toString())
        }

        override fun afterTextChanged(s: Editable) {
            editVersion++
            if (suppress) return
            scheduleHighlight()
            updateGutterPadding()
            onUserEdit?.invoke()
        }
    }

    init {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        typeface = Typeface.MONOSPACE
        gravity = Gravity.TOP or Gravity.START
        setHorizontallyScrolling(true)
        filters = arrayOf<InputFilter>(SmartInputFilter())
        addTextChangedListener(watcher)
        overScrollMode = OVER_SCROLL_NEVER
        applyPalette()
        ready = true
        updateGutterPadding(true)
    }

    // ------------------------------------------------------------------ public API

    /** Replaces the whole content without touching undo history. */
    fun loadText(content: String) {
        suppress = true
        try {
            setText(content)
        } finally {
            suppress = false
        }
        editVersion++
        tokens = emptyList()
        tokensVersion = -1
        updateGutterPadding(true)
        scheduleHighlight(0)
    }

    fun insertText(s: String, cursorBack: Int = 0) {
        val ed = text ?: return
        val a = max(0, min(selectionStart, selectionEnd))
        val b = max(0, max(selectionStart, selectionEnd))
        ed.replace(a, b, s)
        setSelection((a + s.length - cursorBack).coerceIn(0, ed.length))
    }

    fun performUndo(): Boolean {
        val e = undoManager.popUndo() ?: return false
        applyHistory(e.start, e.after.length, e.before)
        return true
    }

    fun performRedo(): Boolean {
        val e = undoManager.popRedo() ?: return false
        applyHistory(e.start, e.before.length, e.after)
        return true
    }

    private fun applyHistory(start: Int, removeLen: Int, insert: String) {
        val ed = text ?: return
        suppress = true
        try {
            ed.replace(start, start + removeLen, insert)
            setSelection((start + insert.length).coerceIn(0, ed.length))
        } catch (e: IndexOutOfBoundsException) {
            undoManager.clear()
        } finally {
            suppress = false
        }
        editVersion++
        scheduleHighlight(0)
        updateGutterPadding()
        onUserEdit?.invoke()
    }

    fun cursorLineCol(): Pair<Int, Int> {
        ensureParas()
        val pos = max(0, selectionStart)
        val p = paraIndex(pos)
        return (p + 1) to (pos - paraStarts[p] + 1)
    }

    fun goToLine(line: Int) {
        val s = text.toString()
        var idx = 0
        var cur = 1
        while (cur < line) {
            val nl = s.indexOf('\n', idx)
            if (nl < 0) break
            idx = nl + 1
            cur++
        }
        requestFocus()
        setSelection(idx)
    }

    fun replacePrefix(prefixLen: Int, word: String) {
        val ed = text ?: return
        val pos = selectionStart
        if (pos < prefixLen) return
        ed.replace(pos - prefixLen, pos, word)
    }

    fun toggleComment() {
        val ed = text ?: return
        val (open, close) = language.lineComment?.let { it to "" } ?: language.blockComment ?: return
        val s = ed.toString()
        val selStart = min(selectionStart, selectionEnd)
        val selEnd = max(selectionStart, selectionEnd)
        val blockStart = s.lastIndexOf('\n', selStart - 1) + 1
        val effEnd = if (selEnd > selStart && s[selEnd - 1] == '\n') selEnd - 1 else selEnd
        val blockEnd = s.indexOf('\n', effEnd).let { if (it < 0) s.length else it }
        val lines = s.substring(blockStart, blockEnd).split("\n")
        val nonBlank = lines.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return
        val o = open.trim()
        val c = close.trim()
        val allCommented = nonBlank.all { val t = it.trim(); t.startsWith(o) && (c.isEmpty() || t.endsWith(c)) }
        val minIndent = nonBlank.minOf { l -> l.length - l.trimStart().length }
        val result = lines.joinToString("\n") { l ->
            if (l.isBlank()) l
            else if (allCommented) {
                var t = l.trim().removePrefix(o)
                if (t.startsWith(" ")) t = t.substring(1)
                if (c.isNotEmpty()) t = t.removeSuffix(c).trimEnd()
                l.substring(0, l.length - l.trimStart().length) + t
            } else {
                l.substring(0, minIndent) + o + " " + l.substring(minIndent) + (if (c.isNotEmpty()) " $c" else "")
            }
        }
        ed.replace(blockStart, blockEnd, result)
        setSelection(blockStart, blockStart + result.length)
    }

    fun formatDocument(): Boolean {
        val ed = text ?: return false
        val old = ed.toString()
        val formatted = CodeFormatter.format(old, language, tabSize)
        if (formatted == old) return false
        val sel = selectionStart
        ed.replace(0, ed.length, formatted)
        setSelection(min(sel, ed.length))
        return true
    }

    fun indentSelection(outdent: Boolean) {
        val ed = text ?: return
        val s = ed.toString()
        val a = min(selectionStart, selectionEnd)
        val b = max(selectionStart, selectionEnd)
        val unit = " ".repeat(tabSize)
        if (a == b && !outdent) {
            val ls = s.lastIndexOf('\n', a - 1) + 1
            val col = a - ls
            insertText(" ".repeat(tabSize - (col % tabSize)))
            return
        }
        val blockStart = s.lastIndexOf('\n', a - 1) + 1
        val effEnd = if (b > a && s[b - 1] == '\n') b - 1 else b
        val blockEnd = s.indexOf('\n', effEnd).let { if (it < 0) s.length else it }
        val lines = s.substring(blockStart, blockEnd).split("\n")
        val result = lines.joinToString("\n") { l ->
            if (outdent) {
                var n = 0
                while (n < tabSize && n < l.length && l[n] == ' ') n++
                if (n == 0 && l.startsWith("\t")) l.substring(1) else l.substring(n)
            } else if (l.isEmpty()) l else unit + l
        }
        if (result == s.substring(blockStart, blockEnd)) return
        ed.replace(blockStart, blockEnd, result)
        setSelection(blockStart, blockStart + result.length)
    }

    // ------------------------------------------------------------------ input handling

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_TAB && !event.isCtrlPressed && !event.isAltPressed) {
            indentSelection(event.isShiftPressed)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private inner class SmartInputFilter : InputFilter {
        override fun filter(
            source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int
        ): CharSequence? {
            if (suppress || !ready || end - start != 1) return null
            val c = source[start]
            val lang = language
            if (c == '\n') return onNewline(dest, dstart, dend, lang)

            val pairs = lang.pairs
            if (pairs.isEmpty() || dstart != dend) return null
            val next: Char? = if (dend < dest.length) dest[dend] else null
            val prev: Char? = if (dstart > 0) dest[dstart - 1] else null

            // Typing a closer that is already next: step over it.
            if (c in pairs.values && next == c) {
                post { setSelection(min(dstart + 1, length())) }
                return ""
            }
            val closer = pairs[c]
            if (closer != null) {
                val nextOk = next == null || next.isWhitespace() || next in ")]},;>"
                val isQuote = closer == c
                val prevOk = !isQuote || prev == null || (!prev.isLetterOrDigit() && prev != '\\')
                if (nextOk && prevOk) {
                    post { setSelection(min(dstart + 1, length())) }
                    return "$c$closer"
                }
                return null
            }
            if (lang.indentStyle == IndentStyle.BRACE && (c == '}' || c == ']' || c == ')')) {
                post { dedentAfterCloser() }
                return null
            }
            if (lang.id == "html" && c == '>') {
                val tag = openTagBefore(dest, dstart)
                if (tag != null) {
                    post { setSelection(min(dstart + 1, length())) }
                    return ">" + "</" + tag + ">"
                }
            }
            return null
        }
    }

    private fun onNewline(dest: Spanned, dstart: Int, dend: Int, lang: LanguageSupport): CharSequence {
        var ls = dstart
        while (ls > 0 && dest[ls - 1] != '\n') ls--
        val before = dest.subSequence(ls, dstart).toString()
        val next: Char? = if (dend < dest.length) dest[dend] else null
        val r = Indenter.onNewline(lang, before, next, " ".repeat(tabSize))
        if (r.cursorBack > 0) post { setSelection(max(0, selectionStart - r.cursorBack)) }
        return r.insert
    }

    /** For `<div class="x"` + '>' returns "div" if a closing tag should be auto-inserted. */
    private fun openTagBefore(dest: Spanned, pos: Int): String? {
        var i = pos - 1
        while (i >= 0 && pos - i < 400) {
            val ch = dest[i]
            if (ch == '>' || ch == '\n') return null
            if (ch == '<') break
            i--
        }
        if (i < 0 || dest[i] != '<') return null
        val tagText = dest.subSequence(i + 1, pos).toString()
        if (tagText.isEmpty() || tagText.endsWith("/") || !tagText[0].isLetter()) return null
        val name = tagText.takeWhile { it.isLetterOrDigit() || it == '-' || it == ':' }
        if (name.lowercase() in Indenter.VOID_TAGS) return null
        return name
    }

    private fun dedentAfterCloser() {
        val ed = text ?: return
        val pos = selectionStart
        if (pos <= 0 || pos != selectionEnd) return
        var ls = pos - 1
        while (ls > 0 && ed[ls - 1] != '\n') ls--
        val prefix = ed.subSequence(ls, pos - 1).toString()
        if (prefix.isNotEmpty() && prefix.isBlank()) {
            val remove = if (prefix.endsWith("\t")) 1 else min(tabSize, prefix.length)
            ed.replace(ls, ls + remove, "")
        }
    }

    // ------------------------------------------------------------------ highlighting

    fun scheduleHighlight(delayMs: Long = 150) {
        ui.removeCallbacks(highlightRunnable)
        ui.postDelayed(highlightRunnable, delayMs)
    }

    private fun startHighlight() {
        val t = text ?: return
        if (t.length > MAX_HIGHLIGHT_CHARS || language.rules.isEmpty() && language.keywords.isEmpty() && language.id != "html") {
            tokens = emptyList()
            tokensVersion = editVersion
            applyVisibleTokens()
            return
        }
        val snapshot = t.toString()
        val version = editVersion
        val lang = language
        worker.execute {
            val result = try {
                lang.tokenize(snapshot)
            } catch (e: Throwable) { // includes StackOverflowError from pathological regex input
                emptyList()
            }
            ui.post {
                if (version == editVersion) {
                    tokens = result
                    tokensVersion = version
                    applyVisibleTokens()
                }
            }
        }
    }

    private fun visibleRange(extraPx: Int): Pair<Int, Int> {
        val l = layout ?: return 0 to (text?.length ?: 0)
        val off = extendedPaddingTop
        val top = max(0, scrollY - off - extraPx)
        val bottom = max(0, scrollY + height - off + extraPx)
        return l.getLineStart(l.getLineForVertical(top)) to l.getLineEnd(l.getLineForVertical(bottom))
    }

    private fun applyVisibleTokens() {
        val ed = text ?: return
        for (s in ed.getSpans(0, ed.length, SyntaxSpan::class.java)) ed.removeSpan(s)
        if (tokensVersion != editVersion) return
        val (a, b) = visibleRange(height)
        appliedStart = a
        appliedEnd = b
        for (t in tokens) {
            if (t.end <= a) continue
            if (t.start >= b || t.end > ed.length) break
            ed.setSpan(SyntaxSpan(palette.colorFor(t.type)), t.start, t.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
        if (!ready || tokensVersion != editVersion) return
        val (a, b) = visibleRange(0)
        if (a < appliedStart || b > appliedEnd) {
            ui.removeCallbacks(applyRunnable)
            ui.postDelayed(applyRunnable, 30)
        }
    }

    // ------------------------------------------------------------------ bracket matching

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!ready) return
        ui.removeCallbacks(bracketRunnable)
        ui.post(bracketRunnable)
        onCursorChanged?.invoke()
    }

    private fun updateBracketMatch() {
        val ed = text ?: return
        for (s in ed.getSpans(0, ed.length, BracketSpan::class.java)) ed.removeSpan(s)
        if (selectionStart != selectionEnd || selectionStart < 0) return
        val m = BracketMatcher.find(ed, selectionStart) ?: return
        ed.setSpan(BracketSpan(palette.bracket), m.first, m.first + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ed.setSpan(BracketSpan(palette.bracket), m.second, m.second + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    // ------------------------------------------------------------------ drawing

    private fun applyPalette() {
        setBackgroundColor(palette.bg)
        setTextColor(palette.fg)
        highlightColor = palette.selection
        if (ready) {
            invalidate()
            scheduleHighlight(0)
        }
    }

    private fun ensureParas() {
        if (paraVersion == editVersion) return
        val t = text ?: return
        val chars = CharArray(t.length)
        TextUtils.getChars(t, 0, t.length, chars, 0)
        var count = 1
        for (ch in chars) if (ch == '\n') count++
        val arr = IntArray(count)
        var k = 1
        for (i in chars.indices) if (chars[i] == '\n') arr[k++] = i + 1
        paraStarts = arr
        paraVersion = editVersion
    }

    private fun paraIndex(offset: Int): Int {
        val i = Arrays.binarySearch(paraStarts, offset)
        return if (i >= 0) i else max(0, -i - 2)
    }

    private fun updateGutterPadding(force: Boolean = false) {
        if (!ready) return
        val d = resources.displayMetrics.density
        var gutter = 0
        if (showLineNumbers) {
            ensureParas()
            gutterPaint.textSize = textSize
            val digits = max(3, paraStarts.size.toString().length)
            gutter = (gutterPaint.measureText("0") * digits + 18 * d).toInt()
        }
        val total = gutter + (6 * d).toInt()
        gutterWidthPx = gutter
        if (force || paddingLeft != total) {
            setPadding(total, (4 * d).toInt(), (12 * d).toInt(), (24 * d).toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawCurrentLine(canvas)
        super.onDraw(canvas)
        if (showLineNumbers) drawGutter(canvas)
    }

    private fun drawCurrentLine(canvas: Canvas) {
        val l = layout ?: return
        if (selectionStart != selectionEnd || selectionStart < 0) return
        val line = l.getLineForOffset(selectionStart)
        val top = extendedPaddingTop + l.getLineTop(line)
        val bottom = extendedPaddingTop + l.getLineBottom(line)
        fillPaint.color = palette.currentLine
        canvas.drawRect((scrollX + gutterWidthPx).toFloat(), top.toFloat(), (scrollX + width).toFloat(), bottom.toFloat(), fillPaint)
    }

    private fun drawGutter(canvas: Canvas) {
        val l = layout ?: return
        val t = text ?: return
        ensureParas()
        val sx = scrollX.toFloat()
        val sy = scrollY
        val offY = extendedPaddingTop
        val density = resources.displayMetrics.density
        fillPaint.color = palette.gutterBg
        canvas.drawRect(sx, sy.toFloat(), sx + gutterWidthPx, (sy + height).toFloat(), fillPaint)
        fillPaint.color = palette.divider
        canvas.drawRect(sx + gutterWidthPx - 1, sy.toFloat(), sx + gutterWidthPx, (sy + height).toFloat(), fillPaint)
        gutterPaint.textSize = textSize
        val first = l.getLineForVertical(max(0, sy - offY))
        val last = l.getLineForVertical(max(0, sy + height - offY))
        val curPara = paraIndex(max(0, selectionStart))
        for (line in first..last) {
            val start = l.getLineStart(line)
            if (start == 0 || t[start - 1] == '\n') {
                val p = paraIndex(start)
                gutterPaint.color = if (p == curPara) palette.gutterActive else palette.gutterFg
                canvas.drawText((p + 1).toString(), sx + gutterWidthPx - 8 * density, (offY + l.getLineBaseline(line)).toFloat(), gutterPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ui.removeCallbacksAndMessages(null)
    }

    companion object {
        /** Above this size syntax highlighting is skipped to keep typing responsive. */
        const val MAX_HIGHLIGHT_CHARS = 250_000
    }
}
