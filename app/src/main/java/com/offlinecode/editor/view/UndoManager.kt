package com.offlinecode.editor.view

/** Per-document edit history. Consecutive typing/backspacing is merged into one step. */
class UndoManager {
    data class Edit(val start: Int, val before: String, val after: String, val time: Long)

    private val undoStack = ArrayList<Edit>()
    private val redoStack = ArrayList<Edit>()

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear(); redoStack.clear()
    }

    fun record(start: Int, before: String, after: String, now: Long = System.currentTimeMillis()) {
        if (before == after) return
        redoStack.clear()
        val last = undoStack.lastOrNull()
        if (last != null && now - last.time < 1000) {
            // Typing: contiguous single-line insertions
            if (last.before.isEmpty() && before.isEmpty() && start == last.start + last.after.length &&
                !after.contains('\n') && !last.after.contains('\n')
            ) {
                undoStack[undoStack.size - 1] = Edit(last.start, "", last.after + after, now); return
            }
            // Backspacing: contiguous deletions moving left
            if (last.after.isEmpty() && after.isEmpty() && start + before.length == last.start &&
                !before.contains('\n') && !last.before.contains('\n')
            ) {
                undoStack[undoStack.size - 1] = Edit(start, before + last.before, "", now); return
            }
        }
        undoStack.add(Edit(start, before, after, now))
        if (undoStack.size > 1000) undoStack.removeAt(0)
    }

    fun popUndo(): Edit? {
        if (undoStack.isEmpty()) return null
        val e = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(e)
        return e
    }

    fun popRedo(): Edit? {
        if (redoStack.isEmpty()) return null
        val e = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(e)
        return e
    }
}
