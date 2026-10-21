package com.offlinecode.editor.project

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.offlinecode.editor.R
import java.io.File

class ExplorerAdapter(
    private val onOpen: (File) -> Unit,
    private val onMenu: (File, View) -> Unit
) : RecyclerView.Adapter<ExplorerAdapter.VH>() {

    private class Row(val file: File, val depth: Int)

    private var root: File? = null
    private val expanded = HashSet<String>()
    private var rows: List<Row> = emptyList()
    private var activePath: String? = null
    private var textColor = 0

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
    }

    fun setTextColor(c: Int) {
        textColor = c
        notifyDataSetChanged()
    }

    fun setRoot(dir: File?) {
        root = dir
        expanded.clear()
        rebuild()
    }

    fun setActive(file: File?) {
        activePath = file?.absolutePath
        notifyDataSetChanged()
    }

    fun expand(dir: File) {
        expanded.add(dir.absolutePath)
        rebuild()
    }

    fun rebuild() {
        val out = ArrayList<Row>()
        fun walk(dir: File, depth: Int) {
            val kids = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
            for (k in kids) {
                out.add(Row(k, depth))
                if (k.isDirectory && expanded.contains(k.absolutePath)) walk(k, depth + 1)
            }
        }
        root?.let { walk(it, 0) }
        rows = out
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_explorer, parent, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val row = rows[position]
        val f = row.file
        val d = h.itemView.resources.displayMetrics.density
        h.itemView.setPadding(((10 + row.depth * 14) * d).toInt(), 0, (8 * d).toInt(), 0)
        h.name.text = f.name
        if (textColor != 0) {
            h.name.setTextColor(textColor)
            h.icon.setColorFilter(textColor)
        }
        h.icon.setImageResource(
            when {
                f.isDirectory && expanded.contains(f.absolutePath) -> R.drawable.ic_expand_more
                f.isDirectory -> R.drawable.ic_chevron_right
                else -> R.drawable.ic_file
            }
        )
        h.name.setTypeface(null, if (f.isDirectory) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        h.itemView.alpha = if (f.absolutePath == activePath) 1f else 0.85f
        h.itemView.setOnClickListener {
            if (f.isDirectory) {
                if (!expanded.add(f.absolutePath)) expanded.remove(f.absolutePath)
                rebuild()
            } else onOpen(f)
        }
        h.itemView.setOnLongClickListener {
            onMenu(f, h.itemView)
            true
        }
    }
}
