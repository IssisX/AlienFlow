package io.github.issisx.alienflow

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityService

/** Framework node/window trees + production resolver. Remote focus delivery is simulated, not installed ChatGPT. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [EditorFocusDiscoveryTest.Broker::class])
class EditorFocusDiscoveryTest {
    @Implements(AccessibilityService::class)
    class Broker : ShadowAccessibilityService() {
        companion object { var direct: AccessibilityNodeInfo? = null }
        @Implementation fun findFocus(type: Int) = if (type == AccessibilityNodeInfo.FOCUS_INPUT) direct else null
    }
    @Before fun reset() { Broker.direct = null }
    private fun node(editable: Boolean = false, focused: Boolean = false, password: Boolean = false) =
        AccessibilityNodeInfo(View(RuntimeEnvironment.getApplication())).apply {
            isEditable = editable; isFocused = focused; isVisibleToUser = true; isEnabled = true; isPassword = password
        }
    private fun window(root: AccessibilityNodeInfo, focused: Boolean = true, type: Int = AccessibilityWindowInfo.TYPE_APPLICATION) =
        AccessibilityWindowInfo().also { shadowOf(it).apply { setRoot(root); setFocused(focused); setType(type) } }
    private fun resolve(direct: AccessibilityNodeInfo?, windows: List<AccessibilityWindowInfo>) =
        EditorFocusDiscovery.resolve(direct, { windows }, { it.isEditable && !it.isPassword }, { it.isPassword })

    @Test fun directInputFocusDoesNotTraverseOrInspectTheTouchedWindow() {
        val field = node(true, true)
        val result = EditorFocusDiscovery.resolve(field, { error("No window traversal on the fast path") }, { it.isEditable }, { it.isPassword })
        assertEquals(field, result.node); assertTrue(result.direct); assertEquals(0, result.visited)
    }
    @Test fun virtualFocusedChildIsFoundWhenGlobalInputFocusReturnsItsContainer() {
        val root = node(focused = true); val field = node(true, true)
        shadowOf(root).addChild(node(true, false)); shadowOf(root).addChild(field)
        val service = Robolectric.buildService(AlienAccessibilityService::class.java).create().get()
        Broker.direct = root
        shadowOf(service).setWindows(listOf(window(root)))
        shadowOf(service).setRootInActiveWindow(node(true, true))
        assertEquals(field, EditorBridge(service).focused())
    }
    @Test fun emptyVirtualFieldDoesNotNeedASelectionActionToBeDiscovered() {
        val root = node(focused = true); val field = node(focused = true)
        field.text = ""; field.setTextSelection(0, 0)
        field.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT)
        assertFalse(field.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_SELECTION })
        shadowOf(root).addChild(field)
        val service = Robolectric.buildService(AlienAccessibilityService::class.java).create().get()
        Broker.direct = root; shadowOf(service).setWindows(listOf(window(root)))
        assertEquals(field, EditorBridge(service).focused())
    }
    @Test fun activeOverlayKeyboardAndUnfocusedApplicationNeverSupplyAField() {
        val wanted = node(true, true); val root = node()
        shadowOf(root).addChild(wanted)
        val wrong = node(true, true)
        val windows = listOf(window(wrong, type = AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY),
            window(wrong, type = AccessibilityWindowInfo.TYPE_INPUT_METHOD), window(wrong, focused = false), window(root))
        assertEquals(wanted, resolve(null, windows).node)
        assertNull(resolve(null, windows.dropLast(1)).node)
        wanted.isFocused = false
        assertNull(resolve(null, listOf(window(root))).node)
    }
    @Test fun protectedOrAmbiguousFocusIsRejectedWithoutSelectingAnotherEditableNode() {
        val root = node(); val first = node(true, true); val second = node(true, true)
        shadowOf(root).addChild(first); shadowOf(root).addChild(second)
        assertNull(resolve(null, listOf(window(root))).node)
        first.isPassword = true
        assertNull(resolve(first, listOf(window(second))).node)
        assertNull(resolve(null, listOf(window(root))).node)
    }
    @Test fun partialTreeCannotProveUniqueFocusAndTraversalIsBounded() {
        val root = node(); shadowOf(root).addChild(node(true, true))
        repeat(260) { shadowOf(root).addChild(node()) }
        val result = resolve(null, listOf(window(root)))
        assertNull(result.node); assertTrue(result.exhausted); assertEquals(256, result.visited)
    }
}
