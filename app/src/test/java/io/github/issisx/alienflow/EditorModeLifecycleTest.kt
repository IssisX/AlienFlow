package io.github.issisx.alienflow

import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs production input lifecycle callbacks; injected state represents a preceding Termux session. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class)
class EditorModeLifecycleTest {
    @Test fun termuxModeDoesNotSurviveKeyboardFinishOrNewEditorStart() {
        val service = Robolectric.buildService(AlienAccessibilityService::class.java).create().get()
        val bridge = EditorBridge(service)
        service.javaClass.getDeclaredField("bridge").apply { isAccessible = true }.set(service, bridge)
        val mode = bridge.javaClass.getDeclaredField("isTerminal").apply { isAccessible = true }
        mode.setBoolean(bridge, true)
        val input = service.onCreateInputMethod()
        input.onFinishInput()
        assertFalse(bridge.isTerminal)
        mode.setBoolean(bridge, true)
        input.onStartInput(EditorInfo().apply { packageName = "com.android.chrome" }, false)
        assertFalse(bridge.isTerminal)
    }
}
