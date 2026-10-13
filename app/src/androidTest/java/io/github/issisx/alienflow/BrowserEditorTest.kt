package io.github.issisx.alienflow

import android.app.UiAutomation
import android.graphics.Rect
import android.content.Context
import android.widget.EditText
import android.view.inputmethod.InputMethodManager
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityWindowInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Disposable emulator only. Real installed Termux, real overlay tap, real accessibility
 * broker/InputConnection and real terminal output. Speech events are injected fixtures;
 * microphone and paid provider are deliberately NOT exercised by this editor test. */
@RunWith(AndroidJUnit4::class)
class BrowserEditorTest {
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
        while (SystemClock.elapsedRealtime() < end) {
            if (test()) return
            Thread.sleep(100)
        }
        error("Timed out: $label; status=${main { app.coordinator.status }}")
    }
    private fun text(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        if (node.packageName?.toString() == "com.termux" && node.childCount == 0)
            return node.contentDescription?.toString().orEmpty() + node.text?.toString().orEmpty()
        return (0 until node.childCount).joinToString("\n") { text(node.getChild(it)) }
    }
    private fun terminalText(): String = automation.windows
        .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
        .joinToString("\n") { text(it.root) }
    private var tapNumber = 0
    private val evidenceDirectory get() = File(app.getExternalFilesDir(null), "browser-proof").apply { mkdirs() }
    private fun tapAlien(service: AlienAccessibilityService) {
        val trace = StringBuilder()
        var prior: Rect? = null
        var stable = 0
        var target: Rect? = null
        // Window/input focus can become ready before the IME and overlay finish moving.
        // Select the actual topmost touch target, not merely View.isShown at stale coordinates.
        await("stable, topmost alien touch target", 10_000) {
            val bounds = main {
                val field = service.javaClass.getDeclaredField("bubble").apply { isAccessible = true }
                val view = field.get(service) as View
                if (!view.isShown || !view.isAttachedToWindow) return@main null
                val xy = IntArray(2); view.getLocationOnScreen(xy)
                Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
            }
            val windows = automation.windows.sortedByDescending { it.layer }
            val hit = bounds?.let { area -> windows.firstOrNull { window ->
                val region = android.graphics.Region(); window.getRegionInScreen(region)
                region.contains(area.centerX(), area.centerY())
            } }
            val canTap = bounds != null && hit?.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
            if (bounds != prior || !canTap) {
                trace.appendLine("time=${SystemClock.uptimeMillis()} view=$bounds hitType=${hit?.type}")
                windows.forEach { window ->
                    val area = Rect(); window.getBoundsInScreen(area)
                    trace.appendLine("window=${window.id} type=${window.type} layer=${window.layer} bounds=$area")
                }
            }
            stable = if (canTap && bounds == prior) stable + 1 else 0
            prior = bounds; target = bounds
            File(evidenceDirectory, "tap-${tapNumber + 1}-geometry.txt").writeText(trace.toString())
            stable >= 3
        }
        val point = checkNotNull(target)
        tapNumber++
        File(evidenceDirectory, "before-tap-$tapNumber.png").outputStream().use {
            automation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val now = SystemClock.uptimeMillis()
        fun inject(action: Int, time: Long) {
            val event = MotionEvent.obtain(now, time, action, point.exactCenterX(), point.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        inject(MotionEvent.ACTION_DOWN, now)
        inject(MotionEvent.ACTION_UP, now + 40)
    }
    private fun setField(owner: Any, name: String, value: Any?) {
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
    }
    private fun event(c: DictationCoordinator, type: String, eventId: String, item: String, value: String = "") {
        c.event(JSONObject().put("type", type).put("event_id", eventId).put("item_id", item).apply {
            if (type.endsWith("delta")) put("delta", value)
            if (type.endsWith("completed")) put("transcript", value)
        })
    }

    private fun js(activity: BrowserFixtureActivity, script: String): String {
        var value: String? = null
        main { activity.web.evaluateJavascript(script) { value = it } }
        await("JavaScript result") { value != null }
        return value!!
    }
    @Test fun traceRealChromiumEditorThroughTheAlien() {
        assertEquals("Disposable emulator required", "1", shell("getprop ro.kernel.qemu").trim())
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        shell("settings put secure enabled_accessibility_services io.github.issisx.alienflow/io.github.issisx.alienflow.AlienAccessibilityService")
        shell("settings put secure accessibility_enabled 1")
        await("real accessibility service", 30_000) { main { app.coordinator.service != null } }
        val service = main { app.coordinator.service!! }
        val coordinator = main {
            app.store.prefs.edit().clear().putString("correction", "A").commit()
            val c = DictationCoordinator(app, app.store, keyProvider = { "fixture-not-a-key" })
            setField(app, "coordinator", c); c.service = service; c
        }
        val audio = PendingAudio(File(app.cacheDir, "browser-editor-fixture.wav"))
        val idleTransport = RealtimeTransport(app, audio, coordinator,
            microphoneFactory = { error("Editor probe must never activate a microphone") })
        val result = JSONObject().put("source", BuildConfig.SOURCE_REVISION)
            .put("api", android.os.Build.VERSION.SDK_INT)
            .put("provider", "fixture; no microphone or API request")
        try {
            val monitor = instrumentation.addMonitor(BrowserFixtureActivity::class.java.name, null, false)
            main { app.startActivity(android.content.Intent(app, BrowserFixtureActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            val activity = monitor.waitForActivityWithTimeout(15_000) as? BrowserFixtureActivity
                ?: error("Browser fixture did not launch")
            instrumentation.removeMonitor(monitor)
            await("HTML document") { js(activity, "document.readyState") == "\"complete\"" }
            val point = main {
                val xy = IntArray(2); activity.web.getLocationOnScreen(xy)
                floatArrayOf(xy[0] + 80 * activity.resources.displayMetrics.density,
                    xy[1] + 120 * activity.resources.displayMetrics.density)
            }
            val now = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
                val touch = MotionEvent.obtain(now, now + index * 40L, action, point[0], point[1], 0)
                touch.source = InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(automation.injectInputEvent(touch, true)) } finally { touch.recycle() }
            }
            await("focused browser and keyboard") { main {
                service.bridge.focused()?.isEditable == true &&
                    service.inputMethod?.currentInputConnection != null
            } }
            // Direct observations from the real broker, before starting any dictation.
            val node = main { service.bridge.focused()!! }
            val connection = main { service.inputMethod!!.currentInputConnection!! }
            val surrounding = connection.getSurroundingText(128, 128, android.view.inputmethod.InputConnection.GET_TEXT_WITH_STYLES)
            result.put("initial_node", JSONObject().put("text", node.text?.toString())
                .put("selection", "${node.textSelectionStart}:${node.textSelectionEnd}")
                .put("hint", node.hintText?.toString()).put("showing_hint", node.isShowingHintText))
            result.put("initial_ime", JSONObject().put("text", surrounding?.text?.toString())
                .put("selection", "${surrounding?.selectionStart}:${surrounding?.selectionEnd}")
                .put("offset", surrounding?.offset))
            result.put("initial_dom", js(activity, "editor.innerText"))
            result.put("webview", android.webkit.WebView.getCurrentWebViewPackage()?.versionName)
            main { setField(coordinator, "transport", idleTransport) }
            tapAlien(service)
            await("real alien starts dictation") { main { coordinator.active } }
            main {
                coordinator.captured(); coordinator.connected(); coordinator.turnCommitted()
                event(coordinator, "input_audio_buffer.committed", "browser-ack", "browser")
                event(coordinator, "conversation.item.input_audio_transcription.delta", "browser-delta", "browser", "Browser fixture speech")
            }
            // Bound the probe; a rejected editor is evidence, not a passing delivery claim.
            val deadline = SystemClock.elapsedRealtime() + 10_000
            var delivered = false
            while (SystemClock.elapsedRealtime() < deadline) {
                delivered = js(activity, "editor.innerText").contains("Browser fixture speech")
                if (delivered || main { service.bridge.lastError.isNotEmpty() }) break
                Thread.sleep(100)
            }
            assertTrue("The reproduced AF-E10 must now deliver actual browser text", delivered)
            result.put("real_bubble_tap", true).put("delivered", delivered)
                .put("after_dom", js(activity, "editor.innerText"))
                .put("editor_error", main { service.bridge.lastError })
            main { event(coordinator, "conversation.item.input_audio_transcription.completed", "browser-final", "browser", "Browser final speech") }
            if (delivered) await("live final revision") { js(activity, "editor.innerText").contains("Browser final speech") }
            main { setField(coordinator, "transport", null) }
            tapAlien(service)
            await("dictation finish") { main { !coordinator.finishing && !coordinator.active } }
            result.put("final_dom", js(activity, "editor.innerText"))
                .put("status", main { coordinator.status })
            assertEquals("Browser final speech", org.json.JSONArray("[" + js(activity, "editor.innerText") + "]").getString(0).trimEnd('\n'))
            // Selected replacement in an existing browser draft, then real keyboard
            // typing in the same dictation. Previously dispatched speech becomes user-owned.
            main { service.inputMethod!!.currentInputConnection!! }.setSelection(8, 13)
            await("real browser selection") { main { service.imeSelection == EditorEvidence.Selection(8, 13) } }
            main { setField(coordinator, "transport", idleTransport) }
            tapAlien(service); await("second browser dictation") { main { coordinator.active } }
            main {
                coordinator.captured(); coordinator.connected(); coordinator.turnCommitted()
                event(coordinator, "input_audio_buffer.committed", "second-ack", "second")
                event(coordinator, "conversation.item.input_audio_transcription.delta", "second-delta", "second", "SECOND")
            }
            await("selected range replaced without losing draft anchors") {
                js(activity, "editor.innerText").contains("Browser SECOND speech") && main {
                    !coordinator.javaClass.getDeclaredField("writing").apply { isAccessible = true }.getBoolean(coordinator)
                }
            }
            shell("input text %styped")
            await("real keyboard text") { js(activity, "editor.innerText").contains("SECOND typed") }
            assertTrue("Typing must not stop recording", main { coordinator.active })
            main { event(coordinator, "conversation.item.input_audio_transcription.delta", "second-tail", "second", " tail") }
            await("speech continues after typing") { js(activity, "editor.innerText").contains("Browser SECOND typed tail speech") }
            main { event(coordinator, "conversation.item.input_audio_transcription.completed", "second-final", "second", "Second. tail") }
            main { setField(coordinator, "transport", null) }; tapAlien(service)
            await("second browser finish") { main { !coordinator.finishing && !coordinator.active } }
            assertTrue(js(activity, "editor.innerText").contains("Browser SECOND typed tail speech"))
            result.put("selected_replacement", true).put("typing_continues_dictation", true)
                .put("typed_text_preserved", true).put("second_dom", js(activity, "editor.innerText"))
            main { service.inputMethod!!.currentInputConnection!! }.setSelection(0, 0)
            await("browser cursor at start") { main { service.imeSelection == EditorEvidence.Selection(0, 0) } }
            main { setField(coordinator, "transport", idleTransport) }
            tapAlien(service); await("third browser dictation") { main { coordinator.active } }
            main {
                coordinator.captured(); coordinator.connected(); coordinator.turnCommitted()
                event(coordinator, "input_audio_buffer.committed", "third-ack", "third")
                event(coordinator, "conversation.item.input_audio_transcription.completed", "third-final", "third", "🛸 Again ")
            }
            await("repeat session preserves Unicode and existing draft") {
                js(activity, "editor.innerText").contains("Again Browser SECOND typed tail speech")
            }
            main { setField(coordinator, "transport", null) }; tapAlien(service)
            await("third browser finish") { main { !coordinator.finishing && !coordinator.active } }
            result.put("repeat_session", true).put("unicode_dom", js(activity, "editor.innerText"))

        } finally {
            File(evidenceDirectory, "result.json").writeText(result.toString(2))
            instrumentation.sendStatus(2, android.os.Bundle().apply { putString("fixture_result", result.toString()) })
            File(evidenceDirectory, "session-diagnostic.txt").writeText(DiagnosticReport.collect(app))
            File(evidenceDirectory, "last-screen.png").outputStream().use {
                automation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            main { coordinator.cancel(); idleTransport.close() }
            audio.close()
        }
    }
}

