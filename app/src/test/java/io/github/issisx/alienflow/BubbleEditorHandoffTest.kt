package io.github.issisx.alienflow

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
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
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

/** Actual service, lifecycle callbacks and attached overlay. Only Android's remote focus/cache
 * broker and EditorInfo delivery are simulated; this is not installed Termux/Fold6 proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class, shadows = [BubbleEditorHandoffTest.Broker::class])
class BubbleEditorHandoffTest {
    @Implements(AccessibilityService::class)
    class Broker : ShadowAccessibilityService() {
        companion object {
            var live: AccessibilityNodeInfo? = null
            var cached: AccessibilityNodeInfo? = null
            var ime: InputMethod? = null
            var queries = 0
            var clears = 0
        }
        @Implementation fun findFocus(type: Int): AccessibilityNodeInfo? {
            if (type != AccessibilityNodeInfo.FOCUS_INPUT) return null
            queries++
            return cached ?: live
        }
        @Implementation fun getInputMethod(): InputMethod? = ime
        @Implementation fun clearCache(): Boolean { cached = null; clears++; return true }
    }
    @Before fun resetBroker() {
        Broker.live = null; Broker.cached = null; Broker.ime = null; Broker.queries = 0; Broker.clears = 0
        val app = org.robolectric.RuntimeEnvironment.getApplication() as AlienApp
        app.store.prefs.edit().putBoolean("always", false).commit()
    }
    private fun node(pkg: String, terminal: Boolean = false, password: Boolean = false) =
        AccessibilityNodeInfo.obtain().apply {
            packageName = pkg
            className = if (terminal) "com.termux.view.TerminalView" else "android.widget.EditText"
            isEditable = !terminal
            isPassword = password
            inputType = if (terminal) InputType.TYPE_NULL else InputType.TYPE_CLASS_TEXT
        }
    private inner class Connected : AutoCloseable {
        val controller = Robolectric.buildService(AlienAccessibilityService::class.java).create()
        val service = controller.get()
        val ime = service.onCreateInputMethod().also { Broker.ime = it }
        init {
            service.javaClass.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
        }
        val bubble: View get() = service.javaClass.getDeclaredField("bubble").apply { isAccessible = true }.get(service) as View
        fun start(pkg: String, terminal: Boolean = false) {
            val info = EditorInfo().apply { packageName = pkg; inputType = if (terminal) InputType.TYPE_NULL else InputType.TYPE_CLASS_TEXT }
            shadowOf(service).startInput(info)
        }
        fun finishInput() {
            ReflectionHelpers.callInstanceMethod<Any?>(ime, "doFinishInput")
        }
        fun inputFromTermux() {
            Broker.live = node("com.termux", terminal = true)
            start("com.termux", terminal = true)
            ui().idle(); assertEquals(View.VISIBLE, bubble.visibility)
            // Represents the previous completed dictation's binding, not a fake recording.
            service.bridge.javaClass.getDeclaredField("isTerminal").apply { isAccessible = true }.setBoolean(service.bridge, true)
        }
        override fun close() { controller.destroy(); ui().idle() }
    }
    companion object { private fun ui() = shadowOf(Looper.getMainLooper()) }

    @Test fun delayedDocumentTreeAfterTermuxReturnsTheSameNonfocusableBubble() {
        Connected().use { c ->
            c.inputFromTermux(); val original = c.bubble
            Broker.cached = Broker.live; Broker.live = null
            c.finishInput(); c.start("com.android.chrome"); ui().idle()
            assertFalse(c.service.bridge.isTerminal); assertTrue(Broker.clears >= 2)
            assertEquals(View.GONE, original.visibility)
            ui().idleFor(Duration.ofMillis(199))
            Broker.live = node("com.android.chrome")
            ui().idleFor(Duration.ofMillis(401))
            assertSame(original, c.bubble); assertTrue(original.isAttachedToWindow)
            assertEquals(View.VISIBLE, original.visibility)
            val flags = (original.layoutParams as WindowManager.LayoutParams).flags
            assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            assertTrue((org.robolectric.RuntimeEnvironment.getApplication() as AlienApp).crashJournal.diagnostic().contains("eligible_input=true; visible=true"))
        }
    }
    @Test fun keyboardSelectionUpdateRecoversAFieldPublishedAfterTheHandoffBurst() {
        Connected().use { c ->
            c.inputFromTermux(); Broker.live = null
            c.finishInput(); c.start("io.github.issisx.alienflow")
            ui().idleFor(Duration.ofSeconds(1)); assertEquals(View.GONE, c.bubble.visibility)
            Broker.live = node("io.github.issisx.alienflow")
            c.ime.onUpdateSelection(0, 0, 1, 1, -1, -1); ui().idle()
            assertEquals(View.VISIBLE, c.bubble.visibility)
            assertFalse(c.service.bridge.isTerminal)
        }
    }
    @Test fun declaredContentChangeEventsRecoverLaterBrowserFields() {
        Connected().use { c ->
            c.service.resources.getXml(R.xml.accessibility_service).use { parser ->
                parser.nextTag()
                val attrs = c.service.resources.obtainAttributes(parser, intArrayOf(android.R.attr.accessibilityEventTypes))
                try { assertTrue(attrs.getInt(0, 0) and AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED != 0) }
                finally { attrs.recycle() }
            }
            c.inputFromTermux(); Broker.live = null
            c.finishInput(); c.start("com.android.chrome"); ui().idleFor(Duration.ofSeconds(1))
            Broker.live = node("com.android.chrome")
            c.service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
            ui().idle(); assertEquals(View.VISIBLE, c.bubble.visibility)
        }
    }
    @Test fun absenceAndPasswordInputsNeverBecomeVisibleBecauseATimerExpired() {
        Connected().use { c ->
            c.start("com.example.editor"); ui().idleFor(Duration.ofSeconds(1))
            assertEquals(View.GONE, c.bubble.visibility)
            Broker.live = node("com.example.editor", password = true)
            c.ime.onUpdateSelection(0, 0, 1, 1, -1, -1)
            ui().idleFor(Duration.ofSeconds(1)); assertEquals(View.GONE, c.bubble.visibility)
        }
    }
    @Test fun explicitHideStaysHiddenAcrossARealEligibleFieldChange() {
        Connected().use { c ->
            c.service.javaClass.getDeclaredField("hidden").apply { isAccessible = true }.setBoolean(c.service, true)
            Broker.live = node("com.android.chrome"); c.start("com.android.chrome")
            ui().idleFor(Duration.ofSeconds(1)); assertEquals(View.GONE, c.bubble.visibility)
        }
    }
    @Test fun availabilityRechecksEndAndAreRemovedWhenTheServiceDies() {
        val c = Connected()
        c.start("com.android.chrome"); ui().idleFor(Duration.ofSeconds(1))
        val settled = Broker.queries
        ui().idleFor(Duration.ofSeconds(10)); assertEquals(settled, Broker.queries)
        c.start("com.android.chrome"); c.close()
        val destroyed = Broker.queries
        ui().idleFor(Duration.ofSeconds(10)); assertEquals(destroyed, Broker.queries)
    }
}
