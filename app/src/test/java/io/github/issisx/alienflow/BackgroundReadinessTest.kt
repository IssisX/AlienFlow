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

/** Production service/notification under a simulated Android broker, not OEM persistence proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = AlienApp::class)
class BackgroundReadinessTest {
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
}
