package io.github.issisx.alienflow

/** Unknown-offset windows have authoritative LOCAL text/selection. Absolute
 * selection callbacks may lag indefinitely; they are not coordinates for this route.
 * A proposal replaces only a freshly observed suffix immediately before the cursor
 * and the current selection. No substring search or inferred absolute offset. */
internal class RelativeKeyboardOwnership(initial: EditorEvidence.KeyboardText,
                                         generation: Long, private val replaceSelection: Boolean = true) {
    private val initialSnapshot = EditorSnapshot(generation, initial.text,
        minOf(initial.start, initial.end), maxOf(initial.start, initial.end))
    private val owner = EditorOwnership(initialSnapshot, replaceSelection)
    var first = true; private set
    data class Edit(val deleteBefore: Int, val text: String, val confirmationLength: Int)
    val writingContext get() = owner.writingContext
    fun originalSelection() = owner.originalSelection()
    fun separateFromTypedWord(value: String) = owner.separateFromTypedWord(value)
    private fun before() = if (first) initialSnapshot else owner.proposedSnapshot(owner.owned)

    /** Align by the returned local selection, never by looking for repeated text.
     * All overlap (including the unchanged suffix) must agree. */
    fun matches(read: EditorEvidence.KeyboardText, proposed: String? = null): Boolean {
        if (!usable(read)) return false
        val expected = if (proposed == null) before() else owner.proposedSnapshot(proposed)
        val start = minOf(read.start, read.end); val end = maxOf(read.start, read.end)
        if (end - start != expected.end - expected.start) return false
        val shift = expected.start - start
        val left = maxOf(0, shift)
        val right = minOf(expected.text.length.toLong(), shift.toLong() + read.text.length).toInt()
        if (right < left || (right == left && (expected.text.isNotEmpty() || read.text.isNotEmpty()))) return false
        return expected.text.regionMatches(left, read.text, left - shift, right - left)
    }
    fun edit(read: EditorEvidence.KeyboardText, value: String): Edit? {
        if (!matches(read)) return null
        val before = before(); val replacement = owner.replacement(value, first)
        val keepSelected = first && !replaceSelection
        val delete = if (keepSelected) 0 else before.start - replacement.range.first
        if (delete < 0 || replacement.range.last + 1 != before.end || delete > minOf(read.start, read.end)) return null
        val selected = if (keepSelected) read.text.substring(minOf(read.start, read.end), maxOf(read.start, read.end)) else ""
        val text = selected + replacement.text
        return Edit(delete, text, text.length)
    }
    fun confirm(read: EditorEvidence.KeyboardText, value: String, changedLength: Int, composing: Boolean): Boolean {
        if (composing || !matches(read, value) || minOf(read.start, read.end) < changedLength) return false
        if (!owner.confirm(owner.proposedSnapshot(value), value)) return false
        first = false
        return true
    }
    fun deletionMatches(read: EditorEvidence.KeyboardText, deleted: Int): Boolean =
        deleted in 0..owner.owned.length && matches(read, owner.owned.dropLast(deleted))
    companion object {
        fun usable(read: EditorEvidence.KeyboardText) = read.offset >= -1 &&
            minOf(read.start, read.end) >= 0 && maxOf(read.start, read.end) <= read.text.length
    }
}
