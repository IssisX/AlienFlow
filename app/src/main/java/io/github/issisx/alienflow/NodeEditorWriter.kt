package io.github.issisx.alienflow

import kotlinx.coroutines.delay

/** All text must belong to this dictation. One text mutation, no selection command or transport fallback. */
internal object NodeEditorWriter {
    suspend fun write(before: EditorSnapshot, expected: EditorSnapshot, wholeFieldOwned: Boolean,
                      current: () -> Boolean, read: () -> EditorSnapshot?, setText: (String) -> Boolean): EditorWriteResult {
        if (!wholeFieldOwned || before.start != before.end || before.end != before.text.length ||
            expected.start != expected.end || expected.end != expected.text.length) return EditorWriteResult.FAILED
        if (!current() || read() != before) return EditorWriteResult.USER_EDIT
        if (!setText(expected.text)) return EditorWriteResult.FAILED
        var pendingCursor = false
        repeat(32) {
            if (!current()) return EditorWriteResult.USER_EDIT
            val sample = read()
            if (sample == expected) return EditorWriteResult.APPLIED
            if (sample?.composing == true) return EditorWriteResult.USER_EDIT
            pendingCursor = sample != null && sample.generation == expected.generation &&
                sample.text == expected.text && sample.start == before.end && sample.end == before.end
            if (sample != null && sample != before && !pendingCursor) return EditorWriteResult.USER_EDIT
            delay(25)
        }
        // A user may leave their cursor at the old position; never force it forward.
        return if (pendingCursor) EditorWriteResult.USER_EDIT else EditorWriteResult.FAILED
    }
}
