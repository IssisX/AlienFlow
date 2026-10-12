package io.github.issisx.alienflow

import kotlinx.coroutines.delay

/** One mutation route, selected before dispatch. Never retry text or switch transports after dispatch. */
internal object NodeEditorWriter {
    suspend fun write(before: EditorSnapshot, expected: EditorSnapshot, wholeFieldOwned: Boolean, current: () -> Boolean,
                      read: () -> EditorSnapshot?, setText: (String) -> Boolean,
                      setSelection: (Int) -> Boolean): EditorWriteResult {
        if (!wholeFieldOwned || before.start != before.end || before.end != before.text.length ||
            expected.start != expected.end || expected.end != expected.text.length) return EditorWriteResult.FAILED
        if (!current() || read() != before) return EditorWriteResult.USER_EDIT
        if (!setText(expected.text)) return EditorWriteResult.FAILED
        var intermediate: EditorSnapshot? = null
        var attempts = 0
        while (attempts++ < 32) {
            if (!current()) return EditorWriteResult.USER_EDIT
            val sample = read()
            if (sample?.composing == true) return EditorWriteResult.USER_EDIT
            if (sample == expected) return EditorWriteResult.APPLIED
            if (sample != null && sample.text == expected.text) {
                // SET_TEXT normally places the cursor at the end. Only that known
                // intermediate cursor may be moved to the end of our owned span.
                if (sample.start != sample.text.length || sample.end != sample.start) return EditorWriteResult.USER_EDIT
                intermediate = sample
                break
            }
            if (sample != null && sample != before) return EditorWriteResult.USER_EDIT
            delay(25)
        }
        val settled = intermediate ?: return EditorWriteResult.FAILED
        if (!current() || read() != settled) return EditorWriteResult.USER_EDIT
        if (!setSelection(expected.start)) return EditorWriteResult.FAILED
        repeat(32) {
            if (!current()) return EditorWriteResult.USER_EDIT
            val sample = read()
            if (sample == expected) return EditorWriteResult.APPLIED
            if (sample != null && sample != settled) return EditorWriteResult.USER_EDIT
            delay(25)
        }
        return EditorWriteResult.FAILED
    }
}
