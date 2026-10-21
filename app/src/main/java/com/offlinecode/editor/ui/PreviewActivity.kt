package com.offlinecode.editor.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.offlinecode.editor.R
import com.offlinecode.editor.project.ProjectManager
import com.offlinecode.editor.run.LocalWebClient
import com.offlinecode.editor.run.ReactBundler
import com.offlinecode.editor.util.FileUtil
import com.offlinecode.editor.util.Prefs
import java.io.File

/** Local browser preview for HTML/CSS/JS and React projects. Remote requests are blocked by default. */
class PreviewActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_ROOT = "root"
        private const val EXTRA_FILE = "file"
        private const val EXTRA_MODE = "mode" // "html" | "react"

        fun intent(ctx: Context, root: File, file: File, mode: String): Intent =
            Intent(ctx, PreviewActivity::class.java)
                .putExtra(EXTRA_ROOT, root.absolutePath)
                .putExtra(EXTRA_FILE, file.absolutePath)
                .putExtra(EXTRA_MODE, mode)
    }

    private lateinit var web: WebView
    private lateinit var consolePanel: View
    private lateinit var consoleText: TextView
    private lateinit var consoleScroll: ScrollView
    private lateinit var root: File
    private lateinit var file: File
    private var mode = "html"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)
        root = File(intent.getStringExtra(EXTRA_ROOT) ?: return finish())
        file = File(intent.getStringExtra(EXTRA_FILE) ?: return finish())
        mode = intent.getStringExtra(EXTRA_MODE) ?: "html"

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "Preview — " + file.name
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.menu.add("Reload").setIcon(R.drawable.ic_refresh).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        toolbar.setOnMenuItemClickListener { load(); true }

        consolePanel = findViewById(R.id.consolePanel)
        consoleText = findViewById(R.id.consoleText)
        consoleScroll = findViewById(R.id.consoleScroll)
        findViewById<View>(R.id.consoleClear).setOnClickListener { consoleText.text = "" }

        web = findViewById(R.id.web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.webViewClient = LocalWebClient(this, root, Prefs(this).previewNetwork)
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                log("[${m.messageLevel().name.lowercase()}] ${m.message()}  (${m.sourceId().substringAfterLast('/')}:${m.lineNumber()})")
                return true
            }
        }
        load()
    }

    private fun log(line: String) {
        consolePanel.visibility = View.VISIBLE
        consoleText.append(line + "\n")
        consoleScroll.post { consoleScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun load() {
        consoleText.text = ""
        if (mode == "react") {
            val entry = ReactBundler.findEntry(root, file)
            if (entry == null) {
                log("No React entry file found. Create src/main.jsx that calls createRoot(...).render(...).")
                return
            }
            val res = ReactBundler.build(this, root, entry)
            if (res.html == null) {
                web.loadUrl("about:blank")
                log(res.error ?: "Unknown bundling error")
            } else {
                web.loadDataWithBaseURL(LocalWebClient.BASE, res.html, "text/html", "UTF-8", null)
            }
        } else {
            val html = ProjectManager(this).htmlFor(root, file)
            if (html == null) {
                web.loadUrl("about:blank")
                log("No .html file found in this project to preview.")
                return
            }
            web.loadUrl(LocalWebClient.BASE + Uri.encode(FileUtil.relativePath(root, html), "/"))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}
