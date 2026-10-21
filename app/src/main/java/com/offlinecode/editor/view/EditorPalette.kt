package com.offlinecode.editor.view

import android.content.Context
import android.content.res.Configuration
import com.offlinecode.editor.lang.TokenType

/** VS Code "Dark+" / "Light+" inspired colours. */
class EditorPalette(val dark: Boolean) {
    val bg = if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
    val fg = if (dark) 0xFFD4D4D4.toInt() else 0xFF1F1F1F.toInt()
    val gutterBg = if (dark) 0xFF1E1E1E.toInt() else 0xFFF5F5F5.toInt()
    val gutterFg = if (dark) 0xFF858585.toInt() else 0xFF999999.toInt()
    val gutterActive = if (dark) 0xFFC6C6C6.toInt() else 0xFF333333.toInt()
    val divider = if (dark) 0xFF333333.toInt() else 0xFFDDDDDD.toInt()
    val currentLine = if (dark) 0x14FFFFFF else 0x10000000
    val selection = if (dark) 0x66264F78 else 0x66ADD6FF
    val bracket = if (dark) 0x66FFFFFF else 0x55007ACC
    val barBg = if (dark) 0xFF252526.toInt() else 0xFFEFEFEF.toInt()
    val tabActive = if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
    val tabInactive = if (dark) 0xFF2D2D2D.toInt() else 0xFFE4E4E4.toInt()
    val barFg = if (dark) 0xFFCCCCCC.toInt() else 0xFF333333.toInt()
    val error = if (dark) 0xFFF48771.toInt() else 0xFFCD3131.toInt()
    val info = if (dark) 0xFF9CDCFE.toInt() else 0xFF0451A5.toInt()

    fun colorFor(t: TokenType): Int = when (t) {
        TokenType.COMMENT -> if (dark) 0xFF6A9955.toInt() else 0xFF008000.toInt()
        TokenType.STRING -> if (dark) 0xFFCE9178.toInt() else 0xFFA31515.toInt()
        TokenType.NUMBER -> if (dark) 0xFFB5CEA8.toInt() else 0xFF098658.toInt()
        TokenType.KEYWORD -> if (dark) 0xFF569CD6.toInt() else 0xFF0000FF.toInt()
        TokenType.BUILTIN -> if (dark) 0xFF4FC1FF.toInt() else 0xFF0070C1.toInt()
        TokenType.TYPE -> if (dark) 0xFF4EC9B0.toInt() else 0xFF267F99.toInt()
        TokenType.FUNCTION -> if (dark) 0xFFDCDCAA.toInt() else 0xFF795E26.toInt()
        TokenType.TAG -> if (dark) 0xFF569CD6.toInt() else 0xFF800000.toInt()
        TokenType.ATTRIBUTE -> if (dark) 0xFF9CDCFE.toInt() else 0xFFE50000.toInt()
        TokenType.VARIABLE -> if (dark) 0xFF9CDCFE.toInt() else 0xFF001080.toInt()
        TokenType.PREPROCESSOR -> if (dark) 0xFFC586C0.toInt() else 0xFFAF00DB.toInt()
        TokenType.PROPERTY -> if (dark) 0xFF9CDCFE.toInt() else 0xFF0451A5.toInt()
        TokenType.ANNOTATION -> if (dark) 0xFFDCDCAA.toInt() else 0xFF795E26.toInt()
    }

    companion object {
        fun from(ctx: Context): EditorPalette {
            val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return EditorPalette(night == Configuration.UI_MODE_NIGHT_YES)
        }
    }
}
