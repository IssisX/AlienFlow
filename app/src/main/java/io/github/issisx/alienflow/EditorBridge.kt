package io.github.issisx.alienflow

import android.text.Spanned
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.*

/** Sole editor writer. State/edits stay on Main; blocking keyboard reads run on IO. */
class EditorBridge(private val service: AlienAccessibilityService) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var node: AccessibilityNodeInfo? = null
    private var generation = 0L
    private var writeSequence = 0L
    private var ownership: EditorOwnership? = null
    private var first = true
    private var detachedSnapshot: EditorSnapshot? = null
    var lastError = ""; private set
    private val app get() = service.application as AlienApp

    private fun isProtected(node: AccessibilityNodeInfo): Boolean {
        fun password(input: Int): Boolean {
            val type = input and InputType.TYPE_MASK_CLASS
            val variation = input and InputType.TYPE_MASK_VARIATION
            return (type == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
                (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        }
        return node.isPassword || password(node.inputType) || password(service.inputMethod?.currentInputEditorInfo?.inputType ?: 0)
    }
    fun focused(): AccessibilityNodeInfo? = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        ?.takeIf { it.isEditable && !isProtected(it) }

    fun begin(): Boolean {
        generation++; writeSequence++; ownership = null; first = true; lastError = ""
        node = focused() ?: return fail("[AF-E01] Tap inside a text box first. Password fields are excluded")
        val snapshot = snapshotNode() ?: return false
        if (service.inputMethod?.currentInputConnection == null) {
            return fail("[AF-E04] Android has not attached the keyboard connection; tap the text box again")
        }
        if (snapshot.composing) return fail("[AF-E08] Finish the keyboard's composing word before dictating")
        ownership = EditorOwnership(snapshot)
        return ownership?.certain == true
    }

    /** Fast metadata validation for focus events/polling. It never reads the keyboard synchronously. */
    private fun snapshotNode(): EditorSnapshot? {
        if (!app.screenReady()) return rejected("[AF-E02] Unlock the screen before dictating")
        val target = node ?: return rejected("[AF-E01] Tap inside a text box first")
        if (!target.refresh()) return rejected("[AF-E03] The original text box is no longer available")
        val current = focused() ?: return rejected("[AF-E03] The text box lost focus; tap inside it again")
        if (current != target) return rejected("[AF-E03] Focus moved to a different text box")
        if (isProtected(target)) return rejected("[AF-E01] Password fields are excluded")
        val editorInfo = service.inputMethod?.currentInputEditorInfo
        if (editorInfo?.packageName != null && editorInfo.packageName != target.packageName?.toString()) {
            return rejected("[AF-E05] The keyboard is attached to another app; tap this text box again")
        }
        val text = target.text
        val showingHint = target.isShowingHintText
        val read = EditorEvidence.fromNode(generation,
            EditorEvidence.NodeText(text?.toString(), showingHint, target.textSelectionStart, target.textSelectionEnd),
            service.composing || (!showingHint && hasComposition(text)))
        val snapshot = read.snapshot ?: return rejected(read.error!!)
        if (snapshot.text.isEmpty() && !snapshot.composing) service.confirmEmptyComposition()
        return snapshot
    }
    private fun hasComposition(text: CharSequence?): Boolean = (text as? Spanned)?.let { spans ->
        spans.getSpans(0, spans.length, Any::class.java).any {
            spans.getSpanFlags(it) and Spanned.SPAN_COMPOSING != 0
        }
    } == true

    fun valid(): Boolean {
        val owner = ownership ?: return false
        val snapshot = snapshotNode() ?: return false
        return if (first) owner.canFirstWrite(snapshot) else owner.matches(snapshot)
    }

    fun write(value: String, completion: (Boolean) -> Unit) = writeOwned(value, false, completion)

    private fun writeOwned(value: String, restoring: Boolean, completion: (Boolean) -> Unit) {
        val owner = ownership ?: return completion(false)
        val writeGeneration = generation
        val sequence = ++writeSequence
        fun current(): Boolean = generation == writeGeneration && ownership === owner && sequence == writeSequence &&
            app.coordinator.service === service && (restoring || app.coordinator.active || app.coordinator.finishing)
        scope.launch {
            var success = false
            try {
                if (!current()) return@launch
                val before = snapshotNode()
                if (before == null || !(if (first) owner.canFirstWrite(before) else owner.matches(before))) {
                    owner.invalidate()
                    if (before != null) fail("[AF-E13] The text or cursor changed; automatic edits stopped")
                    return@launch
                }
                val connection = service.inputMethod?.currentInputConnection
                    ?: run { fail("[AF-E04] Android detached the keyboard connection"); return@launch }
                val surrounding = withContext(Dispatchers.IO) {
                    connection.getSurroundingText(100000, 100000, InputConnection.GET_TEXT_WITH_STYLES)
                }
                if (!current()) return@launch
                if (surrounding == null) {
                    fail("[AF-E06] The keyboard did not return its text; recognized words remain in AlienFlow")
                    owner.invalidate(); return@launch
                }
                val fresh = snapshotNode()
                if (fresh != before) {
                    fail("[AF-E13] The text box changed while checking it; no text was replaced")
                    owner.invalidate(); return@launch
                }
                val evidence = EditorEvidence.corroborate(before, EditorEvidence.KeyboardText(
                    surrounding.text.toString(), surrounding.selectionStart, surrounding.selectionEnd, surrounding.offset))
                if (evidence.snapshot == null || hasComposition(surrounding.text) || service.composing) {
                    fail(evidence.error ?: "[AF-E08] The keyboard is still composing a word")
                    owner.invalidate(); return@launch
                }
                val range = if (first) before.start until before.end else owner.range()
                connection.setSelection(range.first, range.last + 1)
                connection.commitText(value, 1, null)
                delay(100)
                if (!current()) return@launch
                val after = withContext(Dispatchers.IO) {
                    connection.getSurroundingText(100000, 100000, InputConnection.GET_TEXT_WITH_STYLES)
                }
                if (!current()) return@launch
                val afterNode = snapshotNode()
                val confirmation = if (after != null && afterNode != null && !hasComposition(after.text) && !service.composing) {
                    EditorEvidence.corroborate(afterNode, EditorEvidence.KeyboardText(
                        after.text.toString(), after.selectionStart, after.selectionEnd, after.offset))
                } else null
                val confirmed = confirmation?.snapshot
                success = confirmed != null && owner.confirm(confirmed, value)
                first = false
                if (!success) {
                    owner.invalidate()
                    fail(confirmation?.error ?: "[AF-E14] Insertion could not be confirmed; further edits stopped")
                }
            } catch (_: CancellationException) {
                if (generation == writeGeneration && ownership === owner) owner.invalidate()
            } catch (_: Exception) {
                if (generation == writeGeneration && ownership === owner) {
                    owner.invalidate(); fail("[AF-E15] Android rejected the editor operation; recognized words remain in AlienFlow")
                }
            } finally {
                completion(success)
            }
        }
    }
    fun cancel(completion: (Boolean) -> Unit) {
        val owner = ownership ?: return completion(false)
        if (first) { completion(true); return }
        if (!valid()) { owner.invalidate(); completion(false); return }
        writeOwned(owner.originalSelection(), true, completion)
    }
    fun detachForCapture() { invalidate(); detachedSnapshot = snapshotNode() }
    fun detachedTargetUnchanged() = detachedSnapshot?.let { it == snapshotNode() } == true
    fun invalidate() { generation++; writeSequence++; ownership?.invalidate() }
    private fun rejected(reason: String): EditorSnapshot? { fail(reason); return null }
    private fun fail(reason: String): Boolean {
        lastError = reason
        app.crashJournal.editorFailure(reason)
        return false
    }
}
