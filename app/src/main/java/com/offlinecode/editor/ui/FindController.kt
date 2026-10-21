package com.offlinecode.editor.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import com.offlinecode.editor.view.CodeEditText
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class FindController(
    private val bar: View,
    private val editor: CodeEditText,
    private val findInput: EditText,
    private val replaceInput: EditText,
    private val count: TextView,
    private val matchCase: CheckBox,
    private val useRegex: CheckBox
) {
    init {
        findInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = updateCount()
        })
        findInput.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) { next(true); true } else false
        }
        matchCase.setOnCheckedChangeListener { _, _ -> updateCount() }
        useRegex.setOnCheckedChangeListener { _, _ -> updateCount() }
    }

    val isOpen get() = bar.visibility == View.VISIBLE

    fun open() {
        bar.visibility = View.VISIBLE
        val sel = editor.text?.let { t ->
            val a = minOf(editor.selectionStart, editor.selectionEnd)
            val b = maxOf(editor.selectionStart, editor.selectionEnd)
            if (b > a && b - a < 200 && !t.substring(a, b).contains('\n')) t.substring(a, b) else null
        }
        if (sel != null) findInput.setText(sel)
        findInput.requestFocus()
        findInput.setSelection(findInput.text.length)
        updateCount()
    }

    fun close() {
        bar.visibility = View.GONE
        editor.requestFocus()
    }

    private fun matcher(text: String): Matcher? {
        val q = findInput.text.toString()
        if (q.isEmpty()) return null
        var flags = 0
        if (!matchCase.isChecked) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        if (!useRegex.isChecked) flags = flags or Pattern.LITERAL
        return try {
            Pattern.compile(q, flags).matcher(text)
        } catch (e: PatternSyntaxException) {
            count.text = "bad regex"
            null
        }
    }

    fun updateCount() {
        val m = matcher(editor.text.toString())
        if (m == null) { if (findInput.text.isEmpty()) count.text = ""; return }
        var total = 0
        var idx = 0
        val sel = minOf(editor.selectionStart, editor.selectionEnd)
        while (m.find() && total < 10000) {
            if (m.end() == m.start()) continue
            total++
            if (m.start() < sel || (m.start() == sel && editor.selectionStart != editor.selectionEnd)) idx = total
        }
        count.text = if (total == 0) "0" else "$idx/$total"
    }

    fun next(forward: Boolean) {
        val text = editor.text.toString()
        val m = matcher(text) ?: return
        if (forward) {
            var from = maxOf(editor.selectionStart, editor.selectionEnd)
            var found = m.find(from)
            while (found && m.end() == m.start()) found = if (m.end() < text.length) m.find(m.end() + 1) else false
            if (!found) found = m.find(0) && m.end() > m.start()
            if (found) select(m.start(), m.end())
        } else {
            val cur = minOf(editor.selectionStart, editor.selectionEnd)
            var lastBefore: IntArray? = null
            var last: IntArray? = null
            while (m.find()) {
                if (m.end() == m.start()) continue
                last = intArrayOf(m.start(), m.end())
                if (m.start() < cur) lastBefore = last
            }
            (lastBefore ?: last)?.let { select(it[0], it[1]) }
        }
        updateCount()
    }

    private fun select(a: Int, b: Int) {
        editor.requestFocus()
        editor.setSelection(a, b)
    }

    fun replaceCurrent() {
        val ed = editor.text ?: return
        val a = minOf(editor.selectionStart, editor.selectionEnd)
        val b = maxOf(editor.selectionStart, editor.selectionEnd)
        if (b > a) {
            val sel = ed.substring(a, b)
            val m = matcher(sel)
            if (m != null && m.matches()) {
                val repl = replaceInput.text.toString()
                val out = if (useRegex.isChecked) m.replaceFirst(repl) else repl
                ed.replace(a, b, out)
                editor.setSelection(a + out.length)
            }
        }
        next(true)
    }

    fun replaceAll() {
        val ed = editor.text ?: return
        val text = ed.toString()
        val m = matcher(text) ?: return
        val repl = replaceInput.text.toString()
        val out = try {
            m.replaceAll(if (useRegex.isChecked) repl else Matcher.quoteReplacement(repl))
        } catch (e: Exception) {
            count.text = "bad replacement"
            return
        }
        if (out == text) return
        val sel = editor.selectionStart
        ed.replace(0, ed.length, out)
        editor.setSelection(minOf(sel, ed.length))
        updateCount()
    }
}
