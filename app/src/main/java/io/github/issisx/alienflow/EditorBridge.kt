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
    private var keyboardOwnership: KeyboardEditorOwnership? = null
    private var relativeOwnership: RelativeKeyboardOwnership? = null
    private var inputEpoch = 0L
    private var keyboardFieldId = 0
    private var bindingSelection: EditorEvidence.Selection? = null
    private var bindingPresentation: EditorEvidence.NodeText? = null
    private var first = true
    private var detachedSnapshot: EditorSnapshot? = null
    var isTerminal = false; private set
    private var terminalInsertion: TerminalInsertion? = null
    private var terminalFieldId = 0
    private var nodeActions = false
    private val continuation = SpeechContinuation()
    private var userInteracted = false
    private var bound = false
    val hasUserInteraction: Boolean get() = userInteracted
    val bindingInputEpoch: Long get() = inputEpoch
    internal val writingContext: WritingContext get() = if (!sameTarget() || isTerminal) WritingContext()
        else relativeOwnership?.writingContext ?: keyboardOwnership?.writingContext ?: ownership?.writingContext ?: WritingContext()
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
    fun focused(): AccessibilityNodeInfo? {
        val direct = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val found = EditorFocusDiscovery.resolve(direct,
            { service.windows }, { (it.isEditable || supportsNodeActions(it) || terminalNode(it)) && !isProtected(it) }, ::isProtected)
        (service.application as? AlienApp)?.crashJournal?.editorFocus(found)
        (service.application as? AlienApp)?.crashJournal?.inputAvailability(found, direct,
            service.inputMethod?.currentInputEditorInfo, service.inputMethod?.currentInputStarted == true,
            service.inputMethod?.currentInputConnection != null)
        return found.node
    }

    private fun supportsNodeActions(target: AccessibilityNodeInfo): Boolean {
        val actions = target.actionList.map { it.id }
        // Empty editors commonly omit SET_SELECTION: there is nothing to select.
        // SET_TEXT already moves the cursor to its end. Nonempty drafts use IME ranges.
        return AccessibilityNodeInfo.ACTION_SET_TEXT in actions
    }
    /** A same-editor IME restart is a refresh, not proof that focus or text ownership moved. */
    fun inputStarted(info: EditorInfo, restarting: Boolean) {
        val target = node
        if (restarting && bound && target != null && info.packageName == target.packageName?.toString() &&
            (nodeActions || info.fieldId == if (isTerminal) terminalFieldId else keyboardFieldId)) {
            inputEpoch = service.inputEpoch
            writeSequence++ // A read/write from the previous connection cannot authorize a new one.
            return
        }
        if (target == null || !target.refresh() || focused() != target ||
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
        bound = false; keyboardOwnership = null; relativeOwnership = null
        inputEpoch = service.inputEpoch
        isTerminal = false; terminalInsertion = null; nodeActions = false
        continuation.reset(); userInteracted = false
        node = focused() ?: return fail("[AF-E01] Tap inside a text box first. Password fields are excluded")
        if (!node!!.refresh()) return fail("[AF-E03] The original text box is no longer available")
        if (terminalNode(node!!)) {
            if (service.inputMethod?.currentInputConnection == null)
                return fail("[AF-E04] Tap the Termux terminal to attach its keyboard")
            isTerminal = true
            terminalFieldId = service.inputMethod?.currentInputEditorInfo?.fieldId ?: 0
            terminalInsertion = TerminalInsertion()
            bound = true
            app.crashJournal.terminalStage("bound")
            return true
        }
        val connection = service.inputMethod?.currentInputConnection
        // Accessibility identifies the field. Its visible text/selection can include
        // CSS placeholder content and are never coordinates for keyboard mutations.
        if (connection != null) {
            if (service.inputMethod?.currentInputEditorInfo?.packageName != node!!.packageName?.toString())
                return fail("[AF-E05] The keyboard is attached to another app; tap this text box again")
            keyboardFieldId = service.inputMethod?.currentInputEditorInfo?.fieldId ?: 0
            bindingSelection = service.imeSelection
            bindingPresentation = presentation()
            nodeActions = false
            bound = true
            return true
        }
        nodeActions = supportsNodeActions(node!!)
        val snapshot = snapshotNode() ?: return false
        val ambiguousHint = !node!!.isShowingHintText && !node!!.hintText.isNullOrEmpty() && node!!.text?.toString() == node!!.hintText.toString()
        val emptyEvidence = node!!.isShowingHintText || node!!.text != null ||
            (node!!.textSelectionStart == 0 && node!!.textSelectionEnd == 0)
        nodeActions = nodeActions && snapshot.text.isEmpty() && emptyEvidence && !ambiguousHint
        app.crashJournal.editorBinding(snapshot, node!!.hintText?.length ?: 0,
            !node!!.hintText.isNullOrEmpty() && node!!.text?.toString() == node!!.hintText.toString(), node!!.isShowingHintText,
            nodeActions, false)
        if (!nodeActions) return fail(if (ambiguousHint)
            "[AF-E16] This field’s placeholder cannot be separated from its text; no text was changed"
            else "[AF-E04] Can't safely edit the existing text; select the field again or start in an empty field")
        // Binding permits microphone startup during typing; writes use fresh
        // matching text/cursor evidence and finish composition before insertion.
        ownership = EditorOwnership(snapshot.copy(composing = false))
        bound = ownership?.certain == true
        return bound
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
        if (!nodeActions && !isTerminal) return sameTarget()
        if (isTerminal) return terminalInsertion != null && terminalValid()
        val owner = ownership ?: return false
        val snapshot = snapshotNode() ?: return false
        return if (first) owner.canFirstWrite(snapshot) else owner.matches(snapshot)
    }

    /** Focus identity, not text/cursor equality, decides whether dictation stays attached. */
    fun sameTarget(): Boolean {
        if (!bound) return false
        if (isTerminal) return terminalValid()
        val target = node ?: return false
        return app.screenReady() && inputEpoch == service.inputEpoch && target.refresh() && focused() == target && !isProtected(target) &&
            (nodeActions || (service.inputMethod?.currentInputConnection != null &&
                service.inputMethod?.currentInputEditorInfo?.packageName == target.packageName?.toString() &&
                service.inputMethod?.currentInputEditorInfo?.fieldId == keyboardFieldId))
    }

    fun write(value: String, completion: (EditorWriteResult) -> Unit) {
        if (!isTerminal) {
            if (nodeActions) writeOwned(value, false, completion) else writeKeyboard(value, false, completion)
            return
        }
        if (!app.coordinator.finishing || app.coordinator.active) { completion(EditorWriteResult.FAILED); return }
        try {
            val connection = service.inputMethod?.currentInputConnection
            val ok = terminalInsertion?.send(value, connection != null && terminalValid()) {
                app.crashJournal.terminalStage("dispatched")
                connection!!.commitText(it, 1, null)
            } == true
            if (!ok) { app.crashJournal.terminalStage("rejected"); fail("[AF-T01] Termux focus changed; text is saved in AlienFlow") }
            completion(if (ok) EditorWriteResult.APPLIED else EditorWriteResult.FAILED)
        } catch (_: Exception) {
            app.crashJournal.terminalStage("exception")
            fail("[AF-T02] Termux input could not be confirmed; text is saved in AlienFlow")
            completion(EditorWriteResult.FAILED)
        }
    }

    private fun presentation(): EditorEvidence.NodeText? = node?.let {
        EditorEvidence.NodeText(it.text?.toString(), it.isShowingHintText, it.textSelectionStart, it.textSelectionEnd)
    }

    private fun writeKeyboard(value: String, restoring: Boolean, completion: (EditorWriteResult) -> Unit) {
        lastError = ""
        val writeGeneration = generation
        val sequence = ++writeSequence
        val connectionEpoch = service.inputEpoch
        fun current() = generation == writeGeneration && sequence == writeSequence && bound &&
            connectionEpoch == service.inputEpoch &&
            app.coordinator.service === service && (restoring || app.coordinator.active || app.coordinator.finishing) && sameTarget()
        scope.launch {
            var outcome = EditorWriteResult.USER_EDIT
            var phase = "before"
            var evidence: EditorEvidence.KeyboardText? = null
            var evidenceCode = "unavailable"
            var readComposing = false
            try {
                if (!current()) return@launch
                val connection = service.inputMethod?.currentInputConnection ?: return@launch
                var owner = keyboardOwnership
                val preview = if (restoring) value else continuation.tail(value)
                val requested = owner?.replacement(preview)?.let {
                    maxOf(it.range.last.toLong() + 1 - it.range.first, it.text.length.toLong()) + 64
                }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
                    ?: maxOf(128L, preview.length.toLong() + 64).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                suspend fun readRaw(): EditorEvidence.KeyboardText? {
                    val selection = service.imeSelection
                    val surrounding = withContext(Dispatchers.IO) {
                        connection.getSurroundingText(requested, requested, InputConnection.GET_TEXT_WITH_STYLES)
                    }
                    evidence = surrounding?.let { EditorEvidence.KeyboardText(it.text.toString(),
                        it.selectionStart, it.selectionEnd, it.offset,
                        selection.takeIf { selection === service.imeSelection }) }
                    readComposing = service.composing || hasComposition(surrounding?.text)
                    return evidence
                }
                suspend fun read(): KeyboardWindow? {
                    readRaw()
                    return evidence?.let { KeyboardWindow.read(it, readComposing) }
                        .also { evidenceCode = if (it != null) "none" else if (evidence == null) "04" else "11" }
                }
                val rawInitial = withTimeoutOrNull(3_000L) { readRaw() } ?: run {
                    outcome = EditorWriteResult.FAILED; return@launch
                }
                if (!current()) return@launch
                if (rawInitial.offset == -1 || relativeOwnership != null) {
                    if (!RelativeKeyboardOwnership.usable(rawInitial)) { outcome = EditorWriteResult.FAILED; return@launch }
                    if (relativeOwnership == null && keyboardOwnership != null) {
                        continuation.protectDispatched(); userInteracted = true; keyboardOwnership = null
                    }
                    var relative = relativeOwnership
                    if (relative == null || readComposing || !relative.matches(rawInitial)) {
                        if (restoring) return@launch
                        if (relative != null || readComposing || bindingPresentation != presentation()) {
                            continuation.protectDispatched(); userInteracted = true
                        }
                        relative = RelativeKeyboardOwnership(rawInitial, writeGeneration, !userInteracted)
                        relativeOwnership = relative
                    }
                    val relativeWriter = checkNotNull(relative)
                    val tail = if (restoring) value else continuation.tail(value)
                    val inserted = if (!restoring) relativeWriter.separateFromTypedWord(tail) else tail
                    if (!restoring && relativeWriter.first && inserted.isEmpty()) {
                        continuation.dispatched(value); outcome = EditorWriteResult.APPLIED; return@launch
                    }
                    val fresh = withTimeoutOrNull(3_000L) { readRaw() }
                    if (!current()) return@launch
                    val edit = fresh?.let { relativeWriter.edit(it, inserted) }
                    if (edit == null) {
                        outcome = if (fresh != null && !relativeWriter.matches(fresh)) EditorWriteResult.RETRY else EditorWriteResult.FAILED
                        return@launch
                    }
                    // Delete only observed speech immediately BEFORE the current selection;
                    // commit replaces the current selection. No absolute setSelection.
                    if (edit.deleteBefore > 0) {
                        connection.deleteSurroundingText(edit.deleteBefore, 0)
                        val removed = withTimeoutOrNull(3_000L) { readRaw() }
                        if (!current()) return@launch
                        if (removed == null || !relativeWriter.deletionMatches(removed, edit.deleteBefore)) {
                            outcome = EditorWriteResult.FAILED; return@launch
                        }
                    }
                    connection.commitText(edit.text, 1, null)
                    if (!restoring) continuation.dispatched(value)
                    phase = "after"
                    outcome = withTimeoutOrNull(3_000L) {
                        repeat(16) {
                            if (!current()) return@withTimeoutOrNull EditorWriteResult.USER_EDIT
                            val observed = readRaw()
                            if (!current()) return@withTimeoutOrNull EditorWriteResult.USER_EDIT
                            if (observed != null) {
                                if (relativeWriter.confirm(observed, inserted, edit.confirmationLength, readComposing))
                                    return@withTimeoutOrNull EditorWriteResult.APPLIED
                                if (!relativeWriter.matches(observed)) return@withTimeoutOrNull EditorWriteResult.RETRY
                            }
                            if (it < 15) delay(50)
                        }
                        EditorWriteResult.FAILED
                    } ?: EditorWriteResult.FAILED
                    return@launch
                }
                val initial = KeyboardWindow.read(rawInitial, readComposing) ?: run {
                    fail("[AF-E09] Keyboard text or cursor unavailable; words are saved in AlienFlow")
                    outcome = EditorWriteResult.FAILED; return@launch
                }
                val changedBeforeFirstRead = owner == null && (bindingPresentation != presentation() ||
                    (evidence?.offset == -1 && bindingSelection?.let { minOf(it.start, it.end) != initial.absoluteStart ||
                        maxOf(it.start, it.end) != initial.absoluteEnd } == true))
                if (owner == null || initial.composing || !owner.matches(initial)) {
                    if (restoring) return@launch
                    if (owner != null || initial.composing || changedBeforeFirstRead) {
                        userInteracted = true; continuation.protectDispatched()
                    }
                    owner = KeyboardEditorOwnership(initial, writeGeneration, replaceSelection = !userInteracted)
                    keyboardOwnership = owner
                }
                val writer = owner
                val tail = if (restoring) value else continuation.tail(value)
                val inserted = if (!restoring) writer.separateFromTypedWord(tail) else tail
                if (!restoring && writer.first && inserted.isEmpty()) {
                    continuation.dispatched(value); outcome = EditorWriteResult.APPLIED; return@launch
                }
                val replacement = writer.replacement(inserted)
                // A fresh read authorizes exactly the characters about to be replaced.
                // A short window cannot authorize rewriting unseen earlier speech.
                val fresh = withTimeoutOrNull(3_000L) { read() }
                if (!current()) return@launch
                if (fresh == null || !writer.covers(fresh, replacement.range)) {
                    evidenceCode = "09"; outcome = EditorWriteResult.FAILED; return@launch
                }
                if (!writer.matches(fresh)) { outcome = EditorWriteResult.RETRY; return@launch }
                val selectionAtDispatch = evidence?.selection
                val range = replacement.range
                if (writer.first || range.first != fresh.absoluteStart || range.last + 1 != fresh.absoluteEnd)
                    connection.setSelection(range.first, range.last + 1)
                connection.commitText(replacement.text, 1, null)
                if (!restoring) continuation.dispatched(value)
                phase = "after"
                // Confirm through the same API/coordinate system that performed the edit.
                // No whole-field SET_TEXT, clipboard paste, or resend after a timeout.
                outcome = withTimeoutOrNull(3_000L) {
                    repeat(16) {
                        if (!current()) return@withTimeoutOrNull EditorWriteResult.USER_EDIT
                        val observed = read()
                        if (!current()) return@withTimeoutOrNull EditorWriteResult.USER_EDIT
                        if (observed != null) {
                            if (writer.confirm(observed, inserted)) return@withTimeoutOrNull EditorWriteResult.APPLIED
                            val staleSelection = evidence?.offset == -1 && evidence?.selection == selectionAtDispatch &&
                                observed.absoluteEnd != writer.proposedCursor(inserted)
                            if (!staleSelection && !writer.matches(observed))
                                return@withTimeoutOrNull EditorWriteResult.RETRY
                        }
                        if (it < 15) delay(50)
                    }
                    EditorWriteResult.FAILED
                } ?: EditorWriteResult.FAILED
            } catch (_: CancellationException) {
                outcome = EditorWriteResult.USER_EDIT
            } catch (_: Exception) {
                fail("[AF-E15] Android rejected the editor operation; words are saved in AlienFlow")
                outcome = EditorWriteResult.FAILED
            } finally {
                if (outcome == EditorWriteResult.USER_EDIT && current()) outcome = EditorWriteResult.RETRY
                if (outcome == EditorWriteResult.USER_EDIT && generation == writeGeneration && bound && sameTarget()) outcome = EditorWriteResult.RETRY
                if (outcome == EditorWriteResult.RETRY) {
                    continuation.protectDispatched(); userInteracted = true; keyboardOwnership = null; relativeOwnership = null
                }
                if (outcome == EditorWriteResult.FAILED && current()) {
                    app.crashJournal.keyboardObservation(phase, presentation(), evidence, evidenceCode)
                    if (!lastError.startsWith("[AF-E15]")) fail(if (phase == "before")
                        "[AF-E09] Keyboard read did not cover this edit; see Diagnostics"
                        else "[AF-E14] Keyboard insertion unconfirmed; see Diagnostics")
                }
                completion(outcome)
            }
        }
    }

    private fun writeOwned(value: String, restoring: Boolean, completion: (EditorWriteResult) -> Unit) {
        var owner = ownership ?: return completion(EditorWriteResult.USER_EDIT)
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
                val initialNode = snapshotNode() ?: run {
                    outcome = if (sameTarget()) EditorWriteResult.RETRY else EditorWriteResult.USER_EDIT
                    return@launch
                }
                var before = initialNode.copy(composing = false)
                // Accessibility commitText batches finishComposingText before insertion
                // (AOSP RemoteInputConnectionImpl). Do not wait for the user to type a
                // space: snapshot the literal typed word and preserve it as an anchor.
                if (initialNode.composing || !(if (first) owner.canFirstWrite(before) else owner.matches(before))) {
                    if (restoring) return@launch
                    userInteracted = true
                    continuation.protectDispatched()
                    owner.invalidate()
                    owner = EditorOwnership(before, replaceSelection = false); ownership = owner; first = true
                    if (nodeActions && !node!!.isShowingHintText && !node!!.hintText.isNullOrEmpty() &&
                        node!!.text?.toString() == node!!.hintText.toString()) {
                        fail("[AF-E16] This field’s placeholder cannot be separated from its text; no text was changed")
                        outcome = EditorWriteResult.FAILED; return@launch
                    }
                }
                val tail = if (restoring) value else continuation.tail(value)
                var inserted = if (!restoring) owner.separateFromTypedWord(tail) else tail
                if (!restoring && first && inserted.isEmpty()) {
                    continuation.dispatched(value); outcome = EditorWriteResult.APPLIED; return@launch
                }
                if (nodeActions) {
                    phase = "node"
                    val expected = owner.proposedSnapshot(inserted)
                    val selection: ((Int) -> Boolean)? = if (node!!.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_SELECTION })
                        { cursor -> current() && node!!.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,
                            Bundle().apply { putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor); putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor) }) }
                        else null
                    outcome = NodeEditorWriter.writeMerged(before, expected, owner, first, nodeActions, ::current,
                        read = { snapshotNode().also { observation = EditorSynchronization.Reading(it, null, it?.composing == true) } },
                        setText = { text -> current() && node!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                            Bundle().apply { putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }) },
                        setSelection = selection,
                        dispatched = { if (!restoring) continuation.dispatched(value) })
                    if (outcome == EditorWriteResult.APPLIED) {
                        val confirmed = observation.node
                        if (confirmed == null || !owner.confirm(confirmed, inserted)) outcome = EditorWriteResult.FAILED
                        else first = false
                    }
                    return@launch
                }
            } catch (_: CancellationException) {
                outcome = EditorWriteResult.USER_EDIT
            } catch (_: Exception) {
                fail("[AF-E15] Android rejected the editor operation; words are saved in AlienFlow")
                outcome = EditorWriteResult.FAILED
            } finally {
                // An interaction during confirmation retires only the dispatched span.
                // The next write adopts fresh evidence in this same field, never another.
                if (outcome == EditorWriteResult.USER_EDIT && current()) outcome = EditorWriteResult.RETRY
                if (outcome == EditorWriteResult.RETRY) {
                    continuation.protectDispatched()
                    userInteracted = true
                    owner.invalidate()
                } else if (outcome != EditorWriteResult.APPLIED) owner.invalidate()
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
        if (userInteracted) { completion(false); return }
        if (!nodeActions) {
            relativeOwnership?.let { owner ->
                if (owner.first) { completion(true); return }
                writeKeyboard(owner.originalSelection(), true) { completion(it == EditorWriteResult.APPLIED) }
                return
            }
            val owner = keyboardOwnership ?: return completion(true)
            if (owner.first) { completion(true); return }
            writeKeyboard(owner.originalSelection(), true) { completion(it == EditorWriteResult.APPLIED) }
            return
        }
        val owner = ownership ?: return completion(false)
        if (first) { completion(true); return }
        if (!valid()) { owner.invalidate(); completion(false); return }
        writeOwned(owner.originalSelection(), true) { completion(it == EditorWriteResult.APPLIED) }
    }
    fun detachForCapture() { invalidate(); detachedSnapshot = snapshotNode() }
    fun detachedTargetUnchanged() = detachedSnapshot?.let { it == snapshotNode() } == true
    fun invalidate() { bound = false; generation++; writeSequence++; ownership?.invalidate(); keyboardOwnership = null; relativeOwnership = null; terminalInsertion = null; isTerminal = false }
    private fun rejected(reason: String): EditorSnapshot? { fail(reason); return null }
    private fun fail(reason: String): Boolean {
        lastError = reason
        app.crashJournal.editorFailure(reason)
        return false
    }
}
