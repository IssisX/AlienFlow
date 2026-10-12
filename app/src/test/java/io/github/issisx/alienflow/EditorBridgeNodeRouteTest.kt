package io.github.issisx.alienflow

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.os.Bundle
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
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

/** Real bridge/service/native widget. Only the OS broker/node IPC is simulated; no phone claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class, shadows = [EditorBridgeNodeRouteTest.Broker::class, EditorBridgeNodeRouteTest.NodeIpc::class])
class EditorBridgeNodeRouteTest {
    companion object { var field: EditText? = null; var focused: AccessibilityNodeInfo? = null; var mutations = 0 }
    @Implements(AccessibilityService::class)
    class Broker : ShadowAccessibilityService() {
        @Implementation fun findFocus(type: Int) = if (type == AccessibilityNodeInfo.FOCUS_INPUT) focused else null
        @Implementation fun getInputMethod(): android.accessibilityservice.InputMethod? = null
    }
    @Implements(AccessibilityNodeInfo::class)
    class NodeIpc : ShadowAccessibilityNodeInfo() {
        @RealObject private lateinit var actual: AccessibilityNodeInfo
        @Implementation fun refresh(): Boolean {
            val source = field?.createAccessibilityNodeInfo() ?: return false
            actual.text = source.text; actual.isShowingHintText = source.isShowingHintText
            actual.setTextSelection(source.textSelectionStart, source.textSelectionEnd)
            return true
        }
        @Implementation fun performAction(action: Int, arguments: Bundle?): Boolean {
            mutations++
            return field?.performAccessibilityAction(action, arguments) ?: false
        }
    }
    @Before fun reset() { field = null; focused = null; mutations = 0 }
    @Test fun coldNormalFieldWorksWithoutAnImeConnectionAndKeepsItsOwnedRange() {
        Robolectric.buildActivity(Activity::class.java).setup().use { activity ->
            field = EditText(activity.get()).apply { setText("left selected right"); setSelection(5, 13) }
            activity.get().setContentView(field); field!!.requestFocus()
            focused = field!!.createAccessibilityNodeInfo()
            val controller = Robolectric.buildService(AlienAccessibilityService::class.java).create()
            val service = controller.get()
            service.javaClass.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
            val app = service.application as AlienApp
            val active = app.coordinator.javaClass.getDeclaredField("active").apply { isAccessible = true }
            assertNull(service.inputMethod)
            assertTrue(service.bridge.lastError, service.bridge.begin())
            active.setBoolean(app.coordinator, true)
            var outcome: EditorWriteResult? = null
            service.bridge.write("hello") { outcome = it }; shadowOf(Looper.getMainLooper()).idle()
            assertEquals(EditorWriteResult.APPLIED, outcome)
            assertEquals("left hello right", field!!.text.toString())
            // Same-editor restart must not revoke confirmed ownership.
            service.bridge.inputStarted(EditorInfo().apply { packageName = focused!!.packageName.toString() }, true)
            assertTrue(service.bridge.valid())
            service.bridge.write("Hello.") { outcome = it }; shadowOf(Looper.getMainLooper()).idle()
            assertEquals(EditorWriteResult.APPLIED, outcome)
            assertEquals("left Hello. right", field!!.text.toString())
            val prior = mutations
            field!!.setSelection(1)
            service.bridge.write("unsafe overwrite") { outcome = it }; shadowOf(Looper.getMainLooper()).idle()
            assertEquals(EditorWriteResult.USER_EDIT, outcome); assertEquals(prior, mutations)
            active.setBoolean(app.coordinator, false)
            controller.destroy()
        }
    }
}
