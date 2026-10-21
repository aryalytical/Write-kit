package com.offlinecode.editor.ui

import android.content.Context
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object Dialogs {
    fun prompt(
        ctx: Context, title: String, hint: String, initial: String = "",
        okLabel: String = "OK", onResult: (String) -> Unit
    ) {
        val input = EditText(ctx).apply {
            setText(initial)
            this.hint = hint
            setSingleLine()
            setSelection(0, initial.length)
        }
        val pad = (20 * ctx.resources.displayMetrics.density).toInt()
        val frame = FrameLayout(ctx).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(frame)
            .setPositiveButton(okLabel) { _, _ -> onResult(input.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    fun confirm(ctx: Context, title: String, message: String, okLabel: String = "OK", onOk: () -> Unit) {
        MaterialAlertDialogBuilder(ctx).setTitle(title).setMessage(message)
            .setPositiveButton(okLabel) { _, _ -> onOk() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    fun choose(ctx: Context, title: String, items: List<String>, onPick: (Int) -> Unit) {
        MaterialAlertDialogBuilder(ctx).setTitle(title)
            .setItems(items.toTypedArray()) { _, i -> onPick(i) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    fun message(ctx: Context, title: String, message: String) {
        MaterialAlertDialogBuilder(ctx).setTitle(title).setMessage(message)
            .setPositiveButton(android.R.string.ok, null).show()
    }
}
