package org.hedgedoc.android.data

/**
 * Task list checkboxes in the markdown source.
 *
 * The reader renders with Markwon, which numbers task items in document order and skips fenced
 * code. Counting the same way here keeps the index the reader hands back pointing at the line the
 * reader actually drew.
 */
object MarkdownTasks {
    private val TASK = Regex("^(\\s*(?:[-*+]|\\d+[.)])\\s+\\[)([ xX])(\\].*)")
    private val FENCE = Regex("^\\s*(```|~~~)")

    fun count(markdown: String): Int {
        var found = 0
        forEachTask(markdown) { _, _ -> found++ }
        return found
    }

    fun isChecked(markdown: String, index: Int): Boolean {
        var checked = false
        forEachTask(markdown) { ordinal, match ->
            if (ordinal == index) checked = match.groupValues[2] != " "
        }
        return checked
    }

    /** Flips the [index]th checkbox. Null when there is no such checkbox. */
    fun toggle(markdown: String, index: Int): String? {
        if (index < 0) return null
        val lines = markdown.split("\n")
        val out = ArrayList<String>(lines.size)
        var ordinal = 0
        var fenced = false
        var changed = false
        for (line in lines) {
            if (FENCE.containsMatchIn(line)) fenced = !fenced
            val match = if (fenced) null else TASK.find(line)
            if (match == null) {
                out.add(line)
                continue
            }
            if (ordinal == index) {
                val mark = if (match.groupValues[2] == " ") "x" else " "
                out.add(match.groupValues[1] + mark + match.groupValues[3])
                changed = true
            } else {
                out.add(line)
            }
            ordinal++
        }
        return if (changed) out.joinToString("\n") else null
    }

    private inline fun forEachTask(markdown: String, action: (Int, MatchResult) -> Unit) {
        var ordinal = 0
        var fenced = false
        for (line in markdown.split("\n")) {
            if (FENCE.containsMatchIn(line)) fenced = !fenced
            if (fenced) continue
            val match = TASK.find(line) ?: continue
            action(ordinal, match)
            ordinal++
        }
    }
}
