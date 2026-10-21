package com.offlinecode.editor.run

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.offlinecode.editor.util.FileUtil
import java.io.File

/**
 * Runs a .js file inside a hidden Android WebView (the system Chromium JS engine).
 * This is a BROWSER environment: no Node APIs (require/fs/process). Output comes from console.*.
 */
class JsRunner(
    private val activity: Activity,
    private val projectRoot: File,
    private val listener: OutputListener
) {
    private var web: WebView? = null
    private val main = Handler(Looper.getMainLooper())

    private inner class Bridge {
        @JavascriptInterface
        fun print(level: String, msg: String) {
            main.post { listener.onOutput(msg + "\n", if (level == "error" || level == "warn") OutKind.STDERR else OutKind.STDOUT) }
        }

        @JavascriptInterface
        fun done() {
            main.post {
                listener.onOutput("[script loaded; timers or async work may still be running — press Stop to end]\n", OutKind.INFO)
                listener.onFinished(0)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun run(file: File) {
        stop()
        val w = WebView(activity)
        web = w
        w.settings.javaScriptEnabled = true
        w.settings.allowFileAccess = false
        w.settings.allowContentAccess = false
        w.addJavascriptInterface(Bridge(), "Host")
        w.webViewClient = LocalWebClient(activity, projectRoot, false)
        val rel = Uri.encode(FileUtil.relativePath(projectRoot, file), "/")
        val isModule = Regex("(?m)^\\s*(import|export)\\s").containsMatchIn(file.readText())
        val type = if (isModule) " type=\"module\"" else ""
        val html = "<!doctype html><html><head><meta charset=\"utf-8\"><script>" + ConsoleShim.JS +
            "</script></head><body><script$type src=\"$rel\"></script>" +
            "<script>window.addEventListener('load',function(){Host.done();});</script></body></html>"
        w.loadDataWithBaseURL(LocalWebClient.BASE, html, "text/html", "UTF-8", null)
    }

    fun stop() {
        web?.let {
            it.stopLoading()
            it.loadUrl("about:blank")
            it.removeJavascriptInterface("Host")
            it.destroy()
        }
        web = null
    }
}
