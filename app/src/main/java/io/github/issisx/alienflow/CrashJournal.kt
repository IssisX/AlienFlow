package io.github.issisx.alienflow

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Process
import java.io.File
import android.util.AtomicFile
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Stores only startup checkpoints and source locations. Never writes exception
 * messages, API responses, credentials, field contents, audio, or full system logs.
 */
object CrashReportFormatter {
    fun render(stage: String, throwable: Throwable): String {
        val relevant = throwable.stackTrace.filter { it.className.startsWith("io.github.issisx.alienflow.") }.take(12)
        val frames = if (relevant.isNotEmpty()) relevant else throwable.stackTrace.take(6)
        fun frame(row: StackTraceElement): String {
            val file = row.fileName?.substringAfterLast('/')?.substringAfterLast('\\')?.take(72) ?: "unknown"
            val cls = row.className.take(130)
            return "$cls.${row.methodName.take(80)}($file:${row.lineNumber})"
        }
        return buildString {
            appendLine("AlienFlow private crash diagnostic")
            appendLine("Stage: ${stage.take(64)}")
            appendLine("Exception type: ${throwable.javaClass.name.take(150)}")
            appendLine("Location (exception messages omitted):")
            frames.forEach { appendLine(frame(it)) }
            throwable.cause?.let { appendLine("Cause type: ${it.javaClass.name.take(150)}") }
        }
    }
}

