package io.github.issisx.alienflow

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** One foreground lifetime for readiness, microphone capture and final delivery. */
class CaptureService : Service() {
    companion object {
        const val READY = "capture.ready"
        const val START = "capture.start"
        const val STOP = "capture.stop"
        const val CANCEL = "capture.cancel"
        const val HIDE = "capture.hide"
        var instance: CaptureService? = null; private set
    }
    internal var currentType = 0; private set
    private var foreground = false
    private var lastText = ""
    private val app get() = application as AlienApp
    override fun onCreate() {
        super.onCreate(); app.crashJournal.mark("service.created"); instance = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("recording", "AlienFlow dictation", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val coordinator = app.coordinator
        when (intent?.action) {
            STOP -> { coordinator.stop(); return START_NOT_STICKY }
            CANCEL -> { coordinator.cancel(); return START_NOT_STICKY }
            HIDE -> { coordinator.cancel(); coordinator.service?.hideAlien(); stopSelf(); return START_NOT_STICKY }
        }
        if (coordinator.service == null) { stopSelf(); return START_NOT_STICKY }
        try {
            app.crashJournal.mark("service.promoting")
            if (intent?.action == START && coordinator.active) {
                promote("Starting dictation…", ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                app.crashJournal.mark("service.promoted")
                coordinator.microphoneServiceReady()
            } else if (!coordinator.active) {
                promote(if (coordinator.finishing || coordinator.auxiliaryWork) coordinator.status else idleText(), readyType())
            }
        } catch (e: Exception) {
            foreground = false
            app.crashJournal.recordFailure("service.failure", e)
            coordinator.failure("Android denied the background dictation service; open AlienFlow and check permissions")
            stopSelf()
        }
        // A restart cannot silently recreate a microphone session or stale editor.
        return START_NOT_STICKY
    }
    private fun readyType() = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
    private fun idleText() = if (app.coordinator.paidApiEnabled) "Ready • tap the alien to dictate" else "Paid API access paused"
    private fun promote(text: String, type: Int) {
        startForeground(1, notification(text), type)
        currentType = type; foreground = true; lastText = text
    }
    private fun notification(text: String): Notification {
        val coordinator = app.coordinator
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val action = if (coordinator.active) STOP else if (coordinator.finishing || coordinator.auxiliaryWork) CANCEL else HIDE
        val label = if (coordinator.active) "Stop" else if (coordinator.finishing || coordinator.auxiliaryWork) "Cancel" else "Hide alien"
        val control = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "recording").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (coordinator.active) "AlienFlow microphone" else "AlienFlow")
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, label, control).build()).build()
    }
    fun refresh(text: String) {
        if (!foreground) return
        val coordinator = app.coordinator
        if (!coordinator.active && !coordinator.finishing && coordinator.service?.backgroundEnabled != true) { stopSelf(); return }
        val visible = if (coordinator.active || coordinator.finishing || coordinator.auxiliaryWork) text else idleText()
        try {
            if (!coordinator.active && currentType != readyType()) promote(visible, readyType())
            else if (visible != lastText) {
                getSystemService(NotificationManager::class.java).notify(1, notification(visible)); lastText = visible
            }
        } catch (e: Exception) {
            foreground = false
            app.crashJournal.recordFailure("service.failure", e)
            coordinator.failure("Background dictation service interrupted; words preserved in AlienFlow")
            stopSelf()
        }
    }
    override fun onDestroy() {
        foreground = false; stopForeground(STOP_FOREGROUND_REMOVE)
        if (instance === this) { instance = null; app.coordinator.captureServiceLost() }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
