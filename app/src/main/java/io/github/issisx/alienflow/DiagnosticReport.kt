package io.github.issisx.alienflow

import android.Manifest
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.security.MessageDigest
import java.time.Instant

/** A read-only report for user-initiated sharing; no API key is decrypted here. */
object DiagnosticReport {
    fun collect(app: AlienApp): String = buildString {
        appendLine("ALIENFLOW DIAGNOSTIC REPORT")
        appendLine("Created: ${Instant.now()}")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Source: ${BuildConfig.SOURCE_REVISION}")
        appendLine("Package: ${app.packageName}; pid=${Process.myPid()}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT}")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Transport: ${RealtimeProtocol.TRANSPORT_NAME}")
        appendLine("Native WebRTC packaged: false")
        try {
            val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val cert = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            if (cert != null) appendLine("Certificate SHA-256: " + MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) })
        } catch (_: Exception) { appendLine("Certificate: unavailable") }
        fun granted(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        appendLine("Microphone permission: ${granted(Manifest.permission.RECORD_AUDIO)}")
        appendLine("Notification permission: ${granted(Manifest.permission.POST_NOTIFICATIONS)}")
        appendLine("Accessibility bound: ${app.coordinator.service != null}")
        appendLine("Capture service alive: ${CaptureService.instance != null}")
        appendLine("Coordinator active=${app.coordinator.active}; finishing=${app.coordinator.finishing}")
        appendLine("Text contents and credentials are not included. No automatic upload.")
        appendLine()
        appendLine(app.crashJournal.diagnostic())
        appendLine("ANDROID RECORDED PROCESS EXITS (may include update/permission changes)")
        try {
            val manager = app.getSystemService(ActivityManager::class.java)
            val exits = manager.getHistoricalProcessExitReasons(app.packageName, 0, 5)
            if (exits.isEmpty()) appendLine("No exit records supplied by Android. This is not proof that no crash occurred.")
            var nativeInspected = false
            for (exit in exits) {
                appendLine("${Instant.ofEpochMilli(exit.timestamp)} pid=${exit.pid} reason=${reason(exit.reason)} (${exit.reason}) status/signal=${exit.status}")
                // Only accept our own fixed-token process summary, never an arbitrary OS description.
                val state = exit.processStateSummary?.toString(Charsets.UTF_8)
                if (state != null && state.matches(Regex("AF[0-9]+:[0-9]+:[a-z0-9_.-]{1,64}"))) appendLine("Last process stage: $state")
                if (exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE && !nativeInspected) {
                    nativeInspected = true
                    try {
                        val native = exit.traceInputStream?.use { BoundedRead.bytes(it, NativeCrashSummary.MAX_BYTES + 1) }
                        appendLine(if (native == null) "Native trace not retained by Android." else NativeCrashSummary.render(native))
                    } catch (_: Exception) { appendLine("Native trace could not be read.") }
                }
            }
        } catch (e: Exception) { appendLine("Exit records unavailable: ${e.javaClass.simpleName}") }
        appendLine("A stopped dictation and a dead process are different outcomes; compare exit timestamps with the session trace.")
    }.take(30000)

    private fun reason(value: Int): String = when (value) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "JAVA_CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "OTHER_OR_UNKNOWN"
    }
}