class CrashJournal(private val application: Application) {
    private val prefs = application.getSharedPreferences("diagnostic-checkpoints", Context.MODE_PRIVATE)
    private val crashFile = File(application.filesDir, "last-crash-diagnostic.txt")
    private val allowedStages = setOf(
        "idle", "tap.received", "editor.begin", "editor.ready",
        "service.start-requested", "service.start-returned", "service.created",
        "service.promoting", "service.promoted", "microphone.setup",
        "transport.start", "session.secret", "webrtc.initialize",
        "webrtc.factory", "webrtc.peer", "webrtc.data-channel",
        "webrtc.offer", "webrtc.answer", "microphone.request",
        "microphone.frames", "dictation.stopping", "dictation.finished",
        "accessibility.disconnected", "microphone.native-started", "microphone.native-error",
        "transport.failure", "microphone.failure", "service.failure", "probe.start", "probe.end", "probe.cancelled", "probe.failure",
        "stop.editor-change", "stop.screen", "stop.service", "status.failure", "status.other",
        "status.listening", "status.finishing", "editor.rejected",
        "websocket.connecting", "websocket.open", "websocket.session-update",
        "websocket.session-ready", "websocket.failure",
        "audiorecord.creating", "audiorecord.started", "audiorecord.released",
        "audiorecord.failure", "editor.detached", "failure.terminal",
        "dictation.transcript-ready", "cleanup.after-stop", "dictation.saving"
    )
    @Volatile private var persistFailed = false
    private val checkpointWriter = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AlienFlow-checkpoints").apply { isDaemon = true }
    }
    @Volatile private var currentStage = prefs.getString("stage", "idle") ?: "idle"
    private var currentSession = prefs.getLong("session", 0)
    @Volatile private var currentTrace = prefs.getString("trace", "").orEmpty()
    @Synchronized fun mark(stage: String) {
        if (stage !in allowedStages) return
        if (stage != "tap.received" && stage == lastStage()) return
        val session = if (stage == "tap.received") System.currentTimeMillis() else currentSession
        if (stage == "tap.received") prefs.edit().remove("live_problem").remove("editor_code")
            .remove("editor_observation").remove("audio_commit").remove("startup_timing").apply()
        val entry = "${Instant.now()} $stage"
        val trace = (currentTrace.lines().filter { it.isNotBlank() } + entry).takeLast(80).joinToString("\n")
        currentStage = stage; currentSession = session; currentTrace = trace
        // UI/capture observes the in-memory checkpoint immediately. One I/O owner serializes
        // durable writes; a fatal Kotlin failure drains them before writing its crash record.
        checkpointWriter.execute {
            try {
                if (!prefs.edit().putString("stage", stage).putLong("session", session)
                        .putString("trace", trace).commit()) persistFailed = true
                try {
                    application.getSystemService(ActivityManager::class.java)
                        .setProcessStateSummary("AF${BuildConfig.VERSION_CODE}:$session:$stage".toByteArray())
                } catch (_: Exception) { /* Durable local trace remains. */ }
            } catch (_: Exception) { persistFailed = true }
        }
    }
    fun startupTiming(metadata: String) {
        // Generated from numeric monotonic observations; never accepts a server response.
        prefs.edit().putString("startup_timing", metadata).apply()
    }
    fun bubbleAvailability(eligible: Boolean, visible: Boolean, hidden: Boolean,
                          inputStarted: Boolean, terminal: Boolean) {
        val metadata = "eligible_input=$eligible; visible=$visible; explicitly_hidden=$hidden; " +
            "input_started=$inputStarted; bound_terminal=$terminal"
        if (prefs.getString("bubble_availability", "") != metadata) {
            prefs.edit().putString("bubble_availability", metadata).apply()
        }
    }
    @Synchronized fun editorFailure(message: String) {
        val code = Regex("^\\[AF-E([0-9]{2})\\]").find(message)?.groupValues?.get(1) ?: return
        if (prefs.getString("editor_code", "") == code) return
        try { prefs.edit().putString("editor_code", code).commit() } catch (_: Exception) { persistFailed = true }
        mark("editor.rejected")
    }
    internal fun editorFocus(result: EditorFocusDiscovery.Result) {
        val metadata = "direct=${result.direct}; input_window=${result.focusedWindow}; " +
            "visited=${result.visited}; candidates=${result.candidates}; exhausted=${result.exhausted}; resolved=${result.node != null}"
        if (prefs.getString("editor_focus", "") != metadata) prefs.edit().putString("editor_focus", metadata).apply()
    }
    fun editorBinding(snapshot: EditorSnapshot, hintLength: Int, hintEqualsText: Boolean,
                      showingHint: Boolean, whollyOwnedNode: Boolean, keyboardAttached: Boolean) {
        prefs.edit().putString("editor_binding", "length=${snapshot.text.length}; cursor=${snapshot.start}:${snapshot.end}; " +
            "hint_length=$hintLength; hint_equals_text=$hintEqualsText; showing_hint=$showingHint; " +
            "empty_owned_node=$whollyOwnedNode; keyboard_attached=$keyboardAttached").apply()
    }
    @Synchronized fun editorObservation(phase: String, observation: EditorSynchronization.Reading) {
        val n = observation.node; val k = observation.keyboard
        val metadata = "phase=${if (phase in setOf("before", "after", "node")) phase else "after"}; " +
            "node_length=${n?.text?.length}; node_cursor=${n?.start}:${n?.end}; " +
            "ime_length=${k?.text?.length}; ime_cursor=${k?.start}:${k?.end}; " +
            "offset=${k?.offset}; composing=${observation.composing}"
        try { prefs.edit().putString("editor_observation", metadata).commit() }
        catch (_: Exception) { persistFailed = true }
    }
    @Synchronized fun rejectedAudioCommit(bytes: Long, capturing: Boolean, generation: Int) {
        try { prefs.edit().putString("audio_commit", "sent_bytes=$bytes; capturing=$capturing; generation=$generation").commit() }
        catch (_: Exception) { persistFailed = true }
    }
    @Synchronized fun liveConnectionFailure(problem: LiveConnectionProblem) {
        try {
            if (!prefs.edit().putString("live_problem", "${Instant.now()} ${problem.diagnostic()}").commit()) persistFailed = true
        } catch (_: Exception) { persistFailed = true }
        mark("websocket.failure")
    }
    @Synchronized fun beginConnectionCheck() {
        try { prefs.edit().remove("live_problem").commit() } catch (_: Exception) { persistFailed = true }
    }
    fun statusChanged(message: String) {
        if (message.startsWith("[AF-E")) { editorFailure(message); return }
        mark(when {
            message.startsWith("Target changed") || message.startsWith("Focus or manual") -> "stop.editor-change"
            message.startsWith("Screen locked") -> "stop.screen"
            message.startsWith("Accessibility disconnected") || message.startsWith("Microphone service stopped") -> "stop.service"
            message.startsWith("Listening") -> "status.listening"
            message.startsWith("Finishing") -> "status.finishing"
            message.contains("failed", true) || message.contains("denied", true) || message.contains("timed out", true) -> "status.failure"
            else -> "status.other"
        })
    }
    fun probeResult(samples: Int) {
        try { prefs.edit().putInt("probe_samples", samples.coerceAtLeast(0)).commit() } catch (_: Exception) { persistFailed = true }
        mark("probe.end")
    }
    fun lastStage(): String = currentStage
    fun report(): String? = try {
        crashFile.takeIf { it.exists() }?.inputStream()?.use { it.readNBytes(10000).toString(Charsets.UTF_8) }
    } catch (_: Exception) { null }
    fun diagnostic(): String = buildString {
        appendLine("SESSION EVENTS (metadata only)")
        appendLine(currentTrace.ifBlank { "No session events recorded yet." })
        appendLine("STARTUP TIMINGS (first coordinator observation; milliseconds)")
        appendLine(prefs.getString("startup_timing", "No dictation timing recorded yet."))
        appendLine("Latest editor rejection code: " + prefs.getString("editor_code", "none"))
        appendLine("Latest editor observation: " + prefs.getString("editor_observation", "none"))
        appendLine("Latest editor focus resolution (metadata only): " + prefs.getString("editor_focus", "none"))
        appendLine("Latest editor binding (metadata only): " + prefs.getString("editor_binding", "none"))
        appendLine("Latest rejected audio commit: " + prefs.getString("audio_commit", "none"))
        appendLine("Latest bubble availability (metadata only): " + prefs.getString("bubble_availability", "not observed"))
        appendLine("Diagnostic persistence failure observed: $persistFailed")
        appendLine("Android-only microphone test samples: " + if (prefs.contains("probe_samples")) prefs.getInt("probe_samples", 0) else "not run")
        appendLine("LATEST LIVE CONNECTION FAILURE (metadata only)")
        appendLine(prefs.getString("live_problem", "No live connection error recorded."))
        appendLine("JAVA/KOTLIN FAILURE RECORD")
        appendLine(report() ?: "No Java/Kotlin failure captured; native crashes and normal stops may not create one.")
    }
    fun recordFailure(stage: String, problem: Throwable) {
        if (stage !in allowedStages) return
        mark(stage)
        try { writeFailure(problem) } catch (_: Exception) { persistFailed = true }
    }
    private fun writeFailure(problem: Throwable) {
        try { checkpointWriter.submit {}.get(2, TimeUnit.SECONDS) }
        catch (_: Exception) { persistFailed = true }
        val file = AtomicFile(crashFile)
        val out = file.startWrite()
        try {
            val report = "${Instant.now()} Version ${BuildConfig.VERSION_NAME} source=${BuildConfig.SOURCE_REVISION}\n" +
                CrashReportFormatter.render(lastStage(), problem)
            out.write(report.toByteArray(Charsets.UTF_8)); file.finishWrite(out)
        } catch (e: Exception) { file.failWrite(out); throw e }
    }
    @Synchronized fun clear() {
        try { checkpointWriter.submit {}.get(2, TimeUnit.SECONDS) } catch (_: Exception) { persistFailed = true }
        currentStage = "idle"; currentSession = 0L; currentTrace = ""
        crashFile.delete()
        prefs.edit().remove("trace").remove("editor_code").remove("editor_observation").remove("editor_focus").remove("editor_binding").remove("audio_commit").remove("session").remove("stage").remove("startup_timing").commit()
    }
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, problem ->
            try {
                writeFailure(problem)
            } catch (_: Throwable) {
                // Do not suppress the platform's normal crash behavior.
            } finally {
                if (previous != null) previous.uncaughtException(thread, problem)
                else Process.killProcess(Process.myPid())
            }
        }
    }
}
