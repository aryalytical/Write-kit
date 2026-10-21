package com.offlinecode.editor.run

import android.content.Context
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Serves project files at https://appassets.local/project/... and bundled assets at
 * https://appassets.local/vendor/... Everything else (remote http/https) is blocked unless
 * [allowNetwork] is true. NOTE: WebSocket connections are not interceptable by WebViewClient.
 */
class LocalWebClient(
    private val ctx: Context,
    private val projectRoot: File,
    private val allowNetwork: Boolean
) : WebViewClient() {

    companion object {
        const val HOST = "appassets.local"
        const val BASE = "https://$HOST/project/"
    }

    private fun response(status: Int, reason: String, mime: String, bytes: ByteArray): WebResourceResponse =
        WebResourceResponse(mime, "utf-8", status, reason, mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(bytes))

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html"
            "js", "mjs", "jsx" -> "text/javascript"
            "css" -> "text/css"
            "json", "webmanifest" -> "application/json"
            "svg" -> "image/svg+xml"
            "wasm" -> "application/wasm"
            "txt", "md" -> "text/plain"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host == HOST) {
            val path = url.path ?: return response(404, "Not Found", "text/plain", ByteArray(0))
            return when {
                path.startsWith("/project/") -> serveFile(path.removePrefix("/project/"))
                path.startsWith("/vendor/") -> serveAsset("vendor/" + path.removePrefix("/vendor/"))
                else -> response(404, "Not Found", "text/plain", ByteArray(0))
            }
        }
        val scheme = url.scheme ?: ""
        if (scheme == "http" || scheme == "https") {
            return if (allowNetwork) null
            else response(403, "Blocked", "text/plain", "Blocked: preview network access is disabled (offline mode)".toByteArray())
        }
        return null
    }

    private fun serveFile(rel: String): WebResourceResponse {
        val f = File(projectRoot, rel).canonicalFile
        val base = projectRoot.canonicalPath + File.separator
        if (!f.path.startsWith(base) || !f.isFile) return response(404, "Not Found", "text/plain", ByteArray(0))
        return response(200, "OK", mimeFor(f.name), f.readBytes())
    }

    private fun serveAsset(name: String): WebResourceResponse = try {
        response(200, "OK", mimeFor(name), ctx.assets.open(name).use { it.readBytes() })
    } catch (e: Exception) {
        response(404, "Not Found", "text/plain", ByteArray(0))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val u = request.url
        val external = (u.scheme == "http" || u.scheme == "https") && u.host != HOST
        return external && !allowNetwork
    }
}
