package com.offlinecode.editor.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import com.offlinecode.editor.run.OutKind
import com.offlinecode.editor.view.EditorPalette

class OutputPanel(
    private val panel: View,
    private val title: TextView,
    private val text: TextView,
    private val scroll: ScrollView
) {
    private val buf = SpannableStringBuilder()
    private var palette = EditorPalette(true)

    val isVisible get() = panel.visibility == View.VISIBLE

    fun applyPalette(p: EditorPalette) {
        palette = p
        panel.setBackgroundColor(p.bg)
        text.setTextColor(p.fg)
        title.setTextColor(p.barFg)
        for (i in 0 until (panel as android.view.ViewGroup).childCount) {
            val header = panel.getChildAt(i)
            if (header is android.view.ViewGroup && i == 0) {
                header.setBackgroundColor(p.barBg)
                for (j in 0 until header.childCount) (header.getChildAt(j) as? TextView)?.setTextColor(p.barFg)
            }
        }
    }

    fun show() { panel.visibility = View.VISIBLE }
    fun hide() { panel.visibility = View.GONE }
    fun toggle() { if (isVisible) hide() else show() }

    fun clear() {
        buf.clear()
        text.text = buf
    }

    fun setTitle(t: String) { title.text = t }

    fun append(s: String, kind: OutKind) {
        val start = buf.length
        buf.append(s)
        val color = when (kind) {
            OutKind.STDERR -> palette.error
            OutKind.INFO -> palette.info
            OutKind.STDOUT -> palette.fg
        }
        buf.setSpan(ForegroundColorSpan(color), start, buf.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (buf.length > 200_000) buf.delete(0, buf.length - 150_000)
        text.text = buf
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}
