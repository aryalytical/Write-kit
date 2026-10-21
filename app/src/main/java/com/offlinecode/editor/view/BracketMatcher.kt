package com.offlinecode.editor.view

object BracketMatcher {
    private const val OPEN = "([{"
    private const val CLOSE = ")]}"

    /**
     * Finds the bracket pair adjacent to [pos] (char before, then char after).
     * Returns (openIndex, closeIndex) or null. Scans at most [limit] chars.
     * Note: does not skip brackets inside strings/comments.
     */
    fun find(t: CharSequence, pos: Int, limit: Int = 30000): Pair<Int, Int>? {
        if (pos > 0 && pos <= t.length) matchAt(t, pos - 1, limit)?.let { return it }
        if (pos in 0 until t.length) matchAt(t, pos, limit)?.let { return it }
        return null
    }

    private fun matchAt(t: CharSequence, i: Int, limit: Int): Pair<Int, Int>? {
        val c = t[i]
        val oi = OPEN.indexOf(c)
        if (oi >= 0) {
            var depth = 0
            val end = minOf(t.length, i + limit)
            var j = i
            while (j < end) {
                val ch = t[j]
                if (ch == c) depth++
                else if (ch == CLOSE[oi]) {
                    depth--
                    if (depth == 0) return i to j
                }
                j++
            }
            return null
        }
        val ci = CLOSE.indexOf(c)
        if (ci >= 0) {
            var depth = 0
            val start = maxOf(0, i - limit)
            var j = i
            while (j >= start) {
                val ch = t[j]
                if (ch == c) depth++
                else if (ch == OPEN[ci]) {
                    depth--
                    if (depth == 0) return j to i
                }
                j--
            }
        }
        return null
    }
}
