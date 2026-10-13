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
class InstalledTermuxTest {
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
    private val evidenceDirectory get() = File(app.getExternalFilesDir(null), "termux-proof").apply { mkdirs() }
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
    @Test fun actualTerminalReceivesOnlyFinalTextOnceThroughTheFloatingBubble() {
        assertEquals("Refusing destructive test setup on a personal phone", "1", shell("getprop ro.kernel.qemu").trim())
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        shell("settings put secure enabled_accessibility_services io.github.issisx.alienflow/io.github.issisx.alienflow.AlienAccessibilityService")
        shell("settings put secure accessibility_enabled 1")
        await("real accessibility service bound", 30_000) { main { app.coordinator.service != null } }
        val service = main { app.coordinator.service!! }
        // Fixture at the untested recognition/capture boundary; no HTTP connection is opened.
        val coordinator = main {
            app.store.prefs.edit().clear().putString("correction", "A").commit()
            val c = DictationCoordinator(app, app.store, keyProvider = { "fixture-not-a-key" })
            setField(app, "coordinator", c); c.service = service; c
        }
        val audio = PendingAudio(File(app.cacheDir, "terminal-editor-fixture.wav"))
        val idleTransport = RealtimeTransport(app, audio, coordinator,
            microphoneFactory = { error("Editor integration must never activate a microphone") })
        main { setField(coordinator, "transport", idleTransport) }
        try {
            shell("am start -n com.termux/.app.TermuxActivity")
            await("Termux bootstrap and shell", 90_000) { terminalText().contains("$") }
            shell("input text cat"); shell("input keyevent 66")
            shell("input text AF_SEED_")
            await("existing terminal input") { terminalText().contains("AF_SEED_") }
            await("Termux keyboard connection") { main {
                service.bridge.focused()?.packageName?.toString() == "com.termux" &&
                    service.inputMethod?.currentInputConnection != null
            } }
            tapAlien(service)
            await("dictation started by the actual bubble") { main { coordinator.active } }
            main {
                assertTrue(service.bridge.isTerminal)
                coordinator.captured(); coordinator.connected()
                repeat(2) { coordinator.turnCommitted() }
                event(coordinator, "input_audio_buffer.committed", "ack1", "one")
                event(coordinator, "conversation.item.input_audio_transcription.delta", "d1", "one", "CHAT_PARTIAL")
                coordinator.targetEvent()
                event(coordinator, "conversation.item.input_audio_transcription.completed", "f1", "one", "CHAT_FIRST")
                event(coordinator, "input_audio_buffer.committed", "ack2", "two")
                event(coordinator, "conversation.item.input_audio_transcription.delta", "d2", "two", "CHAT_UNFINISHED")
                coordinator.targetEvent()
            }
            Thread.sleep(300)
            assertFalse("Premature partial text reached real Termux", terminalText().contains("CHAT_"))
            // The never-started transport is only a startup seam fixture. Drain is tested separately.
            main { setField(coordinator, "transport", null) }
            tapAlien(service)
            await("stop awaits last recognition") { main { coordinator.finishing } }
            main { coordinator.targetEvent() }
            assertFalse("Finishing event committed too early", terminalText().contains("CHAT_"))
            main { event(coordinator, "conversation.item.input_audio_transcription.completed", "f2", "two", "CHAT_LAST\nEND") }
            await("final delivery completes") { main { !coordinator.finishing } }
            await("actual terminal displays complete appended transcript") {
                terminalText().contains("AF_SEED_CHAT_FIRST CHAT_LAST END")
            }
            repeat(3) { main { coordinator.targetEvent() }; Thread.sleep(100) }
            val observed = terminalText()
            assertEquals("Duplicate terminal send or unintended Enter", 1, Regex("CHAT_FIRST").findAll(observed).count())
            assertFalse(observed.contains("CHAT_PARTIAL")); assertFalse(observed.contains("CHAT_UNFINISHED"))
            assertEquals("Sent to Termux • press Enter yourself", main { coordinator.status })
            // Real transition away from Termux: the ordinary field must regain a bubble
            // and retain live updates, not inherit the terminal's end-only policy.
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            shell("am start -n io.github.issisx.alienflow/.MainActivity")
            val activity = monitor.waitForActivityWithTimeout(15_000) as? MainActivity
                ?: error("AlienFlow main activity did not open")
            instrumentation.removeMonitor(monitor)
            val practice = main {
                val field = activity.window.decorView.findViewWithTag<EditText>("practice")
                    ?: error("The real practice field is missing")
                field.setText("DOC_SEED "); field.setSelection(field.length()); field.requestFocus()
                field.requestRectangleOnScreen(Rect(0, 0, field.width, field.height), true)
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(field, 0)
                setField(coordinator, "transport", idleTransport)
                field
            }
            await("ordinary field replaces terminal focus") { main {
                service.bridge.focused()?.packageName?.toString() == app.packageName &&
                    service.inputMethod?.currentInputConnection != null
            } }
            tapAlien(service)
            await("ordinary dictation starts after terminal") { main { coordinator.active } }
            main {
                assertFalse(service.bridge.isTerminal)
                coordinator.captured(); coordinator.connected(); coordinator.turnCommitted()
                event(coordinator, "input_audio_buffer.committed", "doc-ack", "doc")
                event(coordinator, "conversation.item.input_audio_transcription.delta", "doc-delta", "doc", "DOC_PARTIAL")
            }
            await("ordinary live text before Stop") { main { practice.text.toString() == "DOC_SEED DOC_PARTIAL" } }
            assertTrue(main { coordinator.active })
            main { event(coordinator, "conversation.item.input_audio_transcription.completed", "doc-final", "doc", "DOC_FINAL") }
            await("ordinary live final revision") { main { practice.text.toString() == "DOC_SEED DOC_FINAL" } }
            main { setField(coordinator, "transport", null) }
            tapAlien(service)
            await("ordinary finish") { main { !coordinator.finishing } }
            assertEquals("DOC_SEED DOC_FINAL", main { practice.text.toString() })

            // Return to the same installed terminal without toggling accessibility.
            // A second dictation must allocate fresh one-shot insertion ownership.
            shell("am start -n com.termux/.app.TermuxActivity")
            await("terminal focus restored") { main {
                service.bridge.focused()?.packageName?.toString() == "com.termux" &&
                    service.inputMethod?.currentInputConnection != null
            } }
            main { setField(coordinator, "transport", idleTransport) }
            tapAlien(service)
            await("repeat terminal session starts") { main { coordinator.active } }
            main {
                assertTrue(service.bridge.isTerminal)
                coordinator.captured(); coordinator.connected(); coordinator.turnCommitted()
                event(coordinator, "input_audio_buffer.committed", "repeat-ack", "repeat")
                event(coordinator, "conversation.item.input_audio_transcription.delta", "repeat-delta", "repeat", "NEVER_PARTIAL")
                coordinator.targetEvent()
            }
            assertFalse(terminalText().contains("NEVER_PARTIAL"))
            main { event(coordinator, "conversation.item.input_audio_transcription.completed", "repeat-final", "repeat", "_SECOND_PASS") }
            main { setField(coordinator, "transport", null) }
            tapAlien(service)
            await("repeat terminal final insertion") { main { !coordinator.finishing } && terminalText().contains("END_SECOND_PASS") }
            val repeated = terminalText()
            assertEquals(1, Regex("CHAT_FIRST").findAll(repeated).count())
            assertEquals(1, Regex("SECOND_PASS").findAll(repeated).count())
            assertFalse(repeated.contains("NEVER_PARTIAL"))
            val directory = File(app.getExternalFilesDir(null), "termux-proof").apply { mkdirs() }
            File(directory, "terminal.png").outputStream().use { automation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val result = JSONObject()
                .put("source", BuildConfig.SOURCE_REVISION).put("version", BuildConfig.VERSION_NAME)
                .put("api", android.os.Build.VERSION.SDK_INT).put("termux", "official v0.118.3 x86_64")
                .put("real_bubble_tap", true).put("real_accessibility_broker", true)
                .put("real_terminal_echo", true).put("final_count", 1)
                .put("repeat_session_count", 1).put("ordinary_live_after_termux", true)
                .put("termux_visible_after_switch", true).put("premature_text", false).put("microphone_and_provider", "not exercised; transcript fixtures")
                .toString()
            File(directory, "result.json").writeText(result)
            instrumentation.sendStatus(2, android.os.Bundle().apply { putString("fixture_result", result) })
        } finally {
            File(evidenceDirectory, "session-diagnostic.txt").writeText(DiagnosticReport.collect(app))
            File(evidenceDirectory, "before-instrumentation-exit.png").outputStream().use {
                automation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            main { coordinator.cancel(); idleTransport.close() }
            audio.close()
        }
    }
}

