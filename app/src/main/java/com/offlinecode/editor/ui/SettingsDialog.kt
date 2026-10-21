package com.offlinecode.editor.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.AdapterView
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.offlinecode.editor.util.Prefs

object SettingsDialog {
    fun show(ctx: Context, prefs: Prefs, onChanged: (themeChanged: Boolean) -> Unit) {
        val d = ctx.resources.displayMetrics.density
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0)
        }
        var themeChanged = false

        fun spinner(label: String, options: List<String>, selected: Int, onSel: (Int) -> Unit) {
            root.addView(TextView(ctx).apply { text = label; setPadding(0, (12 * d).toInt(), 0, 0) })
            val sp = Spinner(ctx)
            sp.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, options)
            sp.setSelection(selected)
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                var first = true
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (first) { first = false; return }
                    onSel(pos)
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            root.addView(sp, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        fun toggle(label: String, value: Boolean, onSet: (Boolean) -> Unit) {
            val s = SwitchCompat(ctx).apply {
                text = label
                isChecked = value
                setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
                setOnCheckedChangeListener { _, c -> onSet(c) }
            }
            root.addView(s, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        spinner("Theme", listOf("Follow system", "Light", "Dark"), prefs.themeMode) { prefs.themeMode = it; themeChanged = true }
        val sizes = listOf(11, 12, 13, 14, 16, 18, 20, 24)
        spinner("Font size (sp)", sizes.map { it.toString() }, sizes.indexOf(prefs.fontSizeSp).coerceAtLeast(0)) { prefs.fontSizeSp = sizes[it] }
        val tabs = listOf(2, 4, 8)
        spinner("Indent width (spaces)", tabs.map { it.toString() }, tabs.indexOf(prefs.tabSize).coerceAtLeast(0)) { prefs.tabSize = tabs[it] }
        toggle("Word wrap", prefs.wordWrap) { prefs.wordWrap = it }
        toggle("Line numbers", prefs.lineNumbers) { prefs.lineNumbers = it }
        toggle("Allow network access inside previews (default OFF = fully offline)", prefs.previewNetwork) { prefs.previewNetwork = it }

        MaterialAlertDialogBuilder(ctx).setTitle("Settings").setView(root)
            .setPositiveButton("Done") { _, _ -> onChanged(themeChanged) }
            .setOnCancelListener { onChanged(themeChanged) }
            .show()
    }
}
