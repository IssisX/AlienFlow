package io.github.issisx.alienflow

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

class CaptureService : Service() {
    companion object { const val START = "capture.start"; const val STOP = "capture.stop"; var instance: CaptureService? = null; private set }
    override fun onCreate() { super.onCreate(); (application as AlienApp).crashJournal.mark("service.created"); instance = this; getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("recording", "Dictation microphone", NotificationManager.IMPORTANCE_LOW)) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val coordinator = (application as AlienApp).coordinator
        if (intent?.action == STOP) { coordinator.stop(); stopSelf(); return START_NOT_STICKY }
        if (!coordinator.active) { stopSelf(); return START_NOT_STICKY }
        try {
            (application as AlienApp).crashJournal.mark("service.promoting")
            startForeground(1, notification("Starting dictation…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            (application as AlienApp).crashJournal.mark("service.promoted")
            coordinator.microphoneServiceReady()
        } catch (e: Exception) { (application as AlienApp).crashJournal.recordFailure("service.failure", e); coordinator.failure("Android denied microphone foreground service; check microphone permission and start restrictions"); stopSelf() }
        return START_NOT_STICKY
    }
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "recording").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("AlienFlow microphone")
            .setContentText(text).setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
    }
    fun refresh(text: String) { getSystemService(NotificationManager::class.java).notify(1, notification(text)) }
    override fun onDestroy() { instance = null; (application as AlienApp).coordinator.captureServiceLost(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
