package com.offlinecode.editor.ui

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.offlinecode.editor.R
import com.offlinecode.editor.runtime.Downloader
import com.offlinecode.editor.runtime.InstalledRuntime
import com.offlinecode.editor.runtime.RuntimeManager
import com.offlinecode.editor.util.FileUtil
import org.json.JSONObject
import java.io.File

/** Language-package manager: shows the catalog (assets/packages/catalog.json) and installed runtimes. */
class PackagesActivity : AppCompatActivity() {

    private class CatalogEntry(
        val id: String, val name: String, val languages: List<String>, val downloadMb: String, val installMb: String,
        val abis: List<String>, val minAndroid: Int, val url: String, val sha256: String,
        val notes: String, val guide: List<String>
    )

    private class Item(val entry: CatalogEntry?, val installed: InstalledRuntime?)

    private lateinit var rt: RuntimeManager
    private lateinit var adapter: Adapter
    private lateinit var summary: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var status: TextView
    private var catalog: List<CatalogEntry> = emptyList()

    private val pickArchive = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) installFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_packages)
        rt = RuntimeManager(this)
        val tb = findViewById<MaterialToolbar>(R.id.toolbar)
        tb.title = getString(R.string.language_packages)
        tb.setNavigationIcon(R.drawable.ic_arrow_back)
        tb.setNavigationOnClickListener { finish() }
        summary = findViewById(R.id.summary)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)
        catalog = loadCatalog()
        adapter = Adapter()
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@PackagesActivity)
            adapter = this@PackagesActivity.adapter
        }
        findViewById<View>(R.id.btnImport).setOnClickListener { pickArchive.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
        findViewById<View>(R.id.btnUrl).setOnClickListener {
            Dialogs.prompt(this, "Download package", "https://… .zip", "", "Download") { url -> startDownload(url, null) }
        }
        refresh()
    }

    private fun loadCatalog(): List<CatalogEntry> = try {
        val o = JSONObject(assets.open("packages/catalog.json").bufferedReader().use { it.readText() })
        val arr = o.getJSONArray("packages")
        (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            fun list(k: String) = p.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }
            CatalogEntry(
                p.getString("id"), p.getString("name"), list("languages"), p.optString("approxDownloadMb"),
                p.optString("approxInstallMb"), list("abi"), p.optInt("minAndroid", 8), p.optString("downloadUrl"),
                p.optString("sha256"), p.optString("notes"), list("guide")
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun refresh() {
        val installed = rt.installed()
        val items = ArrayList<Item>()
        for (c in catalog) items.add(Item(c, installed.firstOrNull { it.manifest.id == c.id }))
        for (i in installed) if (catalog.none { it.id == i.manifest.id }) items.add(Item(null, i))
        adapter.submit(items)
        val free = StatFs(filesDir.path).availableBytes
        summary.text = "Device ABIs: ${Build.SUPPORTED_ABIS.joinToString()}\n" +
            "Installed packages use ${FileUtil.humanSize(rt.totalInstalledBytes())}; free space ${FileUtil.humanSize(free)}.\n" +
            "No runtime is bundled with this app. Size figures below are rough ESTIMATES."
    }

    private fun setBusy(busy: Boolean, msg: String = "") {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        status.visibility = if (busy) View.VISIBLE else View.GONE
        status.text = msg
    }

    private fun installFromUri(uri: Uri) {
        setBusy(true, "Installing…")
        Thread {
            val r = runCatching {
                contentResolver.openInputStream(uri)!!.use { input ->
                    rt.install(input) { m -> runOnUiThread { status.text = m } }
                }
            }
            runOnUiThread { finishInstall(r) }
        }.start()
    }

    private fun startDownload(url: String, entry: CatalogEntry?) {
        if (url.isBlank()) return
        setBusy(true, "Downloading…")
        Thread {
            val tmp = File(cacheDir, "pkg-download.zip")
            val r = runCatching {
                Downloader.download(url, tmp) { done, total ->
                    runOnUiThread { status.text = "Downloading… ${FileUtil.humanSize(done)}" + if (total > 0) " / ${FileUtil.humanSize(total)}" else "" }
                }
                val expected = entry?.sha256.orEmpty()
                if (expected.isNotBlank()) {
                    runOnUiThread { status.text = "Verifying checksum…" }
                    val actual = Downloader.sha256(tmp)
                    require(actual.equals(expected, ignoreCase = true)) { "SHA-256 mismatch — archive rejected" }
                }
                tmp.inputStream().use { input -> rt.install(input) { m -> runOnUiThread { status.text = m } } }
            }
            tmp.delete()
            runOnUiThread { finishInstall(r) }
        }.start()
    }

    private fun finishInstall(r: Result<*>) {
        setBusy(false)
        r.onSuccess { Toast.makeText(this, "Package installed", Toast.LENGTH_SHORT).show() }
            .onFailure { Dialogs.message(this, "Install failed", it.message ?: it.toString()) }
        refresh()
    }

    private fun showGuide(e: CatalogEntry) {
        val steps = e.guide.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        Dialogs.message(
            this, e.name + " — install guide",
            "Languages: ${e.languages.joinToString()}\n" +
                "Estimated download: ${e.downloadMb} MB • installed: ${e.installMb} MB (estimates)\n" +
                "Supported ABIs: ${e.abis.joinToString()} • Android ${e.minAndroid}+\n\n" +
                steps + "\n\n" + e.notes + "\n\nAfter installation it runs fully offline."
        )
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<Item> = emptyList()

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.pkgTitle)
            val meta: TextView = v.findViewById(R.id.pkgMeta)
            val installFile: TextView = v.findViewById(R.id.pkgInstallFile)
            val download: TextView = v.findViewById(R.id.pkgDownload)
            val guide: TextView = v.findViewById(R.id.pkgGuide)
            val remove: TextView = v.findViewById(R.id.pkgRemove)
        }

        fun submit(l: List<Item>) { items = l; notifyDataSetChanged() }
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_package, parent, false))

        override fun onBindViewHolder(h: VH, position: Int) {
            val it = items[position]
            val e = it.entry
            val inst = it.installed
            h.title.text = (e?.name ?: inst!!.manifest.name) + if (inst != null) "  ✓ installed" else ""
            val sb = StringBuilder()
            if (e != null) {
                val compatible = e.abis.any { a -> Build.SUPPORTED_ABIS.contains(a) } && Build.VERSION.SDK_INT >= 26
                sb.append("Languages: ${e.languages.joinToString()}\n")
                sb.append("Estimated: ${e.downloadMb} MB download, ${e.installMb} MB installed\n")
                sb.append("ABIs: ${e.abis.joinToString()} — ${if (compatible) "compatible with this device" else "NOT compatible with this device"}")
            }
            if (inst != null) {
                sb.append((if (sb.isEmpty()) "" else "\n") +
                    "Installed v${inst.manifest.version} (${inst.manifest.abi}) • ${FileUtil.humanSize(rt.sizeOf(inst))} on disk")
            }
            h.meta.text = sb.toString()
            h.installFile.setOnClickListener { pickArchive.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            h.download.visibility = if (e != null) View.VISIBLE else View.GONE
            h.download.setOnClickListener {
                if (e == null) return@setOnClickListener
                if (e.url.isBlank()) {
                    Dialogs.message(
                        this@PackagesActivity, "No download URL configured",
                        "This project does not host runtime archives, so '${e.name}' has no default URL.\n\n" +
                            "Options: build or obtain a compatible archive (see docs/RUNTIME_PACKAGES.md) and use " +
                            "'Install file', or use 'Download from HTTPS URL' with a host you trust, or set " +
                            "downloadUrl and sha256 in assets/packages/catalog.json."
                    )
                } else {
                    Dialogs.confirm(
                        this@PackagesActivity, "Download ${e.name}?",
                        "Only this archive is downloaded (about ${e.downloadMb} MB):\n${e.url}\nNo project data is sent.", "Download"
                    ) { startDownload(e.url, e) }
                }
            }
            h.guide.visibility = if (e != null) View.VISIBLE else View.GONE
            h.guide.setOnClickListener { if (e != null) showGuide(e) }
            h.remove.visibility = if (inst != null) View.VISIBLE else View.GONE
            h.remove.setOnClickListener {
                Dialogs.confirm(this@PackagesActivity, "Uninstall?", "Remove ${inst!!.manifest.name} and free ${FileUtil.humanSize(rt.sizeOf(inst))}?", "Uninstall") {
                    rt.uninstall(inst.manifest.id); refresh()
                }
            }
        }
    }
}
