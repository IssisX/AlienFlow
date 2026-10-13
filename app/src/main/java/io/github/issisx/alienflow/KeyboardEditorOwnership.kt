package io.github.issisx.alienflow

/** Keyboard coordinates are independent of accessibility presentation text. */
internal data class KeyboardWindow(val text: String, val start: Int, val end: Int, val offset: Int,
                                   val composing: Boolean = false) {
    val absoluteStart: Int get() = offset + minOf(start, end)
    val absoluteEnd: Int get() = offset + maxOf(start, end)
    companion object {
        fun read(text: EditorEvidence.KeyboardText, composing: Boolean): KeyboardWindow? {
            if (text.offset < -1) return null
            val start = minOf(text.start, text.end); val end = maxOf(text.start, text.end)
            if (start < 0 || end > text.text.length) return null
            val offset = if (text.offset >= 0) text.offset else {
                val selection = text.selection ?: return null
                val absoluteStart = minOf(selection.start, selection.end)
                val absoluteEnd = maxOf(selection.start, selection.end)
                if (absoluteStart.toLong() - start != absoluteEnd.toLong() - end) return null
                absoluteStart - start
            }
            if (offset < 0 || offset.toLong() + text.text.length > Int.MAX_VALUE) return null
            return KeyboardWindow(text.text, start, end, offset, composing)
        }
    }
}

/** Only the observed mutation range is writable. A bounded read need not contain
 * the entire draft or an unchanged speech prefix. Every shared character is checked;
 * a revision/cancel requires coverage of every character it would replace. */
internal class KeyboardEditorOwnership(initial: KeyboardWindow, generation: Long, replaceSelection: Boolean = true) {
    private val base = initial.offset
    private val initialSnapshot = EditorSnapshot(generation, initial.text, initial.start, initial.end)
    private val owner = EditorOwnership(initialSnapshot, replaceSelection)
    var first = true; private set
    val owned: String get() = owner.owned
    fun originalSelection() = owner.originalSelection()
    fun separateFromTypedWord(value: String) = owner.separateFromTypedWord(value)
    fun replacement(value: String): EditorReplacement {
        val local = owner.replacement(value, first)
        return local.copy(range = (base + local.range.first) until (base + local.range.last + 1))
    }
    private fun before() = if (first) initialSnapshot else owner.proposedSnapshot(owner.owned)
    fun matches(window: KeyboardWindow, proposed: String? = null): Boolean {
        val expected = if (proposed == null) before() else owner.proposedSnapshot(proposed)
        if (window.absoluteStart != base + expected.start || window.absoluteEnd != base + expected.end) return false
        val left = maxOf(base, window.offset)
        val right = minOf(base.toLong() + expected.text.length, window.offset.toLong() + window.text.length).toInt()
        if (right < left) return false
        return expected.text.regionMatches(left - base, window.text, left - window.offset, right - left)
    }
    fun covers(window: KeyboardWindow, range: IntRange) =
        range.first >= window.offset && range.last.toLong() + 1 <= window.offset.toLong() + window.text.length
    fun proposedRange(value: String): IntRange =
        replacement(value).range.first until (base + owner.proposedSnapshot(value).end)
    fun proposedCursor(value: String) = base + owner.proposedSnapshot(value).end
    fun confirm(window: KeyboardWindow, value: String): Boolean {
        if (window.composing || !matches(window, value) || !covers(window, proposedRange(value))) return false
        if (!owner.confirm(owner.proposedSnapshot(value), value)) return false
        first = false
        return true
    }
}
