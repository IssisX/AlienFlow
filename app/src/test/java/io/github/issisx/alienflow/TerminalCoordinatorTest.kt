package io.github.issisx.alienflow

import android.Manifest
import android.accessibilityservice.InputMethod
import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.SurroundingText
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityNodeInfo
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.concurrent.TimeUnit

/** Public coordinator lifecycle, production bridge and native consume/clear input.
 * Accessibility broker/IPC and recognition events are fixtures, not installed Termux proof.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class, shadows = [EditorBridgeNodeRouteTest.Broker::class,
    EditorBridgeNodeRouteTest.Ime::class, EditorBridgeNodeRouteTest.KeyboardIpc::class, TerminalCoordinatorTest.NodeIpc::class])
class TerminalCoordinatorTest {
    @Implements(AccessibilityNodeInfo::class)
    class NodeIpc : ShadowAccessibilityNodeInfo() {
        @Implementation override fun refresh() = true
    }
    private fun withTerminal(check: (DictationCoordinator, AlienAccessibilityService, StringBuilder, () -> Int) -> Unit) {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as AlienApp
        app.store.prefs.edit().clear().putString("correction", "A").commit()
        val coordinator = DictationCoordinator(app, app.store, keyProvider = { "fixture-not-a-key" })
        ReflectionHelpers.setField(app, "coordinator", coordinator)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        Robolectric.buildActivity(Activity::class.java).setup().use { activity ->
            val terminal = StringBuilder("existing draft "); var commits = 0
            val node = AccessibilityNodeInfo.obtain().apply {
                className = "android.view.View"; packageName = "com.termux"
                isFocused = true; isFocusable = true; isEditable = false
            }
            EditorBridgeNodeRouteTest.focused = node
            EditorBridgeNodeRouteTest.field = null
            EditorBridgeNodeRouteTest.committedTexts.clear()
            EditorBridgeNodeRouteTest.selections.clear()
            EditorBridgeNodeRouteTest.nativeKeyboard = object : BaseInputConnection(View(activity.get()), true) {
                override fun getSurroundingText(beforeLength: Int, afterLength: Int, flags: Int): SurroundingText? =
                    error("Terminal must not use document readback")
                override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                    super.commitText(text, newCursorPosition)
                    val buffer = checkNotNull(editable)
                    terminal.append(buffer); buffer.clear(); commits++
                    return true
                }
            }
            EditorBridgeNodeRouteTest.info = EditorInfo().apply { packageName = "com.termux"; inputType = 0; fieldId = 73 }
            val controller = Robolectric.buildService(AlienAccessibilityService::class.java).create()
            val service = controller.get()
            EditorBridgeNodeRouteTest.serviceForCallbacks = service
            val inputMethod = InputMethod(service)
            EditorBridgeNodeRouteTest.inputMethod = inputMethod
            @Suppress("UNCHECKED_CAST")
            val remoteClass = Class.forName("com.android.internal.inputmethod.RemoteAccessibilityInputConnection") as Class<Any>
            EditorBridgeNodeRouteTest.connection = ReflectionHelpers.callConstructor(InputMethod.AccessibilityInputConnection::class.java,
                ClassParameter.from(InputMethod::class.java, inputMethod), ClassParameter.from(remoteClass, null))
            service.javaClass.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
            try {
                coordinator.start()
                assertTrue(coordinator.status, coordinator.active)
                assertTrue(service.bridge.isTerminal)
                check(coordinator, service, terminal) { commits }
            } finally {
                coordinator.cancel(); controller.destroy()
                EditorBridgeNodeRouteTest.focused = null; EditorBridgeNodeRouteTest.nativeKeyboard = null
                EditorBridgeNodeRouteTest.inputMethod = null; EditorBridgeNodeRouteTest.connection = null
                EditorBridgeNodeRouteTest.info = null; EditorBridgeNodeRouteTest.serviceForCallbacks = null
            }
        }
    }
    private fun event(coordinator: DictationCoordinator, type: String, id: String, item: String, text: String = "") {
        val value = JSONObject().put("type", type).put("event_id", id).put("item_id", item)
        if (type.endsWith("delta")) value.put("delta", text)
        if (type.endsWith("completed")) value.put("transcript", text)
        coordinator.event(value)
    }
    private fun complete(coordinator: DictationCoordinator) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (coordinator.finishing && System.nanoTime() < until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        assertFalse(coordinator.status, coordinator.finishing)
    }
    @Test fun accessibilityEventsDuringSpeechKeepTheTerminalBoundAndFinalTextCommitsOnce() = withTerminal { coordinator, service, terminal, commits ->
        coordinator.turnCommitted()
        event(coordinator, "input_audio_buffer.committed", "ack", "one")
        event(coordinator, "conversation.item.input_audio_transcription.delta", "delta", "one", "Cory's café 🛸")
        coordinator.targetEvent()
        assertSame(service.bridge, ReflectionHelpers.getField<EditorBridge?>(coordinator, "editor"))
        assertEquals(0, commits())
        event(coordinator, "conversation.item.input_audio_transcription.completed", "final", "one", "Cory's café 🛸\nkeep $42")
        coordinator.targetEvent()
        assertEquals(0, commits()); assertEquals("existing draft ", terminal.toString())
        coordinator.stop(); complete(coordinator)
        assertEquals("existing draft Cory's café 🛸 keep $42", terminal.toString())
        assertEquals(1, commits()); assertFalse(terminal.any { it.isISOControl() })
        assertEquals("Sent to Termux • press Enter yourself", coordinator.status)
        coordinator.targetEvent(); assertEquals(1, commits())
    }
    @Test fun finishingEventsCannotSendPartialTerminalTextBeforeTheLastTranscript() = withTerminal { coordinator, _, terminal, commits ->
        repeat(2) { coordinator.turnCommitted() }
        event(coordinator, "input_audio_buffer.committed", "ack1", "one")
        event(coordinator, "conversation.item.input_audio_transcription.completed", "final1", "one", "first")
        event(coordinator, "input_audio_buffer.committed", "ack2", "two")
        event(coordinator, "conversation.item.input_audio_transcription.delta", "delta2", "two", "unfinished")
        coordinator.stop()
        coordinator.targetEvent()
        assertEquals("existing draft ", terminal.toString()); assertEquals(0, commits())
        event(coordinator, "conversation.item.input_audio_transcription.completed", "final2", "two", "second")
        complete(coordinator)
        assertEquals("existing draft first second", terminal.toString()); assertEquals(1, commits())
    }
    @Test fun terminalFocusLossPreservesTheTranscriptWithoutCommittingToAnotherField() = withTerminal { coordinator, _, terminal, commits ->
        coordinator.turnCommitted()
        event(coordinator, "input_audio_buffer.committed", "ack", "one")
        event(coordinator, "conversation.item.input_audio_transcription.completed", "final", "one", "words preserved")
        EditorBridgeNodeRouteTest.focused = null
        coordinator.targetEvent(); coordinator.stop(); complete(coordinator)
        assertEquals("existing draft ", terminal.toString()); assertEquals(0, commits())
        assertEquals("words preserved", coordinator.result)
    }
}
