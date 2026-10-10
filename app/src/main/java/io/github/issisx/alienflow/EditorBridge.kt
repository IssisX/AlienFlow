package io.github.issisx.alienflow

import android.accessibilityservice.AccessibilityService
import android.text.Spanned
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo

/** The only component allowed to mutate editor text. A write is never blindly retried. */
class EditorBridge(private val service: AlienAccessibilityService) {
    private var node: AccessibilityNodeInfo? = null
    private var generation = 0L
    private var ownership: EditorOwnership? = null
    private var first = true
    private var detachedSnapshot: EditorSnapshot? = null
    var lastError = ""; private set
    private fun isProtected(node: AccessibilityNodeInfo): Boolean {
        fun password(input: Int): Boolean {
            val type = input and InputType.TYPE_MASK_CLASS
            val variation = input and InputType.TYPE_MASK_VARIATION
            return (type == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
                (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        }
        return node.isPassword || password(node.inputType) || password(service.inputMethod?.currentInputEditorInfo?.inputType ?: 0)
    }
    fun focused(): AccessibilityNodeInfo? = service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable && !isProtected(it) }
    fun begin(): Boolean {
        generation++; ownership = null; first = true
        node = focused() ?: return fail("Focus a regular editable field; protected inputs are excluded")
        val snapshot = snapshot() ?: return fail("This editor does not expose reliable text and selection")
        ownership = EditorOwnership(snapshot)
        return if (snapshot.composing) fail("Finish the keyboard's composing word before dictating") else true
    }
    private fun snapshot(): EditorSnapshot? {
        if (!(service.application as AlienApp).screenReady()) return null
        val target = node ?: return null
        if (!target.refresh()) return null
        val current = focused() ?: return null
        if (current != target) return null
        // Protected status is checked before requesting any text.
        if (isProtected(target)) return null
        val connection = service.inputMethod?.currentInputConnection ?: return null
        val surrounding = connection.getSurroundingText(100000, 100000, 1) ?: return null
        if (surrounding.offset != 0) return null // cannot prove the complete anchors
        val text = surrounding.text ?: return null
        if (text.toString() != target.text?.toString().orEmpty()) return null
        if (text.isEmpty() && surrounding.selectionStart == 0 && surrounding.selectionEnd == 0) service.confirmEmptyComposition()
        val composing = service.composing || !service.compositionKnown || (text as? Spanned)?.let { spans -> spans.getSpans(0, spans.length, Any::class.java).any { spans.getSpanFlags(it) and Spanned.SPAN_COMPOSING != 0 } } ?: false
        if (surrounding.selectionStart != target.textSelectionStart || surrounding.selectionEnd != target.textSelectionEnd) return null
        return EditorSnapshot(generation, text.toString(), surrounding.selectionStart, surrounding.selectionEnd, composing)
    }
    fun valid(): Boolean {
        val owner = ownership ?: return false
        val snapshot = snapshot() ?: return false
        return if (first) owner.canFirstWrite(snapshot) else owner.matches(snapshot)
    }
    fun write(value: String, completion: (Boolean) -> Unit) {
        val owner = ownership ?: return completion(false)
        val before = snapshot()
        if (before == null || !(if (first) owner.canFirstWrite(before) else owner.matches(before))) { owner.invalidate(); fail("The field changed; automatic edits stopped"); completion(false); return }
        val connection = service.inputMethod?.currentInputConnection ?: run { completion(false); return }
        val range = if (first) {
            before.start until before.end
        } else owner.range()
        val writeGeneration = generation
        try {
            connection.setSelection(range.first, range.last + 1)
            connection.commitText(value, 1, null)
            // IPC editor writes may complete asynchronously. Read once after dispatch, never retry the mutation.
            service.handler.postDelayed({
                if (generation != writeGeneration || ownership !== owner) { completion(false); return@postDelayed }
                val evidence = snapshot()
                val ok = evidence != null && owner.confirm(evidence, value)
                first = false
                if (!ok) { owner.invalidate(); fail("Insertion could not be confirmed; result preserved. No further edits") }
                completion(ok)
            }, 100)
        } catch (_: Exception) { owner.invalidate(); fail("Editor rejected insertion; result preserved"); completion(false) }
    }
    fun cancel(completion: (Boolean) -> Unit) {
        val owner = ownership ?: return completion(false)
        if (first) { completion(true); return }
        if (!valid()) { owner.invalidate(); completion(false); return }
        write(owner.originalSelection(), completion)
    }
    fun detachForCapture() { invalidate(); detachedSnapshot = snapshot() }
    fun detachedTargetUnchanged() = detachedSnapshot?.let { it == snapshot() } == true
    fun invalidate() { generation++; ownership?.invalidate() }
    private fun fail(reason: String): Boolean { lastError = reason; return false }
}
