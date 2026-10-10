package io.github.issisx.alienflow

import android.app.Application
import android.content.Context
import android.os.Process
import java.io.File

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
        "microphone.frames", "dictation.stopping", "dictation.finished"
    )
    fun mark(stage: String) {
        if (stage in allowedStages) prefs.edit().putString("stage", stage).commit()
    }
    fun lastStage(): String = prefs.getString("stage", "idle") ?: "idle"
    fun report(): String? = try {
        crashFile.takeIf { it.exists() }?.readText(Charsets.UTF_8)?.take(10000)
    } catch (_: Exception) { null }
    fun diagnostic(): String = report() ?: (
        "No Java/Kotlin crash captured. Last startup checkpoint: ${lastStage()}. " +
            "A native crash or OS process termination may not reach the Java crash handler."
    )
    fun clear() { crashFile.delete(); mark("idle") }
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, problem ->
            try {
                val sanitized = CrashReportFormatter.render(lastStage(), problem)
                crashFile.writeText(sanitized, Charsets.UTF_8)
            } catch (_: Throwable) {
                // Do not suppress the platform's normal crash behavior.
            } finally {
                if (previous != null) previous.uncaughtException(thread, problem)
                else Process.killProcess(Process.myPid())
            }
        }
    }
}
