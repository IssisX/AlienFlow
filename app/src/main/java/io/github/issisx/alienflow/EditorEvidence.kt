package io.github.issisx.alienflow

/** Android accessibility labels and keyboard contents are different representations. */
object EditorEvidence {
    data class NodeText(
        val text: String?,
        val showingHint: Boolean,
        val start: Int,
        val end: Int,
    )
    data class KeyboardText(val text: String, val start: Int, val end: Int, val offset: Int)
    data class Read(val snapshot: EditorSnapshot? = null, val error: String? = null)

    /** Candidate ranges are half-open. A zero-length 0..0 range is not composition. */
    fun hasComposingRange(start: Int, end: Int): Boolean = start >= 0 && end > start

    fun fromNode(
        generation: Long,
        node: NodeText,
        composing: Boolean,
    ): Read {
        // A hint is an invitation to enter text, not existing user content.
        val text = if (node.showingHint) "" else node.text.orEmpty()
        val emptyCursor = text.isEmpty() && node.start in -1..0 && node.end in -1..0
        val start = if (emptyCursor) 0 else minOf(node.start, node.end)
        val end = if (emptyCursor) 0 else maxOf(node.start, node.end)
        if (start < 0 || end > text.length) {
            return Read(error = "[AF-E07] The text box has not provided a usable cursor. Tap inside it again")
        }
        // Unknown composition is not proof of active composition. The bridge chooses
        // range edits or a wholly-owned empty-field route before any mutation.
        return Read(EditorSnapshot(generation, text, start, end,
            composing = !emptyCursor && composing))
    }

    /** An explicit Android offset anchors a window, including the entire mutation range.
     * Compare all shared characters. IME-only text beyond the node is outside the
     * permitted edit and remains untouched by ranged commits. Never trim it or
     * infer its contents. Unknown offsets still require exact full text.
     */
    fun corroborate(node: EditorSnapshot, keyboard: KeyboardText,
                    required: IntRange = node.start until node.end): Read {
        val start = minOf(keyboard.start, keyboard.end)
        val end = maxOf(keyboard.start, keyboard.end)
        val offset = if (keyboard.offset == -1 && keyboard.text == node.text) 0 else keyboard.offset
        val windowEnd = offset.toLong() + keyboard.text.length
        val sharedEnd = minOf(windowEnd, node.text.length.toLong())
        if (offset < 0 || offset > node.text.length ||
            (sharedEnd <= offset && (node.text.isNotEmpty() || keyboard.text.isNotEmpty())) ||
            required.first < offset || required.last.toLong() + 1 > sharedEnd) {
            return Read(error = "[AF-E09] The keyboard window does not cover this edit; no text was replaced")
        }
        if (start < 0 || end > keyboard.text.length || offset.toLong() + start != node.start.toLong() ||
            offset.toLong() + end != node.end.toLong()) {
            return Read(error = "[AF-E11] Keyboard and text-box cursor positions disagree; tap the field again")
        }
        if (!node.text.regionMatches(offset, keyboard.text, 0, (sharedEnd - offset).toInt())) {
            return Read(error = "[AF-E10] Keyboard and text-box contents disagree; no text was replaced")
        }
        return Read(node)
    }

    /** Explicit hint metadata + independent empty editor contents, never a phrase blacklist. */
    fun corroborateWithHint(node: EditorSnapshot, hint: String?, keyboard: KeyboardText): Read {
        if (!node.composing && !hint.isNullOrEmpty() && node.text == hint && keyboard.text.isEmpty() &&
            keyboard.start == 0 && keyboard.end == 0 && keyboard.offset in setOf(0, -1)) {
            return corroborate(EditorSnapshot(node.generation, "", 0, 0), keyboard)
        }
        return corroborate(node, keyboard)
    }
}
