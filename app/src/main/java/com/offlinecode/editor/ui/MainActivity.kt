package com.offlinecode.editor.ui

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.offlinecode.editor.R
import com.offlinecode.editor.lang.LanguageRegistry
import com.offlinecode.editor.lang.RunKind
import com.offlinecode.editor.project.ExplorerAdapter
import com.offlinecode.editor.project.ProjectManager
import com.offlinecode.editor.project.ProjectTemplates
import com.offlinecode.editor.run.JsRunner
import com.offlinecode.editor.run.LocalRunner
import com.offlinecode.editor.run.OutKind
import com.offlinecode.editor.run.OutputListener
import com.offlinecode.editor.runtime.RuntimeManager
import com.offlinecode.editor.util.FileUtil
import com.offlinecode.editor.util.Prefs
import com.offlinecode.editor.view.Autocomplete
import com.offlinecode.editor.view.CodeEditText
import com.offlinecode.editor.view.EditorPalette
import com.offlinecode.editor.view.UndoManager
import java.io.File

class MainActivity : AppCompatActivity(), OutputListener {

    private class OpenDoc(val file: File, var text: String) {
        var dirty = false
        var selection = 0
        var scrollY = 0
        val undo = UndoManager()
    }

    private lateinit var prefs: Prefs
    private lateinit var projects: ProjectManager
    private lateinit var runtimes: RuntimeManager
    private lateinit var palette: EditorPalette

    private lateinit var drawer: DrawerLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var editor: CodeEditText
    private lateinit var emptyView: TextView
    private lateinit var tabScroll: View
    private lateinit var tabContainer: LinearLayout
    private lateinit var suggestScroll: View
    private lateinit var suggestContainer: LinearLayout
    private lateinit var symbolScroll: View
    private lateinit var symbolContainer: LinearLayout
    private lateinit var projectName: TextView
    private lateinit var explorer: ExplorerAdapter
    private lateinit var find: FindController
    private lateinit var output: OutputPanel

    private var projectRoot: File? = null
    private val docs = ArrayList<OpenDoc>()
    private var current: OpenDoc? = null
    private var importTarget: File? = null
    private var exportSource: File? = null

    private val ui = Handler(Looper.getMainLooper())
    private val suggestRunnable = Runnable { refreshSuggestions() }
    private var localRunner: LocalRunner? = null
    private var jsRunner: JsRunner? = null

