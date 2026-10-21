package com.offlinecode.editor.util

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 0 = follow system, 1 = light, 2 = dark */
    var themeMode: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()

    var fontSizeSp: Int
        get() = sp.getInt("font", 14)
        set(v) = sp.edit().putInt("font", v).apply()

    var tabSize: Int
        get() = sp.getInt("tab", 4)
        set(v) = sp.edit().putInt("tab", v).apply()

    var wordWrap: Boolean
        get() = sp.getBoolean("wrap", false)
        set(v) = sp.edit().putBoolean("wrap", v).apply()

    var lineNumbers: Boolean
        get() = sp.getBoolean("lines", true)
        set(v) = sp.edit().putBoolean("lines", v).apply()

    /** If true, previews may load http(s) resources. Default false = fully offline. */
    var previewNetwork: Boolean
        get() = sp.getBoolean("previewNet", false)
        set(v) = sp.edit().putBoolean("previewNet", v).apply()

    var lastProject: String?
        get() = sp.getString("lastProject", null)
        set(v) = sp.edit().putString("lastProject", v).apply()

    var openFiles: List<String>
        get() = sp.getString("openFiles", "")!!.split("\n").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("openFiles", v.joinToString("\n")).apply()

    var activeFile: String?
        get() = sp.getString("activeFile", null)
        set(v) = sp.edit().putString("activeFile", v).apply()
}
