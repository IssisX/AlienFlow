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
        var brokenHint = false; val selections = mutableListOf<Pair<Int, Int>>()
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
        @Implementation fun getSurroundingText(before: Int, after: Int, flags: Int): SurroundingText? = nativeKeyboard?.getSurroundingText(before, after, flags)
        @Implementation fun setSelection(start: Int, end: Int) { selections.add(start to end); nativeKeyboard!!.setSelection(start, end) }
        @Implementation fun commitText(text: CharSequence, cursor: Int, attributes: TextAttribute?) { nativeKeyboard!!.commitText(text, cursor) }
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
                actual.text = source.text; actual.isShowingHintText = source.isShowingHintText
                actual.setTextSelection(source.textSelectionStart, source.textSelectionEnd)
            }
            return true
        }
        @Implementation override fun performAction(action: Int, arguments: Bundle?): Boolean {
            mutations++
            return field?.performAccessibilityAction(action, arguments) ?: false
        }
    }
    @Before fun reset() {
        field = null; focused = null; mutations = 0; nativeKeyboard = null; inputMethod = null
        connection = null; info = null; brokenHint = false; selections.clear()
    }
    private fun withField(text: String = "", hint: String = "", keyboard: Boolean = false, block: (AlienAccessibilityService) -> Unit) {
        Robolectric.buildActivity(Activity::class.java).setup().use { activity ->
            field = EditText(activity.get()).apply { this.hint = hint; setText(text); setSelection(text.length) }
            activity.get().setContentView(field); field!!.requestFocus()
            focused = field!!.createAccessibilityNodeInfo()
            val controller = Robolectric.buildService(AlienAccessibilityService::class.java).create()
            val service = controller.get()
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
        while (result == null && System.currentTimeMillis() < until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        assertNotNull("Editor operation did not complete", result)
        return result
    }
    @Test fun coldEmptyFieldWorksWithoutAnImeAndPreservesItsOwnedTextAcrossRestart() = withField { service ->
        assertNull(service.inputMethod); assertTrue(service.bridge.lastError, service.bridge.begin()); activate(service)
        assertEquals(EditorWriteResult.APPLIED, write(service, "hello")); assertEquals("hello", field!!.text.toString())
        service.bridge.inputStarted(EditorInfo().apply { packageName = focused!!.packageName.toString() }, true)
        assertTrue(service.bridge.valid())
        assertEquals(EditorWriteResult.APPLIED, write(service, "Hello.")); assertEquals("Hello.", field!!.text.toString())
        val prior = mutations; field!!.setSelection(1)
        assertEquals(EditorWriteResult.USER_EDIT, write(service, "unsafe overwrite")); assertEquals(prior, mutations)
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
}