    // ---- Storage Access Framework launchers ----
    private val importFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val dir = importTarget ?: projectRoot ?: return@registerForActivityResult
        guarded("Import failed") { uris.forEach { projects.importFile(it, dir) } }
        explorer.expand(dir); explorer.rebuild()
    }
    private val importFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) guarded("Import failed") { openProject(projects.importTree(uri)) }
    }
    private val importZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) guarded("Import failed") { openProject(projects.importZipAsProject(uri)) }
    }
    private val exportZip = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val root = projectRoot
        if (uri != null && root != null) { saveAll(); guarded("Export failed") { projects.exportProjectZip(root, uri) }; toast("Exported") }
    }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val f = exportSource
        if (uri != null && f != null) { saveAll(); guarded("Export failed") { projects.exportFile(f, uri) }; toast("Exported") }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(
            when (prefs.themeMode) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        projects = ProjectManager(this)
        runtimes = RuntimeManager(this)
        palette = EditorPalette.from(this)

        drawer = findViewById(R.id.drawer)
        toolbar = findViewById(R.id.toolbar)
        editor = findViewById(R.id.editor)
        emptyView = findViewById(R.id.emptyView)
        tabScroll = findViewById(R.id.tabScroll)
        tabContainer = findViewById(R.id.tabContainer)
        suggestScroll = findViewById(R.id.suggestScroll)
        suggestContainer = findViewById(R.id.suggestContainer)
        symbolScroll = findViewById(R.id.symbolScroll)
        symbolContainer = findViewById(R.id.symbolContainer)
        projectName = findViewById(R.id.projectName)

        toolbar.inflateMenu(R.menu.main_menu)
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener { drawer.openDrawer(Gravity.START) }
        toolbar.setOnMenuItemClickListener { onMenu(it) }

        output = OutputPanel(
            findViewById(R.id.outputPanel), findViewById(R.id.outputTitle),
            findViewById(R.id.outputText), findViewById(R.id.outputScroll)
        )
        findViewById<View>(R.id.outputStop).setOnClickListener { stopRun() }
        findViewById<View>(R.id.outputClear).setOnClickListener { output.clear() }
        findViewById<View>(R.id.outputClose).setOnClickListener { output.hide() }

        find = FindController(
            findViewById(R.id.findBar), editor, findViewById(R.id.findInput), findViewById(R.id.replaceInput),
            findViewById(R.id.findCount), findViewById<CheckBox>(R.id.findCase), findViewById<CheckBox>(R.id.findRegex)
        )
        findViewById<View>(R.id.findNext).setOnClickListener { find.next(true) }
        findViewById<View>(R.id.findPrev).setOnClickListener { find.next(false) }
        findViewById<View>(R.id.findClose).setOnClickListener { find.close() }
        findViewById<View>(R.id.replaceOne).setOnClickListener { find.replaceCurrent() }
        findViewById<View>(R.id.replaceAll).setOnClickListener { find.replaceAll() }

        explorer = ExplorerAdapter({ openFile(it); drawer.closeDrawers() }, { f, v -> showFileMenu(f, v) })
        findViewById<RecyclerView>(R.id.explorerList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = explorer
        }
        findViewById<View>(R.id.btnNewFile).setOnClickListener { newFile(projectRoot) }
        findViewById<View>(R.id.btnNewFolder).setOnClickListener { newFolder(projectRoot) }
        findViewById<View>(R.id.btnProjects).setOnClickListener { drawer.closeDrawers(); showProjectsMenu() }

        editor.onUserEdit = { markDirty(); ui.removeCallbacks(suggestRunnable); ui.postDelayed(suggestRunnable, 150) }
        editor.onCursorChanged = { updateStatus() }

        buildSymbolBar()
        applyPrefs()

        val last = prefs.lastProject?.let { projects.find(it) }
        if (last != null) {
            openProject(last, restore = true)
        } else {
            val existing = projects.list().firstOrNull()
            openProject(existing ?: projects.create("Welcome", ProjectTemplates.all.first()))
        }
    }

    override fun onPause() {
        super.onPause()
        saveAll()
        persistSession()
    }

    override fun onDestroy() {
        stopRun()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ appearance

    private fun applyPrefs() {
        palette = EditorPalette.from(this)
        editor.palette = palette
        editor.textSize = prefs.fontSizeSp.toFloat()
        editor.tabSize = prefs.tabSize
        editor.wordWrap = prefs.wordWrap
        editor.showLineNumbers = prefs.lineNumbers
        tabScroll.setBackgroundColor(palette.barBg)
        suggestScroll.setBackgroundColor(palette.barBg)
        symbolScroll.setBackgroundColor(palette.barBg)
        emptyView.setBackgroundColor(palette.bg)
        emptyView.setTextColor(palette.gutterFg)
        findViewById<View>(R.id.findBar).setBackgroundColor(palette.barBg)
        output.applyPalette(palette)
        explorer.setTextColor(palette.barFg)
        rebuildTabs()
        buildSymbolBar()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------------ symbol & suggestion bars

    private fun buildSymbolBar() {
        symbolContainer.removeAllViews()
        data class Sym(val label: String, val text: String, val back: Int = 0)
        val syms = listOf(
            Sym("⇥", "\t"), Sym("{ }", "{}", 1), Sym("[ ]", "[]", 1), Sym("( )", "()", 1), Sym("=>", "=>"),
            Sym(";", ";"), Sym("\" \"", "\"\"", 1), Sym("' '", "''", 1), Sym("`", "`"), Sym(":", ":"), Sym(",", ","),
            Sym(".", "."), Sym("<", "<"), Sym(">", ">"), Sym("/", "/"), Sym("=", "="), Sym("!", "!"), Sym("&", "&"),
            Sym("|", "|"), Sym("+", "+"), Sym("-", "-"), Sym("*", "*"), Sym("_", "_"), Sym("#", "#"), Sym("$", "$"),
            Sym("%", "%"), Sym("\\", "\\"), Sym("?", "?"), Sym("~", "~")
        )
        for (s in syms) {
            val tv = TextView(this).apply {
                text = s.label
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                textSize = 16f
                setTextColor(palette.barFg)
                minWidth = dp(44)
                setPadding(dp(8), 0, dp(8), 0)
                isClickable = true
                setOnClickListener {
                    if (s.text == "\t") editor.indentSelection(false) else editor.insertText(s.text, s.back)
                }
            }
            symbolContainer.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun refreshSuggestions() {
        val ed = editor.text
        if (current == null || ed == null || editor.selectionStart != editor.selectionEnd) {
            suggestScroll.visibility = View.GONE; return
        }
        val (prefix, list) = Autocomplete.suggest(ed, editor.selectionStart, editor.language)
        suggestContainer.removeAllViews()
        if (list.isEmpty()) { suggestScroll.visibility = View.GONE; return }
        for (w in list) {
            val tv = TextView(this).apply {
                text = w
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                setTextColor(palette.fg)
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { editor.replacePrefix(prefix.length, w) }
            }
            suggestContainer.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        suggestScroll.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------ documents & tabs

    private fun markDirty() {
        val c = current ?: return
        if (!c.dirty) { c.dirty = true; rebuildTabs() }
    }

    private fun captureCurrent() {
        val c = current ?: return
        if (c.dirty) c.text = editor.text.toString()
        c.selection = editor.selectionStart
        c.scrollY = editor.scrollY
    }

    private fun openFile(file: File) {
        docs.firstOrNull { it.file == file }?.let { switchTo(it); return }
        if (file.length() > 2_000_000) { toast("File is larger than 2 MB"); return }
        val bytes = try { file.readBytes() } catch (e: Exception) { toast("Cannot read file"); return }
        if (FileUtil.isBinary(bytes)) { toast("Binary files cannot be edited"); return }
        val doc = OpenDoc(file, String(bytes, Charsets.UTF_8))
        docs.add(doc)
        switchTo(doc)
    }

    private fun switchTo(doc: OpenDoc?) {
        captureCurrent()
        current = doc
        if (doc == null) {
            editor.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            suggestScroll.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            editor.visibility = View.VISIBLE
            editor.undoManager = doc.undo
            editor.language = LanguageRegistry.forFileName(doc.file.name)
            editor.loadText(doc.text)
            editor.setSelection(doc.selection.coerceIn(0, editor.length()))
            val y = doc.scrollY
            editor.post { editor.scrollTo(editor.scrollX, y) }
            explorer.setActive(doc.file)
        }
        rebuildTabs()
        updateStatus()
    }

    private fun closeDoc(doc: OpenDoc) {
        if (doc === current) captureCurrent()
        saveDoc(doc)
        val idx = docs.indexOf(doc)
        docs.remove(doc)
        if (doc === current) {
            current = null
            switchTo(docs.getOrNull(minOf(idx, docs.size - 1)))
        } else rebuildTabs()
    }

    private fun rebuildTabs() {
        tabContainer.removeAllViews()
        for (d in docs) {
            val active = d === current
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(if (active) palette.tabActive else palette.tabInactive)
            }
            tab.addView(TextView(this).apply {
                text = d.file.name + if (d.dirty) " ●" else ""
                setTextColor(if (active) palette.fg else palette.gutterFg)
                setPadding(dp(14), 0, dp(6), 0)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { switchTo(d) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            tab.addView(TextView(this).apply {
                text = "×"
                textSize = 18f
                setTextColor(palette.gutterFg)
                setPadding(dp(8), 0, dp(12), 0)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { closeDoc(d) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            tabContainer.addView(tab, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun updateStatus() {
        val c = current
        if (c == null) { toolbar.title = getString(R.string.app_name); toolbar.subtitle = projectRoot?.name; return }
        val (line, col) = editor.cursorLineCol()
        toolbar.title = c.file.name
        toolbar.subtitle = "${editor.language.displayName} • Ln $line, Col $col"
    }

    private fun saveDoc(d: OpenDoc) {
        if (d === current) d.text = editor.text.toString()
        if (!d.dirty) return
        try {
            FileUtil.writeAtomic(d.file, d.text)
            d.dirty = false
        } catch (e: Exception) {
            toast("Save failed: ${e.message}")
        }
    }

    private fun saveAll() {
        var changed = false
        for (d in docs) if (d.dirty) { saveDoc(d); changed = true }
        if (changed) rebuildTabs()
    }

    private fun persistSession() {
        val root = projectRoot ?: return
        prefs.lastProject = root.name
        prefs.openFiles = docs.map { FileUtil.relativePath(root, it.file) }
        prefs.activeFile = current?.let { FileUtil.relativePath(root, it.file) }
    }

    // ------------------------------------------------------------------ projects

    private fun openProject(dir: File, restore: Boolean = false) {
        saveAll()
        docs.clear()
        current = null
        projectRoot = dir
        projectName.text = dir.name
        explorer.setRoot(dir)
        switchTo(null)
        if (restore) {
            prefs.openFiles.map { File(dir, it) }.filter { it.isFile }.forEach { openFile(it) }
            prefs.activeFile?.let { File(dir, it) }?.takeIf { it.isFile }?.let { openFile(it) }
        }
        if (docs.isEmpty()) projects.defaultFile(dir)?.let { openFile(it) }
        prefs.lastProject = dir.name
        updateStatus()
    }

    private fun showProjectsMenu() {
        val items = listOf(
            "New project…", "Open project…", "Rename project…", "Delete project…",
            "Import folder from device as project…", "Import project from .zip…", "Export project as .zip…"
        )
        Dialogs.choose(this, "Projects", items) { i ->
            when (i) {
                0 -> newProject()
                1 -> openProjectDialog()
                2 -> projectRoot?.let { root ->
                    Dialogs.prompt(this, "Rename project", "Name", root.name, "Rename") { n ->
                        guarded("Rename failed") {
                            FileUtil.validateName(n)?.let { throw IllegalArgumentException(it) }
                            saveAll()
                            openProject(projects.rename(root, n))
                        }
                    }
                }
                3 -> projectRoot?.let { root ->
                    Dialogs.confirm(this, "Delete project", "Delete \"${root.name}\" and all its files? This cannot be undone.", "Delete") {
                        docs.clear(); current = null
                        projects.delete(root)
                        val next = projects.list().firstOrNull() ?: projects.create("Welcome", ProjectTemplates.all.first())
                        openProject(next)
                    }
                }
                4 -> importFolder.launch(null)
                5 -> importZip.launch(arrayOf("application/zip", "application/octet-stream"))
                6 -> projectRoot?.let { exportZip.launch(it.name + ".zip") }
            }
        }
    }

    private fun newProject() {
        Dialogs.prompt(this, "New project", "Project name", "", "Next") { name ->
            val err = FileUtil.validateName(name)
            if (err != null) { toast(err); return@prompt }
            Dialogs.choose(this, "Template", ProjectTemplates.all.map { it.title }) { t ->
                guarded("Could not create project") { openProject(projects.create(name, ProjectTemplates.all[t])) }
            }
        }
    }

    private fun openProjectDialog() {
        val list = projects.list()
        if (list.isEmpty()) { toast("No projects"); return }
        Dialogs.choose(this, "Open project", list.map { it.name }) { openProject(list[it]) }
    }

    // ------------------------------------------------------------------ explorer operations

    private fun showFileMenu(f: File, anchor: View) {
        val pm = PopupMenu(this, anchor)
        val items = mutableListOf("New file here", "New folder here", "Rename", "Move…", "Delete", "Import files here…")
        if (f.isFile) items.add("Export file…")
        items.forEachIndexed { i, s -> pm.menu.add(0, i, i, s) }
        pm.setOnMenuItemClickListener { mi ->
            val dir = if (f.isDirectory) f else f.parentFile
            when (mi.itemId) {
                0 -> newFile(dir)
                1 -> newFolder(dir)
                2 -> renameEntry(f)
                3 -> moveEntry(f)
                4 -> deleteEntry(f)
                5 -> { importTarget = dir; importFiles.launch(arrayOf("*/*")) }
                6 -> { exportSource = f; exportFile.launch(f.name) }
            }
            true
        }
        pm.show()
    }

    private fun newFile(dir: File?) {
        val d = dir ?: return
        Dialogs.prompt(this, "New file", "File name (e.g. app.js)", "", "Create") { n ->
            guarded("Could not create file") {
                FileUtil.validateName(n)?.let { throw IllegalArgumentException(it) }
                val f = projects.createFile(d, n)
                explorer.expand(d); explorer.rebuild()
                openFile(f)
            }
        }
    }

    private fun newFolder(dir: File?) {
        val d = dir ?: return
        Dialogs.prompt(this, "New folder", "Folder name", "", "Create") { n ->
            guarded("Could not create folder") {
                FileUtil.validateName(n)?.let { throw IllegalArgumentException(it) }
                projects.createFolder(d, n)
                explorer.expand(d); explorer.rebuild()
            }
        }
    }

    private fun closeDocsUnder(path: File) {
        saveAll()
        val victims = docs.filter { it.file == path || it.file.absolutePath.startsWith(path.absolutePath + File.separator) }
        for (v in victims) closeDoc(v)
    }

    private fun renameEntry(f: File) {
        Dialogs.prompt(this, "Rename", "New name", f.name, "Rename") { n ->
            guarded("Rename failed") {
                FileUtil.validateName(n)?.let { throw IllegalArgumentException(it) }
                closeDocsUnder(f)
                projects.renameFile(f, n)
                explorer.rebuild()
            }
        }
    }

    private fun moveEntry(f: File) {
        val root = projectRoot ?: return
        val folders = projects.foldersUnder(root).filter { it != f.parentFile && !it.absolutePath.startsWith(f.absolutePath + File.separator) && it != f }
        if (folders.isEmpty()) { toast("No other folders"); return }
        Dialogs.choose(this, "Move to…", folders.map { if (it == root) "/ (project root)" else "/" + FileUtil.relativePath(root, it) }) { i ->
            guarded("Move failed") {
                closeDocsUnder(f)
                projects.move(f, folders[i])
                explorer.expand(folders[i]); explorer.rebuild()
            }
        }
    }

    private fun deleteEntry(f: File) {
        Dialogs.confirm(this, "Delete", "Delete \"${f.name}\"${if (f.isDirectory) " and everything in it" else ""}?", "Delete") {
            guarded("Delete failed") {
                closeDocsUnder(f)
                projects.delete(f)
                explorer.rebuild()
            }
        }
    }

    // ------------------------------------------------------------------ menu & keyboard

    private fun onMenu(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_run -> runCurrent()
            R.id.action_undo -> if (!editor.performUndo()) toast("Nothing to undo")
            R.id.action_redo -> if (!editor.performRedo()) toast("Nothing to redo")
            R.id.action_find -> if (current != null) find.open()
            R.id.action_save -> { saveAll(); toast("Saved") }
            R.id.action_save_all -> { saveAll(); toast("Saved all") }
            R.id.action_format -> if (current != null) { if (!editor.formatDocument()) toast("Already formatted") }
            R.id.action_comment -> if (current != null) editor.toggleComment()
            R.id.action_goto -> if (current != null) Dialogs.prompt(this, "Go to line", "Line number", "", "Go") { s ->
                s.toIntOrNull()?.let { editor.goToLine(it) }
            }
            R.id.action_preview_web -> preview("html")
            R.id.action_preview_react -> preview("react")
            R.id.action_output -> output.toggle()
            R.id.action_projects -> showProjectsMenu()
            R.id.action_packages -> startActivity(Intent(this, PackagesActivity::class.java))
            R.id.action_settings -> {
                saveAll(); persistSession()
                SettingsDialog.show(this, prefs) { themeChanged ->
                    if (themeChanged) recreate() else applyPrefs()
                }
            }
            R.id.action_about -> Dialogs.message(this, "Feature status", FEATURE_STATUS)
            else -> return false
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_S -> { saveAll(); toast("Saved"); return true }
                KeyEvent.KEYCODE_Z -> { if (event.isShiftPressed) editor.performRedo() else editor.performUndo(); return true }
                KeyEvent.KEYCODE_Y -> { editor.performRedo(); return true }
                KeyEvent.KEYCODE_F -> {
                    if (event.isShiftPressed) { if (current != null) editor.formatDocument() } else if (current != null) find.open()
                    return true
                }
                KeyEvent.KEYCODE_ENTER -> { runCurrent(); return true }
                KeyEvent.KEYCODE_SLASH -> { editor.toggleComment(); return true }
                KeyEvent.KEYCODE_W -> { current?.let { closeDoc(it) }; return true }
                KeyEvent.KEYCODE_B -> {
                    if (drawer.isDrawerOpen(Gravity.START)) drawer.closeDrawers() else drawer.openDrawer(Gravity.START); return true
                }
                KeyEvent.KEYCODE_GRAVE -> { output.toggle(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ------------------------------------------------------------------ running

    private fun preview(mode: String) {
        val root = projectRoot ?: return
        saveAll()
        val file = current?.file ?: projects.defaultFile(root) ?: run { toast("Project is empty"); return }
        startActivity(PreviewActivity.intent(this, root, file, mode))
    }

    private fun runCurrent() {
        val doc = current ?: run { toast("Open a file first"); return }
        val root = projectRoot ?: return
        saveAll()
        stopRun()
        val lang = editor.language
        when (lang.runKind) {
            RunKind.WEB_PREVIEW -> preview("html")
            RunKind.REACT_PREVIEW -> preview("react")
            RunKind.JS_SCRIPT -> {
                output.clear(); output.show(); output.setTitle("OUTPUT — ${doc.file.name}")
                output.append("▶ Running ${doc.file.name} in the WebView JavaScript engine (browser environment, not Node.js)\n", OutKind.INFO)
                jsRunner = JsRunner(this, root, this).also { it.run(doc.file) }
            }
            RunKind.NATIVE_RUNTIME -> runNative(doc.file, root, lang.runtimeId ?: lang.id, lang.displayName)
            RunKind.NONE -> toast("${lang.displayName} files cannot be run")
        }
    }

    private fun runNative(file: File, root: File, runtimeId: String, langName: String) {
        output.clear(); output.show(); output.setTitle("OUTPUT — ${file.name}")
        val rt = runtimes.findFor(runtimeId)
        if (rt == null) {
            output.append(
                "No $langName runtime is installed.\nNothing was executed — this app never simulates execution.\n" +
                    "Install a compatible language package via ⋮ → Language packages.\n", OutKind.STDERR
            )
            Snackbar.make(findViewById(R.id.drawer), "No $langName runtime installed", Snackbar.LENGTH_LONG)
                .setAction("Packages") { startActivity(Intent(this, PackagesActivity::class.java)) }.show()
            return
        }
        try {
            val plan = runtimes.buildPlan(rt, runtimeId, file, root)
            output.append("▶ ${rt.manifest.name} ${rt.manifest.version}\n", OutKind.INFO)
            localRunner = LocalRunner(this).also { it.start(plan) }
        } catch (e: Exception) {
            output.append("Cannot run: ${e.message}\n", OutKind.STDERR)
        }
    }

    private fun stopRun() {
        localRunner?.stop(); localRunner = null
        jsRunner?.stop(); jsRunner = null
    }

    override fun onOutput(text: String, kind: OutKind) = output.append(text, kind)

    override fun onFinished(exitCode: Int?) {
        when (exitCode) {
            null -> output.append("■ Stopped\n", OutKind.INFO)
            0 -> output.append("✔ Finished (exit code 0)\n", OutKind.INFO)
            else -> output.append("✖ Exited with code $exitCode\n", OutKind.STDERR)
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun guarded(errorTitle: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Dialogs.message(this, errorTitle, e.message ?: e.toString())
        }
    }

    companion object {
        private const val FEATURE_STATUS = """IMPLEMENTED (compiled by you, untested by the author):
• Multi-file editor with tabs, undo/redo, find/replace (regex), line numbers
• Syntax highlighting: HTML, CSS, JS, JSX, JSON, Python, PHP, Java, C, C++
• Smart indentation, auto-close pairs, bracket matching, comment toggle
• Re-indent formatter (not a full pretty-printer)
• Keyword + document-word autocomplete
• Projects & file manager (create/rename/move/delete), SAF import/export
• HTML/CSS/JS preview and JS runner in WebView
• React preview (needs vendor JS files, see README)
• Language package manager + real process execution for installed packages

NOT BUNDLED: Python, PHP, Java, C and C++ runtimes. Without an installed package, Run reports this and executes nothing.

PLANNED: Node.js package, LSP-style semantic completion, TypeScript, Git, stdin input, debugger."""
    }
}
