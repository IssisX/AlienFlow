package io.github.issisx.alienflow

/** Field access is optional for live display; a failed editor must not end audio capture. */
object InlineEditorContinuity {
    enum class Action { KEEP, DETACH }
    fun action(hasEditor: Boolean, writePending: Boolean, editorValid: Boolean): Action =
        if (hasEditor && !writePending && !editorValid) Action.DETACH else Action.KEEP
}
