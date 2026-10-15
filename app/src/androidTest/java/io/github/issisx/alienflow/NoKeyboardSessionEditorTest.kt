package io.github.issisx.alienflow

import android.app.UiAutomation
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Disposable Android 11–12 emulator only (for example Galaxy Note10+ class devices). Real overlay tap,
 * accessibility node IPC and native field; speech events are fixtures and no microphone is used. */
@RunWith(AndroidJUnit4::class)
class NoKeyboardSessionEditorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val app get() = instrumentation.targetContext.applicationContext as AlienApp
    private fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun await(label: String, timeout: Long = 15_000, test: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) { if (test()) return; Thread.sleep(100) }
        error("Timed out: $label; status=${main { app.coordinator.status }}")
    }
    private fun setField(owner: Any, name: String, value: Any?) {
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
    }
    /** Taps the alien only once it is the topmost window at its own, stable center. */
    private fun tapAlien(service: AlienAccessibilityService) {
        var target: Rect? = null
        val trace = StringBuilder()
        try { awaitTarget(service, trace) { bounds -> target = bounds } } catch (e: IllegalStateException) {
            throw IllegalStateException("${e.message}; windows=${trace.takeLast(1500)}")
        }
        val point = checkNotNull(target); val now = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to now, MotionEvent.ACTION_UP to now + 40)) {
            val event = MotionEvent.obtain(now, time, action, point.exactCenterX(), point.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }
    private fun awaitTarget(service: AlienAccessibilityService, trace: StringBuilder, settled: (Rect?) -> Unit) {
        var prior: Rect? = null; var stable = 0
        await("stable, topmost alien touch target", 10_000) {
            val bounds = main {
                val view = service.javaClass.getDeclaredField("bubble").apply { isAccessible = true }.get(service) as View
                if (!view.isShown || !view.isAttachedToWindow) return@main null
                val xy = IntArray(2); view.getLocationOnScreen(xy)
                Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
            }
            val hit = bounds?.let { area -> automation.windows.sortedByDescending { it.layer }.firstOrNull { window ->
                val region = android.graphics.Region(); window.getRegionInScreen(region)
                region.contains(area.centerX(), area.centerY())
            } }
            val canTap = bounds != null && hit?.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
            if (!canTap) {
                trace.clear().append("view=$bounds hitType=${hit?.type};")
                automation.windows.forEach { window -> val area = Rect(); window.getBoundsInScreen(area)
                    trace.append(" w${window.id}:t${window.type}:l${window.layer}:$area") }
            }
            stable = if (canTap && bounds == prior) stable + 1 else 0
            prior = bounds; settled(bounds)
            stable >= 3
        }
    }
    private fun event(c: DictationCoordinator, type: String, eventId: String, item: String, value: String = "") {
        c.event(JSONObject().put("type", type).put("event_id", eventId).put("item_id", item).apply {
            if (type.endsWith("delta")) put("delta", value)
            if (type.endsWith("completed")) put("transcript", value)
        })
    }

    @Test fun emptyFieldsReceiveLiveSpeechAndExistingTextIsRefusedWithItsReason() {
        assumeTrue("Android 13+ uses the keyboard session; see BrowserEditorTest", Build.VERSION.SDK_INT < 33)
        assertEquals("Refusing destructive test setup on a personal phone", "1", shell("getprop ro.kernel.qemu").trim())
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        shell("settings put secure enabled_accessibility_services io.github.issisx.alienflow/io.github.issisx.alienflow.AlienAccessibilityService")
        shell("settings put secure accessibility_enabled 1")
        await("real accessibility service bound", 30_000) { main { app.coordinator.service != null } }
        val service = main { app.coordinator.service!! }
        assertFalse(main { service.keyboardAttached })
        val coordinator = main {
            app.store.prefs.edit().clear().putString("correction", "A").commit()
            DictationCoordinator(app, app.store, keyProvider = { "fixture-not-a-key" }).also { setField(app, "coordinator", it); it.service = service }
        }
        val idleTransport = RealtimeTransport(app, PendingAudio(File(app.cacheDir, "no-keyboard-session-fixture.wav")), coordinator,
            microphoneFactory = { error("Editor integration must never activate a microphone") })
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        shell("am start -n io.github.issisx.alienflow/.MainActivity")
        val activity = monitor.waitForActivityWithTimeout(15_000) as? MainActivity ?: error("AlienFlow main activity did not open")
        instrumentation.removeMonitor(monitor)
        fun focusPractice(text: String) = main {
            val field = activity.window.decorView.findViewWithTag<EditText>("practice") ?: error("The real practice field is missing")
            field.setText(text); field.setSelection(field.length()); field.requestFocus()
            field.requestRectangleOnScreen(Rect(0, 0, field.width, field.height), true)
            activity.getSystemService(InputMethodManager::class.java).showSoftInput(field, 0)
            setField(coordinator, "transport", idleTransport)
            field
        }
        val practice = focusPractice("")
        await("empty practice field focused") { main { service.bridge.focused()?.packageName?.toString() == app.packageName } }
        tapAlien(service)
        await("dictation started by the actual bubble") { main { coordinator.active } }
        main {
            coordinator.captured(); coordinator.connected(); coordinator.turnCommitted()
            event(coordinator, "input_audio_buffer.committed", "a12-ack", "a12")
            event(coordinator, "conversation.item.input_audio_transcription.delta", "a12-delta", "a12", "LIVE_WORDS")
        }
        await("live words in the empty field before Stop") { main { practice.text.toString() == "LIVE_WORDS" } }
        main { event(coordinator, "conversation.item.input_audio_transcription.completed", "a12-final", "a12", "Live words.") }
        await("live final revision") { main { practice.text.toString() == "Live words." } }
        main { setField(coordinator, "transport", null) }
        tapAlien(service)
        await("finish") { main { !coordinator.finishing && !coordinator.active } }
        assertEquals("Live words.", main { practice.text.toString() })

        focusPractice("EXISTING_DRAFT")
        main { setField(coordinator, "transport", null) }
        await("draft field focused") { main { service.bridge.focused()?.packageName?.toString() == app.packageName } }
        tapAlien(service)
        await("explicit pre-Android 13 refusal") { main { coordinator.status.startsWith("[AF-E17]") } }
        assertFalse(main { coordinator.active })
        assertEquals("EXISTING_DRAFT", main { practice.text.toString() })

        val result = JSONObject().put("source", BuildConfig.SOURCE_REVISION).put("version", BuildConfig.VERSION_NAME)
            .put("api", Build.VERSION.SDK_INT).put("empty_field_live", true).put("existing_text_refused", true)
        instrumentation.sendStatus(2, android.os.Bundle().apply { putString("fixture_result", result.toString()) })
    }
}
