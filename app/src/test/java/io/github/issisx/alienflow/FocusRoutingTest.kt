package io.github.issisx.alienflow

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityService

/** Simulated OS broker differentiates touched-window from keyboard focus.
 * Runs the production EditorBridge resolver, not the real device's accessibility broker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class, shadows = [FocusRoutingTest.FocusShadow::class])
class FocusRoutingTest {
    @Implements(AccessibilityService::class)
    class FocusShadow : ShadowAccessibilityService() {
        companion object { var input: AccessibilityNodeInfo? = null }
        @Implementation fun findFocus(type: Int): AccessibilityNodeInfo? =
            if (type == AccessibilityNodeInfo.FOCUS_INPUT) input else null
    }
    @Before fun reset() { FocusShadow.input = null }
    private fun input(password: Boolean = false) = AccessibilityNodeInfo.obtain().apply {
        isEditable = true; isPassword = password
    }
    @Test fun touchingOverlayDoesNotReplaceKeyboardFocusWithActiveWindow() {
        val service = Robolectric.buildService(AlienAccessibilityService::class.java).create().get()
        val actual = input(); FocusShadow.input = actual
        assertNull(service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
        assertEquals(actual, EditorBridge(service).focused())
    }
    @Test fun genuineFocusLossDoesNotFallBackToStaleEditor() {
        val service = Robolectric.buildService(AlienAccessibilityService::class.java).create().get()
        val bridge = EditorBridge(service); FocusShadow.input = input()
        assertNotNull(bridge.focused())
        FocusShadow.input = null
        assertNull(bridge.focused())
    }
    @Test fun protectedInputsAreStillExcluded() {
        val service = Robolectric.buildService(AlienAccessibilityService::class.java).create().get()
        FocusShadow.input = input(true)
        assertNull(EditorBridge(service).focused())
    }
}
