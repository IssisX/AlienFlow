package io.github.issisx.alienflow

import android.text.Spanned
import android.text.InputType
import android.os.Bundle
import android.view.inputmethod.EditorInfo
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
    var isTerminal = false; private set
    private var terminalInsertion: TerminalInsertion? = null
    private var terminalFieldId = 0
    private var nodeActions = false
    var lastError = ""; private set
    private val app get() = service.application as AlienApp

    private fun isProtected(node: AccessibilityNodeInfo): Boolean {
        fun password(input: Int): Boolean {
            val type = input and InputType.TYPE_MASK_CLASS
            val variation = input and InputType.TYPE_MASK_VARIATION
            return (type == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
                (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        }
        val info = service.inputMethod?.currentInputEditorInfo
        return node.isPassword || password(node.inputType) ||
            (info?.packageName == node.packageName?.toString() && password(info?.inputType ?: 0))
    }
    fun focused(): AccessibilityNodeInfo? = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        ?.takeIf { (it.isEditable || supportsNodeActions(it) || terminalNode(it)) && !isProtected(it) }

    private fun supportsNodeActions(target: AccessibilityNodeInfo): Boolean {
        val actions = target.actionList.map { it.id }
        return AccessibilityNodeInfo.ACTION_SET_TEXT in actions && AccessibilityNodeInfo.ACTION_SET_SELECTION in actions
    }
    /** A same-editor IME restart is a refresh, not proof that focus or text ownership moved. */
    fun inputStarted(info: EditorInfo, restarting: Boolean) {
        val target = node
        if (!restarting || target == null || !target.refresh() || focused() != target ||
            info.packageName != target.packageName?.toString() || isProtected(target)) invalidate()
    }

    private fun terminalNode(target: AccessibilityNodeInfo): Boolean = TerminalDictation.supported(
        target.className?.toString(), target.packageName?.toString(),
        service.inputMethod?.currentInputEditorInfo?.packageName,
        service.inputMethod?.currentInputEditorInfo?.inputType ?: -1)

    private fun terminalValid(): Boolean {
        val target = node ?: return false
        return app.screenReady() && target.refresh() && focused() == target && terminalNode(target) &&
            service.inputMethod?.currentInputEditorInfo?.fieldId == terminalFieldId &&
            service.inputMethod?.currentInputConnection != null
    }

    fun begin(): Boolean {
        generation++; writeSequence++; ownership = null; first = true; lastError = ""
        isTerminal = false; terminalInsertion = null; nodeActions = false
        node = focused() ?: return fail("[AF-E01] Tap inside a text box first. Password fields are excluded")
        if (terminalNode(node!!)) {
            if (service.inputMethod?.currentInputConnection == null)
                return fail("[AF-E04] Tap the Termux terminal to attach its keyboard")
            isTerminal = true
            terminalFieldId = service.inputMethod?.currentInputEditorInfo?.fieldId ?: 0
            terminalInsertion = TerminalInsertion()
            return true
        }
        nodeActions = supportsNodeActions(node!!)
        val snapshot = snapshotNode() ?: return false
        if (!nodeActions && service.inputMethod?.currentInputConnection == null) {
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
        if (!nodeActions && editorInfo?.packageName != null && editorInfo.packageName != target.packageName?.toString()) {
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
        if (isTerminal) return terminalInsertion != null && terminalValid()
        val owner = ownership ?: return false
        val snapshot = snapshotNode() ?: return false
        return if (first) owner.canFirstWrite(snapshot) else owner.matches(snapshot)
    }

    fun write(value: String, completion: (EditorWriteResult) -> Unit) {
        if (!isTerminal) { writeOwned(value, false, completion); return }
        if (!app.coordinator.finishing || app.coordinator.active) { completion(EditorWriteResult.FAILED); return }
        try {
            val connection = service.inputMethod?.currentInputConnection
            val ok = terminalInsertion?.send(value, connection != null && terminalValid()) {
                connection!!.commitText(it, 1, null)
            } == true
            if (!ok) fail("[AF-T01] Termux focus changed; text is saved in AlienFlow")
            completion(if (ok) EditorWriteResult.APPLIED else EditorWriteResult.FAILED)
        } catch (_: Exception) {
            fail("[AF-T02] Termux input could not be confirmed; text is saved in AlienFlow")
            completion(EditorWriteResult.FAILED)
        }
    }

    private fun writeOwned(value: String, restoring: Boolean, completion: (EditorWriteResult) -> Unit) {
        val owner = ownership ?: return completion(EditorWriteResult.USER_EDIT)
        lastError = ""
        val writeGeneration = generation
        val sequence = ++writeSequence
        fun current(): Boolean = generation == writeGeneration && ownership === owner && sequence == writeSequence &&
            app.coordinator.service === service && (restoring || app.coordinator.active || app.coordinator.finishing) &&
            node?.refresh() == true && focused() == node
        scope.launch {
            var outcome = EditorWriteResult.USER_EDIT
            var phase = "before"
            var observation = EditorSynchronization.Reading(null, null)
            try {
                if (!current()) return@launch
                val before = snapshotNode() ?: run { outcome = EditorWriteResult.FAILED; return@launch }
                if (!(if (first) owner.canFirstWrite(before) else owner.matches(before))) {
                    owner.invalidate()
                    return@launch // User typing/cursor movement is an interaction, not a failure.
                }
                if (nodeActions) {
                    phase = "node"
                    val expected = owner.proposedSnapshot(value)
                    outcome = NodeEditorWriter.write(before, expected, ::current,
                        read = { snapshotNode().also { observation = EditorSynchronization.Reading(it, null, it?.composing == true) } },
                        setText = { text -> current() && node!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                            Bundle().apply { putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }) },
                        setSelection = { cursor -> current() && node!!.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,
                            Bundle().apply { putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor); putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor) }) })
                    if (outcome == EditorWriteResult.APPLIED) {
                        val confirmed = observation.node
                        if (confirmed == null || !owner.confirm(confirmed, value)) outcome = EditorWriteResult.FAILED
                        else first = false
                    }
                    return@launch
                }
                val connection = service.inputMethod?.currentInputConnection
                    ?: run { fail("[AF-E04] Android detached the keyboard connection"); outcome = EditorWriteResult.FAILED; return@launch }
                suspend fun read(): EditorSynchronization.Reading {
                    val surrounding = withContext(Dispatchers.IO) {
                        connection.getSurroundingText(100000, 100000, InputConnection.GET_TEXT_WITH_STYLES)
                    }
                    val keyboard = surrounding?.let { EditorEvidence.KeyboardText(
                        it.text.toString(), it.selectionStart, it.selectionEnd, it.offset) }
                    observation = EditorSynchronization.Reading(snapshotNode(), keyboard,
                        service.composing || hasComposition(surrounding?.text))
                    return observation
                }
                outcome = EditorSynchronization.await(before, before, ::current, ::read)
                if (outcome != EditorWriteResult.APPLIED) return@launch
                val range = if (first) before.start until before.end else owner.range()
                connection.setSelection(range.first, range.last + 1)
                connection.commitText(value, 1, null)
                phase = "after"
                // A single 100ms sample can read a new IME buffer and an old browser node.
                // Only exact proposed text AND cursor in both sources authorize another write.
                outcome = EditorSynchronization.await(before, owner.proposedSnapshot(value), ::current, ::read)
                if (outcome == EditorWriteResult.APPLIED) {
                    val confirmed = observation.node
                    if (confirmed == null || !owner.confirm(confirmed, value)) outcome = EditorWriteResult.FAILED
                    else first = false
                }
            } catch (_: CancellationException) {
                outcome = EditorWriteResult.USER_EDIT
            } catch (_: Exception) {
                fail("[AF-E15] Android rejected the editor operation; words are saved in AlienFlow")
                outcome = EditorWriteResult.FAILED
            } finally {
                if (outcome != EditorWriteResult.APPLIED) owner.invalidate()
                if (outcome == EditorWriteResult.FAILED && current()) {
                    app.crashJournal.editorObservation(phase, observation)
                    val structuralError = observation.node == null && Regex("^\\[AF-E(?:04|05|07|15)\\]").containsMatchIn(lastError)
                    if (!structuralError && !lastError.startsWith("[AF-E15]"))
                        fail(if (phase == "node") "[AF-E14] Accessibility editor update unconfirmed; see Diagnostics"
                            else if (phase == "before") "[AF-E10] Editor sync timed out; see Diagnostics"
                            else "[AF-E14] Insertion sync timed out; see Diagnostics")
                }
                completion(outcome)
            }
        }
    }
    fun cancel(completion: (Boolean) -> Unit) {
        if (isTerminal) { completion(terminalInsertion?.sent != true); return }
        val owner = ownership ?: return completion(false)
        if (first) { completion(true); return }
        if (!valid()) { owner.invalidate(); completion(false); return }
        writeOwned(owner.originalSelection(), true) { completion(it == EditorWriteResult.APPLIED) }
    }
    fun detachForCapture() { invalidate(); detachedSnapshot = snapshotNode() }
    fun detachedTargetUnchanged() = detachedSnapshot?.let { it == snapshotNode() } == true
    fun invalidate() { generation++; writeSequence++; ownership?.invalidate(); terminalInsertion = null; isTerminal = false }
    private fun rejected(reason: String): EditorSnapshot? { fail(reason); return null }
    private fun fail(reason: String): Boolean {
        lastError = reason
        app.crashJournal.editorFailure(reason)
        return false
    }
}
