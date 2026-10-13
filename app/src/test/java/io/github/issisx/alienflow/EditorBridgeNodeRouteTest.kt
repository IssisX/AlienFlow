package io.github.issisx.alienflow

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.app.Activity
import android.os.Bundle
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.SurroundingText
import android.view.inputmethod.TextAttribute
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowAccessibilityNodeInfo
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Real bridge/service/widget and native InputConnection. OS broker/remote IPC are simulated, never phone proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class, shadows = [EditorBridgeNodeRouteTest.Broker::class,
    EditorBridgeNodeRouteTest.NodeIpc::class, EditorBridgeNodeRouteTest.Ime::class, EditorBridgeNodeRouteTest.KeyboardIpc::class])
class EditorBridgeNodeRouteTest {
    companion object {
        var field: EditText? = null; var focused: AccessibilityNodeInfo? = null; var mutations = 0
        var nativeKeyboard: InputConnection? = null; var inputMethod: InputMethod? = null
        var connection: InputMethod.AccessibilityInputConnection? = null; var info: EditorInfo? = null
        var brokenHint = false; var hideCompositionSpans = false; val selections = mutableListOf<Pair<Int, Int>>()
        var serviceForCallbacks: AlienAccessibilityService? = null
        var keyboardWindow = Int.MAX_VALUE
        var unexposedSuffix = ""
        var conflictingKeyboard = false
        val committedTexts = mutableListOf<String>()
    }
    @Implements(AccessibilityService::class)
    class Broker : ShadowAccessibilityService() {
        @Implementation fun findFocus(type: Int) = if (type == AccessibilityNodeInfo.FOCUS_INPUT) focused else null
        @Implementation fun getInputMethod(): InputMethod? = inputMethod
    }
    @Implements(InputMethod::class)
    class Ime {
        @Implementation fun getCurrentInputConnection() = connection
        @Implementation fun getCurrentInputEditorInfo() = info
        @Implementation fun getCurrentInputStarted() = connection != null
    }
    @Implements(InputMethod.AccessibilityInputConnection::class)
    class KeyboardIpc {
        @Implementation fun getSurroundingText(before: Int, after: Int, flags: Int): SurroundingText? {
            val read = nativeKeyboard?.getSurroundingText(minOf(before, keyboardWindow), minOf(after, keyboardWindow), flags) ?: return null
            return if (conflictingKeyboard && read.text.isNotEmpty()) SurroundingText(
                "!" + read.text.toString().drop(1), read.selectionStart, read.selectionEnd, read.offset) else read
        }
        @Implementation fun setSelection(start: Int, end: Int) { selections.add(start to end); nativeKeyboard!!.setSelection(start, end) }
        @Implementation fun commitText(text: CharSequence, cursor: Int, attributes: TextAttribute?) {
            committedTexts.add(text.toString())
            // AOSP accessibility IPC batches finishComposingText before commitText.
            // Calling a raw native commit alone would simulate a different API.
            nativeKeyboard!!.beginBatchEdit()
            nativeKeyboard!!.finishComposingText()
            nativeKeyboard!!.commitText(text, cursor)
            nativeKeyboard!!.endBatchEdit()
            serviceForCallbacks?.confirmEmptyComposition() // Simulated OS selection callback.
        }
    }
    @Implements(AccessibilityNodeInfo::class)
    class NodeIpc : ShadowAccessibilityNodeInfo() {
        @RealObject private lateinit var actual: AccessibilityNodeInfo
        @Implementation override fun refresh(): Boolean {
            val source = field?.createAccessibilityNodeInfo() ?: return false
            actual.hintText = source.hintText
            if (brokenHint && field!!.text.isEmpty()) {
                actual.text = source.hintText; actual.isShowingHintText = false; actual.setTextSelection(1, 1)
            } else {
                actual.text = if (hideCompositionSpans) source.text?.toString() else source.text
                // Simulate distinct accessibility/IME representations; the real native
                // editor and InputConnection retain every suffix character.
                if (unexposedSuffix.isNotEmpty() && source.text?.endsWith(unexposedSuffix) == true)
                    actual.text = source.text!!.dropLast(unexposedSuffix.length)
                actual.isShowingHintText = source.isShowingHintText
                actual.setTextSelection(source.textSelectionStart, source.textSelectionEnd)
            }
            // Real remote refresh publishes the widget's current actions as well.
            actual.actionList.toList().forEach { actual.removeAction(it) }
            source.actionList.forEach { actual.addAction(it) }
            return true
        }
        @Implementation override fun performAction(action: Int, arguments: Bundle?): Boolean {
            mutations++
            val result = field?.performAccessibilityAction(action, arguments) ?: false
            if (result && action == AccessibilityNodeInfo.ACTION_SET_TEXT)
                serviceForCallbacks?.confirmEmptyComposition() // Native setText clears composing spans.
            return result
        }
    }
    @Before fun reset() {
        field = null; focused = null; mutations = 0; nativeKeyboard = null; inputMethod = null
        connection = null; info = null; brokenHint = false; hideCompositionSpans = false; selections.clear(); serviceForCallbacks = null
        keyboardWindow = Int.MAX_VALUE
        unexposedSuffix = ""; conflictingKeyboard = false
        committedTexts.clear()
    }
    private fun withField(text: String = "", hint: String = "", keyboard: Boolean = false, block: (AlienAccessibilityService) -> Unit) {
        Robolectric.buildActivity(Activity::class.java).setup().use { activity ->
            field = EditText(activity.get()).apply { this.hint = hint; setText(text); setSelection(text.length) }
            activity.get().setContentView(field); field!!.requestFocus()
            focused = field!!.createAccessibilityNodeInfo()
            val controller = Robolectric.buildService(AlienAccessibilityService::class.java).create()
            val service = controller.get()
            serviceForCallbacks = service
            if (keyboard) {
                info = EditorInfo().apply { packageName = focused!!.packageName.toString(); inputType = field!!.inputType; fieldId = 42 }
                nativeKeyboard = field!!.onCreateInputConnection(EditorInfo())!!
                inputMethod = InputMethod(service)
                @Suppress("UNCHECKED_CAST")
                val remoteClass = Class.forName("com.android.internal.inputmethod.RemoteAccessibilityInputConnection") as Class<Any>
                connection = ReflectionHelpers.callConstructor(InputMethod.AccessibilityInputConnection::class.java,
                    ClassParameter.from(InputMethod::class.java, inputMethod), ClassParameter.from(remoteClass, null))
            }
            service.javaClass.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
            try { block(service) } finally {
                (service.application as AlienApp).coordinator.javaClass.getDeclaredField("active").apply { isAccessible = true }.setBoolean((service.application as AlienApp).coordinator, false)
                controller.destroy()
            }
        }
    }
    private fun activate(service: AlienAccessibilityService) {
        val app = service.application as AlienApp
        app.coordinator.javaClass.getDeclaredField("active").apply { isAccessible = true }.setBoolean(app.coordinator, true)
    }
    private fun write(service: AlienAccessibilityService, value: String): EditorWriteResult? {
        var result: EditorWriteResult? = null
        service.bridge.write(value) { result = it }
        val until = System.currentTimeMillis() + 10_000
        while (result == null && System.currentTimeMillis() < until) {
            // Advance the paused framework clock as well as waiting for IO. A
            // rejected read must exercise the production retry/timeout boundary.
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(5)); Thread.sleep(5)
        }
        assertNotNull("Editor operation did not complete", result)
        return result
    }
    @Test fun reported25To27MismatchPreservesImeOnlyCharactersThroughRevisionAndCancel() =
        withField("abcdefghijklmnopqrstuvwxyXY", keyboard = true) { service ->
            unexposedSuffix = "XY"; field!!.setSelection(25)
            val original = field!!.text.toString()
            assertTrue(service.bridge.begin()); activate(service)
            assertEquals(EditorWriteResult.APPLIED, write(service, "hello"))
            assertEquals(EditorWriteResult.APPLIED, write(service, "Hello."))
            assertEquals(original.take(25) + "Hello.XY", field!!.text.toString())
            assertEquals(31, field!!.selectionStart)
            var canceled: Boolean? = null
            service.bridge.cancel { canceled = it }
            val until = System.currentTimeMillis() + 5_000
            while (canceled == null && System.currentTimeMillis() < until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
            assertEquals(true, canceled); assertEquals(original, field!!.text.toString())
            assertEquals(0, mutations)
        }
    @Test fun unexposedSuffixSurvivesBoundedWindowSelectionAndUnicodeCorrection() =
        withField("older 🛸 line\n".repeat(20) + "left selected right\n\n", keyboard = true) { service ->
            unexposedSuffix = "\n\n"; keyboardWindow = 24
            val original = field!!.text.toString(); val start = original.indexOf("selected")
            field!!.setSelection(start, start + 8)
            assertTrue(service.bridge.begin()); activate(service)
            assertEquals(EditorWriteResult.APPLIED, write(service, "hello 🛸"))
            assertEquals(EditorWriteResult.APPLIED, write(service, "Hello 🚀."))
            assertEquals(original.take(start) + "Hello 🚀." + original.drop(start + 8), field!!.text.toString())
            assertEquals(listOf(start to start + 8, start to start + "hello 🛸".length), selections)
            assertEquals(0, mutations)
        }
    @Test fun longerKeyboardBufferStillRejectsConflictingSharedTextBeforeAnyCommit() =
        withField("abcdefghijklmnopqrstuvwxyXY", keyboard = true) { service ->
            unexposedSuffix = "XY"; field!!.setSelection(25)
            assertTrue(service.bridge.begin()); activate(service); conflictingKeyboard = true
            assertEquals(EditorWriteResult.FAILED, write(service, "hello"))
            assertEquals("abcdefghijklmnopqrstuvwxyXY", field!!.text.toString())
            assertTrue(committedTexts.isEmpty()); assertTrue(selections.isEmpty()); assertEquals(0, mutations)
            val report = (service.application as AlienApp).crashJournal.diagnostic()
            assertTrue(report, report.contains("evidence_code=10"))
            assertFalse(report.contains("abcdefghijklmnopqrstuvwxy"))
        }
    @Test fun typingBetweenSpeechUpdatesWithAnUnexposedSuffixRemainsUserOwned() =
        withField("draft XY", keyboard = true) { service ->
            unexposedSuffix = "XY"; field!!.setSelection(6)
            assertTrue(service.bridge.begin()); activate(service)
            assertEquals(EditorWriteResult.APPLIED, write(service, "hello"))
            val cursor = field!!.selectionStart
            field!!.text.insert(cursor, " typed"); field!!.setSelection(cursor + 6)
            assertEquals(EditorWriteResult.APPLIED, write(service, "hello there"))
            assertEquals("draft hello typed thereXY", field!!.text.toString())
            assertTrue(service.bridge.hasUserInteraction); assertTrue(service.bridge.sameTarget())
            var canceled: Boolean? = null; service.bridge.cancel { canceled = it }
            assertEquals(false, canceled)
            assertEquals("draft hello typed thereXY", field!!.text.toString()); assertEquals(0, mutations)
        }
    @Test fun boundedKeyboardWindowInALongExistingDraftInsertsAndRevisesOnlyTheOwnedRange() =
        withField("older line 🛸\n".repeat(100) + "left  right", keyboard = true) { service ->
            keyboardWindow = 24
            val initial = field!!.text.toString(); val cursor = initial.length - 6
            field!!.setSelection(cursor)
            assertTrue(service.bridge.begin()); activate(service)
            assertEquals(EditorWriteResult.APPLIED, write(service, "hello world"))
            assertEquals(EditorWriteResult.APPLIED, write(service, "Hello, world."))
            assertEquals(initial.take(cursor) + "Hello, world." + initial.drop(cursor), field!!.text.toString())
            assertEquals(cursor + "Hello, world.".length, field!!.selectionStart)
            assertEquals(0, mutations) // Range edits through the native keyboard, no whole-field SET_TEXT.
        }
    @Test fun aLongOwnedDictationCanAppendThroughAShortKeyboardWindow() = withField("prior draft ", keyboard = true) { service ->
        assertTrue(service.bridge.begin()); activate(service)
        val spoken = "hello ".repeat(100)
        assertEquals(EditorWriteResult.APPLIED, write(service, spoken))
        keyboardWindow = 32
        assertEquals(EditorWriteResult.APPLIED, write(service, spoken + "new words"))
        assertEquals("prior draft " + spoken + "new words", field!!.text.toString())
        assertEquals(listOf(spoken, "new words"), committedTexts)
    }
    @Test fun endOnlyDocumentWaitsForStopDespiteAccessibilityEvents() = withField("left  right", keyboard = true) { service ->
        val app = service.application as AlienApp
        app.store.prefs.edit().clear().putString("correction", "A").putBoolean("live_display", false).commit()
        val coordinator = DictationCoordinator(app, app.store, keyProvider = { "fixture-not-a-key" })
        ReflectionHelpers.setField(app, "coordinator", coordinator); coordinator.service = service
        shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        field!!.setSelection(5); coordinator.start(); assertTrue(coordinator.status, coordinator.active)
        coordinator.turnCommitted()
        coordinator.event(org.json.JSONObject().put("type", "input_audio_buffer.committed").put("event_id", "ack").put("item_id", "one"))
        coordinator.event(org.json.JSONObject().put("type", "conversation.item.input_audio_transcription.completed")
            .put("event_id", "final").put("item_id", "one").put("transcript", "hello world"))
        coordinator.targetEvent()
        val writeUntil = System.currentTimeMillis() + 2_000
        while (ReflectionHelpers.getField<Boolean>(coordinator, "writing") && System.currentTimeMillis() < writeUntil) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5)
        }
        assertEquals("left  right", field!!.text.toString())
        coordinator.stop()
        val until = System.currentTimeMillis() + 2_000
        while (coordinator.finishing && System.currentTimeMillis() < until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        assertFalse(coordinator.status, coordinator.finishing)
        assertEquals("left hello world right", field!!.text.toString())
    }
    @Test fun coldEmptyFieldWorksWithoutAnImeAndPreservesItsOwnedTextAcrossRestart() = withField { service ->
        assertNull(service.inputMethod); assertTrue(service.bridge.lastError, service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello")); assertEquals("hello", field!!.text.toString())
        service.bridge.inputStarted(EditorInfo().apply { packageName = focused!!.packageName.toString() }, true)
        assertTrue(service.bridge.valid())
        assertEquals(EditorWriteResult.APPLIED, write(service, "Hello.")); assertEquals("Hello.", field!!.text.toString())
        assertTrue(service.bridge.sameTarget())
    }
    @Test fun existingDraftWithoutARangeConnectionIsNeverRebuilt() = withField("left selected right") { service ->
        field!!.setSelection(5, 13)
        assertFalse(service.bridge.begin()); assertTrue(service.bridge.lastError.startsWith("[AF-E04]"))
        assertEquals("left selected right", field!!.text.toString()); assertEquals(0, mutations)
    }
    @Test fun nativeKeyboardRangeEditsPreserveExistingPrefixAndSuffix() = withField("left selected right", keyboard = true) { service ->
        field!!.setSelection(5, 13)
        assertTrue(service.bridge.lastError, service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello")); assertEquals("left hello right", field!!.text.toString())
        assertEquals(EditorWriteResult.APPLIED, write(service, "Hello.")); assertEquals("left Hello. right", field!!.text.toString())
        assertEquals(listOf(5 to 13, 5 to 10), selections); assertEquals(0, mutations)
    }
    @Test fun misreportedPlaceholderCannotBecomeDictatedPrefixOrSuffix() = withField(hint = "Start a note", keyboard = true) { service ->
        brokenHint = true
        assertTrue(service.bridge.lastError, service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "spoken words"))
        assertEquals("spoken words", field!!.text.toString()); assertEquals(listOf(0 to 0), selections); assertEquals(0, mutations)
    }
    @Test fun ambiguousHintWithoutIndependentReadbackDoesNotMutate() = withField(hint = "Start a note") { service ->
        brokenHint = true
        assertFalse(service.bridge.begin()); assertTrue(service.bridge.lastError.startsWith("[AF-E16]")); assertEquals(0, mutations)
        assertEquals("", field!!.text.toString())
    }
    @Test fun realUserTextEqualToTheHintRemainsInTheField() = withField("Start a note", "Start a note", keyboard = true) { service ->
        field!!.setSelection(5)
        assertTrue(service.bridge.lastError, service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, " words")); assertEquals("Start words a note", field!!.text.toString())
        assertEquals(listOf(5 to 5), selections); assertEquals(0, mutations)
    }

    @Test fun keyboardTypingKeepsTheCoordinatorAttachedAndSpeechContinuesInTheSameTurn() = withField(keyboard = true) { service ->
        assertTrue(service.bridge.begin()); activate(service)
        val coordinator = (service.application as AlienApp).coordinator
        fun set(name: String, value: Any?) = coordinator.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(coordinator, value)
        set("editor", service.bridge); set("captureTarget", service.bridge); set("correctionMode", "A")
        fun delta(id: String, text: String) {
            coordinator.event(org.json.JSONObject().put("type", "conversation.item.input_audio_transcription.delta")
                .put("event_id", id).put("item_id", "turn").put("delta", text))
            val until = System.currentTimeMillis() + 10_000
            do { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
            while (coordinator.javaClass.getDeclaredField("writing").apply { isAccessible = true }.getBoolean(coordinator) && System.currentTimeMillis() < until)
        }
        delta("a", "hello"); assertEquals("hello", field!!.text.toString())
        field!!.append(" typed"); field!!.setSelection(field!!.length()); coordinator.targetEvent()
        assertTrue(coordinator.active); assertSame(service.bridge, coordinator.javaClass.getDeclaredField("editor").apply { isAccessible = true }.get(coordinator))
        delta("b", " there"); assertEquals("hello typed there", field!!.text.toString()); assertTrue(coordinator.active)
        field!!.append(" again"); field!!.setSelection(field!!.length()); coordinator.targetEvent()
        delta("c", " friend"); assertEquals("hello typed there again friend", field!!.text.toString())
        assertFalse(coordinator.status.contains("paused", ignoreCase = true))
    }

    @Test fun nodeOnlyEditorPreservesKeyboardTextAndContinuesWithoutAModeSwitch() = withField { service ->
        assertTrue(service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello"))
        field!!.append(" typed"); field!!.setSelection(field!!.length())
        assertTrue(service.bridge.sameTarget())
        assertEquals(EditorWriteResult.APPLIED, write(service, "Hello. there"))
        assertEquals("hello typed there", field!!.text.toString())
        assertEquals(EditorWriteResult.APPLIED, write(service, "Hello. there friend"))
        assertEquals("hello typed there friend", field!!.text.toString())
    }

    @Test fun nodeOnlyEditorKeepsAnEditedWordAndRestoresAMiddleCursor() = withField { service ->
        assertTrue(service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello there"))
        field!!.setText("Cory there"); field!!.setSelection(4)
        assertEquals(EditorWriteResult.APPLIED, write(service, "Hello there friend"))
        assertEquals("Cory friend there", field!!.text.toString()); assertEquals(11, field!!.selectionStart)
        assertEquals(EditorWriteResult.APPLIED, write(service, "Hello there friend again"))
        assertEquals("Cory friend again there", field!!.text.toString()); assertEquals("Cory friend again".length, field!!.selectionStart)
    }

    @Test fun editingRecognizedWordsAndLateFinalizationNeverOverwriteKeyboardEdits() = withField("left  right", keyboard = true) { service ->
        field!!.setSelection(5); assertTrue(service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello world"))
        field!!.setText("left hello Cory right"); field!!.setSelection(15)
        assertEquals(EditorWriteResult.APPLIED, write(service, "Hello, world."))
        assertEquals("left hello Cory right", field!!.text.toString())
        assertEquals(EditorWriteResult.APPLIED, write(service, "Hello, world. next"))
        assertEquals("left hello Cory next right", field!!.text.toString())
    }

    @Test fun cursorMovementInsertsNewSpeechWithoutDeletingSelectedUserText() = withField("left  right", keyboard = true) { service ->
        field!!.setSelection(5); assertTrue(service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello"))
        field!!.setSelection(0, 4)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello next"))
        assertEquals("left next hello right", field!!.text.toString())
    }

    @Test fun composingKeyboardWordDoesNotWaitForSpaceBeforeMoreSpeech() = withField(keyboard = true) { service ->
        assertTrue(service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello"))
        nativeKeyboard!!.setComposingText(" typ", 1)
        service.javaClass.getDeclaredField("composing").apply { isAccessible = true }.setBoolean(service, true)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello there"))
        assertEquals("hello typ there", field!!.text.toString())
        assertTrue((service.application as AlienApp).coordinator.active); assertTrue(service.bridge.sameTarget())
    }

    @Test fun microphoneBindingDoesNotRequireTheUserToStopTypingFirst() = withField("typ", keyboard = true) { service ->
        nativeKeyboard!!.setComposingRegion(0, 3)
        service.javaClass.getDeclaredField("composing").apply { isAccessible = true }.setBoolean(service, true)
        assertTrue(service.bridge.lastError, service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello"))
        assertTrue((service.application as AlienApp).coordinator.active)
        assertEquals("typ hello", field!!.text.toString())
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello world"))
        assertEquals("typ hello world", field!!.text.toString())
    }

    @Test fun keyboardCompositionStillWorksWhenTheNodeOmitsComposingMetadata() = withField("typ", keyboard = true) { service ->
        hideCompositionSpans = true; nativeKeyboard!!.setComposingRegion(0, 3)
        assertFalse(service.composing); assertTrue(service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello"))
        assertEquals("typ hello", field!!.text.toString())
    }

    @Test fun switchingFieldsCannotAdoptTheNewTargetDuringDictation() = withField(keyboard = true) { service ->
        assertTrue(service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello"))
        focused = null
        assertFalse(service.bridge.sameTarget())
        assertEquals(EditorWriteResult.USER_EDIT, write(service, "hello there"))
        assertEquals("hello", field!!.text.toString())
    }

    @Test fun cancelAfterKeyboardInteractionCannotUndoUserText() = withField { service ->
        assertTrue(service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello"))
        field!!.append(" typed"); field!!.setSelection(field!!.length())
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello there"))
        var restored: Boolean? = null; service.bridge.cancel { restored = it }
        assertEquals(false, restored); assertEquals("hello typed there", field!!.text.toString())
    }
}
