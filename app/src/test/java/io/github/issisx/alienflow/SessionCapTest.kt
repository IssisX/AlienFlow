package io.github.issisx.alienflow

import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration

/** Real coordinator timer on the paused main looper; no microphone, network or key. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class)
class SessionCapTest {
    private fun exercise(check: (DictationCoordinator, Long, MutableList<String>) -> Unit) {
        val app = RuntimeEnvironment.getApplication() as AlienApp
        app.store.prefs.edit().clear().commit()
        val coordinator = DictationCoordinator(app, app.store, keyProvider = { "fixture-not-a-key" })
        ReflectionHelpers.setField(app, "coordinator", coordinator)
        ReflectionHelpers.setField(coordinator, "active", true)
        ReflectionHelpers.setField(coordinator, "correctionMode", "A")
        ReflectionHelpers.setField(coordinator, "session", 4L)
        val statuses = mutableListOf<String>()
        coordinator.listener = { statuses += coordinator.status }
        ReflectionHelpers.callInstanceMethod<Unit>(coordinator, "scheduleSessionCap",
            ClassParameter.from(Long::class.javaPrimitiveType, 4L))
        try { check(coordinator, 4L, statuses) }
        finally { coordinator.cancel(); shadowOf(Looper.getMainLooper()).idle() }
    }

    @Test fun aForgottenSessionStopsAtTheCapWithAVisibleReason() = exercise { coordinator, _, statuses ->
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DictationCoordinator.SESSION_CAP_MS - 1))
        assertTrue(coordinator.active); assertTrue(statuses.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertFalse(coordinator.active) // finishing then completes on IO; both statuses keep the reason
        assertEquals("Stopped after 5 minutes to limit cost • finishing captured speech…", statuses.first())
        assertTrue(statuses.all { it.startsWith(DictationCoordinator.SESSION_CAP_STATUS) })
    }

    @Test fun theCapNeverStopsALaterSession() = exercise { coordinator, token, statuses ->
        ReflectionHelpers.setField(coordinator, "session", token + 1)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DictationCoordinator.SESSION_CAP_MS + 1))
        assertTrue(coordinator.active); assertFalse(coordinator.finishing); assertTrue(statuses.isEmpty())
    }
}
