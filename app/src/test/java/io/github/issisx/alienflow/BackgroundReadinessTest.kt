package io.github.issisx.alienflow

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File

/** Production service/notification under a simulated Android broker, not OEM persistence proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = AlienApp::class)
class BackgroundReadinessTest {
    @Test fun notificationShowsApiWorkAndItsCancelActionEvenWhenDictationIsIdle() {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as AlienApp
        val controller = Robolectric.buildService(CaptureService::class.java).create()
        val service = controller.get()
        val recovery = kotlinx.coroutines.Job()
        app.coordinator.service = Robolectric.buildService(AlienAccessibilityService::class.java).create().get()
        try {
            service.onStartCommand(Intent(app, CaptureService::class.java).setAction(CaptureService.READY), 0, 1)
            assertEquals("Paid API access paused", shadowOf(service).lastForegroundNotification!!.extras.getString(Notification.EXTRA_TEXT))
            ReflectionHelpers.setField(app.coordinator, "recoveryJob", recovery)
            service.refresh("Paid recovery upload in progress")
            val notice = app.getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == 1 }.notification
            assertEquals("Paid recovery upload in progress", notice.extras.getString(Notification.EXTRA_TEXT))
            assertEquals("Cancel", notice.actions.single().title.toString())
            service.onStartCommand(Intent(app, CaptureService::class.java).setAction(CaptureService.CANCEL), 0, 2)
            assertTrue(recovery.isCancelled)
        } finally {
            ReflectionHelpers.setField(app.coordinator, "recoveryJob", null)
            app.coordinator.service = null; controller.destroy()
        }
    }
    @Test fun readyAndFinishingKeepOneForegroundNotificationWithoutStartingTheMicrophone() {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as AlienApp
        val controller = Robolectric.buildService(CaptureService::class.java).create()
        val service = controller.get()
        app.coordinator.service = Robolectric.buildService(AlienAccessibilityService::class.java).create().get()
        try {
            service.onStartCommand(Intent(app, CaptureService::class.java).setAction(CaptureService.READY), 0, 1)
            val expectedType = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            assertEquals(expectedType, service.foregroundServiceType)
            assertEquals(1, shadowOf(service).lastForegroundNotificationId)
            assertFalse(app.coordinator.active)
            assertNull(ReflectionHelpers.getField<Any?>(app.coordinator, "transport"))
            ReflectionHelpers.setField(app.coordinator, "finishing", true)
            service.refresh("Finishing captured speech…")
            assertFalse(shadowOf(service).isStoppedBySelf)
            val notifications = app.getSystemService(NotificationManager::class.java).activeNotifications
            assertEquals(1, notifications.count { it.id == 1 })
            assertEquals("Finishing captured speech…", notifications.single { it.id == 1 }.notification.extras.getString(Notification.EXTRA_TEXT))
            assertEquals(expectedType, service.foregroundServiceType)
            ReflectionHelpers.setField(app.coordinator, "finishing", false)
            service.refresh("Complete")
            assertFalse(shadowOf(service).isStoppedBySelf)
            assertNull(ReflectionHelpers.getField<Any?>(app.coordinator, "transport"))
        } finally { app.coordinator.service = null; ReflectionHelpers.setField(app.coordinator, "finishing", false); controller.destroy() }
    }
    @Test fun hideAndAccessibilityLossStopBackgroundReadiness() {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as AlienApp
        val controller = Robolectric.buildService(CaptureService::class.java).create()
        val service = controller.get()
        app.coordinator.service = Robolectric.buildService(AlienAccessibilityService::class.java).create().get()
        try {
            service.onStartCommand(Intent(app, CaptureService::class.java).setAction(CaptureService.READY), 0, 1)
            service.onStartCommand(Intent(app, CaptureService::class.java).setAction(CaptureService.HIDE), 0, 2)
            assertFalse(app.coordinator.service!!.backgroundEnabled)
            assertTrue(shadowOf(service).isStoppedBySelf)
            app.coordinator.serviceLost()
            assertNull(app.coordinator.service)
            assertFalse(app.coordinator.active)
        } finally { app.coordinator.service = null; controller.destroy() }
    }
    @Test fun microphoneStopPrecedesTheTransitionToBackgroundReadiness() {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as AlienApp
        val controller = Robolectric.buildService(CaptureService::class.java).create()
        val service = controller.get()
        app.coordinator.service = Robolectric.buildService(AlienAccessibilityService::class.java).create().get()
        try {
            service.onStartCommand(Intent(app, CaptureService::class.java).setAction(CaptureService.READY), 0, 1)
            // Simulate already-established hardware/FGS state; production Stop owns the ordering.
            service.startForeground(1, shadowOf(service).lastForegroundNotification!!, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            ReflectionHelpers.setField(service, "currentType", ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            val audio = PendingAudio(File(app.store.audioDir, "microphone-order.wav"))
            val transport = RealtimeTransport(app, audio, app.coordinator)
            var typeAtStop = -1
            ReflectionHelpers.setField(transport, "microphone", object : MicrophoneInput {
                override fun start() = Unit
                override fun read(samples: ShortArray) = 0
                override fun stop() { typeAtStop = service.foregroundServiceType }
                override fun release() = Unit
            })
            ReflectionHelpers.setField(app.coordinator, "transport", transport)
            ReflectionHelpers.setField(app.coordinator, "audio", audio)
            ReflectionHelpers.setField(app.coordinator, "active", true)
            app.coordinator.stop()
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, typeAtStop)
            assertEquals(if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0, service.foregroundServiceType)
        } finally { app.coordinator.cancel(); app.coordinator.service = null; controller.destroy() }
    }
}
