package io.github.issisx.alienflow

import kotlinx.coroutines.delay

/** One editor mutation per proposal. Preserve captured user anchors; never resend. */
internal object NodeEditorWriter {
    /** Only a bridge already bound to an unambiguous empty field may use this.
     * Captured user prefix/suffix must be retained byte-for-byte. Revalidate right
     * before dispatch and never resend a mutation, even if confirmation changes.
     */
    suspend fun writeMerged(before: EditorSnapshot, expected: EditorSnapshot, owner: EditorOwnership,
                           first: Boolean, emptySession: Boolean, current: () -> Boolean, read: () -> EditorSnapshot?,
                           setText: (String) -> Boolean, setSelection: ((Int) -> Boolean)?,
                           dispatched: () -> Unit): EditorWriteResult {
        if (!emptySession) return EditorWriteResult.FAILED
        if (!(if (first) owner.canFirstWrite(before) else owner.matches(before)) ||
            !owner.preservesAnchors(expected)) return EditorWriteResult.RETRY
        if (before.composing) return EditorWriteResult.RETRY
        if (expected.end != expected.text.length && setSelection == null) return EditorWriteResult.FAILED
        if (!current()) return EditorWriteResult.USER_EDIT
        if (read() != before) return EditorWriteResult.RETRY
        if (!setText(expected.text)) return EditorWriteResult.FAILED
        dispatched()
        if (expected.end != expected.text.length) {
            val sample = read()
            if (!current()) return EditorWriteResult.USER_EDIT
            if (sample == null || sample.text != expected.text || sample.start != expected.text.length ||
                sample.end != expected.text.length || sample.composing) return EditorWriteResult.RETRY
            if (setSelection?.invoke(expected.end) != true) return EditorWriteResult.FAILED
        }
        var pendingCursor = false
        repeat(32) {
            if (!current()) return EditorWriteResult.USER_EDIT
            val sample = read()
            if (sample == expected) return EditorWriteResult.APPLIED
            if (sample?.composing == true) return EditorWriteResult.RETRY
            pendingCursor = sample != null && sample.generation == expected.generation &&
                sample.text == expected.text && sample.start == before.end && sample.end == before.end
            if (sample != null && sample != before && !pendingCursor) return EditorWriteResult.RETRY
            delay(25)
        }
        return if (pendingCursor) EditorWriteResult.RETRY else EditorWriteResult.FAILED
    }

}
