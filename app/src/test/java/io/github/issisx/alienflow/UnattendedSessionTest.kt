package io.github.issisx.alienflow

import android.Manifest
import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration
import java.time.LocalDate

/** Real coordinator, service and bridge on the paused main looper. Field focus and the keyboard
 * session use EditorBridgeNodeRouteTest's simulated broker; capture-thread calls are a plain thread. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class, shadows = [EditorBridgeNodeRouteTest.Broker::class,
    EditorBridgeNodeRouteTest.NodeIpc::class, EditorBridgeNodeRouteTest.Ime::class, EditorBridgeNodeRouteTest.KeyboardIpc::class])
class UnattendedSessionTest {
    private val harness = EditorBridgeNodeRouteTest()
    @Before fun reset() = harness.reset()
    private fun ui() = shadowOf(Looper.getMainLooper())
    private class Session(val coordinator: DictationCoordinator, val store: LocalStore,
                          val provider: LocalProvider, val statuses: MutableList<String>) {
        /** Monotonic clock when the coordinator first left the active state. */
        var stoppedAt: Long? = null
    }

    private fun withSession(correction: String = "A", liveDisplay: Boolean = true, dailyMinutes: Int? = null,
                            usedBytes: Int = 0, check: (Session) -> Unit) = harness.withField("", keyboard = true) { service ->
        val app = service.application as AlienApp
        val settings = app.store.prefs.edit().clear().putString("correction", correction).putBoolean("live_display", liveDisplay)
        if (dailyMinutes != null) settings.putInt("daily_minutes", dailyMinutes)
        settings.commit()
        val store = LocalStore(app) { LocalDate.of(2026, 10, 18) }
        store.usage.addRealtimeAudio(usedBytes)
        val provider = LocalProvider()
        val coordinator = DictationCoordinator(app, store, provider.api, Dispatchers.Unconfined) { "fixture-not-a-key" }
        ReflectionHelpers.setField(app, "coordinator", coordinator); coordinator.service = service
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val statuses = mutableListOf<String>()
        val session = Session(coordinator, store, provider, statuses)
        // The active bubble animates, and Robolectric advances the clock for its frames inside idle(),
        // so stop times are recorded when they happen rather than inferred from idle boundaries.
        coordinator.listener = {
            statuses += coordinator.status
            if (!coordinator.active && session.stoppedAt == null) session.stoppedAt = SystemClock.elapsedRealtime()
        }
        try {
            coordinator.start(); assertTrue(coordinator.status, coordinator.active)
            check(session)
        } finally { coordinator.cancel(); ui().idle() }
    }
    /** The user leaves for a screen without an eligible field. Returns the monotonic release time. */
    private fun release(coordinator: DictationCoordinator): Long {
        EditorBridgeNodeRouteTest.focused = null; coordinator.targetEvent()
        assertEquals("Listening • select a text field for new speech", coordinator.status)
        return SystemClock.elapsedRealtime()
    }
    /** The user selects another field and its keyboard session starts. */
    private fun attach(coordinator: DictationCoordinator, fieldId: Int) {
        val current = EditorBridgeNodeRouteTest.field!!
        val next = EditText(current.context).apply { setText("draft "); setSelection(length()) }
        (current.context as Activity).setContentView(next); next.requestFocus()
        EditorBridgeNodeRouteTest.field = next; EditorBridgeNodeRouteTest.focused = next.createAccessibilityNodeInfo()
        EditorBridgeNodeRouteTest.info = EditorInfo().apply {
            packageName = EditorBridgeNodeRouteTest.focused!!.packageName.toString(); this.fieldId = fieldId
            inputType = next.inputType; initialSelStart = next.length(); initialSelEnd = next.length()
        }
        EditorBridgeNodeRouteTest.nativeKeyboard = next.onCreateInputConnection(EditorInfo())!!
        EditorBridgeNodeRouteTest.inputMethod!!.onStartInput(EditorBridgeNodeRouteTest.info!!, false)
        coordinator.targetEvent()
        assertEquals("Listening • tap the alien to stop", coordinator.status)
    }
    private fun speech(coordinator: DictationCoordinator, id: String, words: String) {
        coordinator.event(JSONObject().put("type", "conversation.item.input_audio_transcription.completed")
            .put("event_id", id).put("item_id", id).put("_editor_epoch", ReflectionHelpers.getField<Long>(coordinator, "editorEpoch"))
            .put("transcript", words))
        ui().idle()
    }
    /** Runs the paused main looper at least up to [time] on the monotonic clock. */
    private fun advanceTo(time: Long) = ui().idleFor(Duration.ofMillis((time - SystemClock.elapsedRealtime()).coerceAtLeast(0)))
    private val noFieldMs = DictationCoordinator.NO_FIELD_STOP_MS

    @Test fun aSessionLeftWithoutAFieldStopsAtExactlyTenSecondsAndKeepsItsWords() = withSession { s ->
        val releasedAt = release(s.coordinator)
        speech(s.coordinator, "room", "words heard with no field")
        advanceTo(releasedAt + noFieldMs - 100)
        assertTrue(s.coordinator.active); assertNull(s.stoppedAt)
        s.statuses.clear()
        advanceTo(releasedAt + noFieldMs + 1_000)
        assertEquals("Stopped exactly ten seconds after the field was released", releasedAt + noFieldMs, s.stoppedAt)
        assertFalse(s.coordinator.active)
        assertEquals("Stopped • no text field selected • finishing captured speech…", s.statuses.first())
        assertFalse("Captured speech finished through the normal Stop path", s.coordinator.finishing)
        assertEquals("Stopped • no text field selected • Complete • text saved across apps", s.coordinator.status)
        assertTrue(s.statuses.all { it.startsWith(DictationCoordinator.NO_FIELD_STOP_STATUS) })
        assertEquals("words heard with no field", s.store.history().first().getString("original"))
        assertTrue(s.provider.requests.isEmpty())
    }

    @Test fun attachingAFieldInTimeKeepsTheSessionAndAnotherReleaseRestartsTheTenSeconds() = withSession { s ->
        val firstRelease = release(s.coordinator)
        advanceTo(firstRelease + 5_000)
        attach(s.coordinator, 77)
        advanceTo(firstRelease + noFieldMs + 2_000)
        assertTrue("The first release's deadline passed with a field attached", s.coordinator.active)
        assertNull(s.stoppedAt)
        val secondRelease = release(s.coordinator)
        advanceTo(secondRelease + noFieldMs + 1_000)
        assertEquals("Ten seconds from the second release, not the first", secondRelease + noFieldMs, s.stoppedAt)
        assertFalse(s.coordinator.active)
        assertTrue(s.coordinator.status, s.coordinator.status.startsWith(DictationCoordinator.NO_FIELD_STOP_STATUS))
    }

    @Test fun anEarlierSessionsTimerNeverStopsALaterSession() = withSession { s ->
        val original = EditorBridgeNodeRouteTest.field!!
        val earlierRelease = release(s.coordinator)
        advanceTo(earlierRelease + 4_000)
        s.coordinator.cancel(); ui().idle()
        EditorBridgeNodeRouteTest.focused = original.createAccessibilityNodeInfo()
        s.coordinator.start(); assertTrue(s.coordinator.status, s.coordinator.active)
        s.stoppedAt = null // The cancel above ended the earlier session.
        val laterRelease = release(s.coordinator)
        advanceTo(earlierRelease + noFieldMs + 1)
        assertTrue("The earlier session's deadline passed", s.coordinator.active)
        assertNull(s.stoppedAt)
        advanceTo(laterRelease + noFieldMs + 1_000)
        assertEquals(laterRelease + noFieldMs, s.stoppedAt)
        assertFalse(s.coordinator.active)
        assertTrue(s.coordinator.status, s.coordinator.status.startsWith(DictationCoordinator.NO_FIELD_STOP_STATUS))
    }

    @Test fun livePolishIsNotRequestedWhileNoFieldIsAttached() = withSession(correction = "B", liveDisplay = false) { s ->
        speech(s.coordinator, "a", "first field words")
        assertEquals(listOf("/v1/responses"), s.provider.requests.toList())
        release(s.coordinator)
        speech(s.coordinator, "b", "television in the room")
        speech(s.coordinator, "c", "more television")
        assertEquals("No polish while no field is attached", 1, s.provider.requests.size)
        attach(s.coordinator, 77)
        speech(s.coordinator, "d", "new field words")
        assertEquals(2, s.provider.requests.size)
        assertEquals(2, s.store.usage.today().polishRequests)
    }

    @Test fun crossingTheDailyLimitDuringCaptureStopsThroughTheNormalStop() {
        val limit = UsageMeter.limitBytes(5).toInt()
        withSession(dailyMinutes = 5, usedBytes = limit - RealtimeProtocol.CHUNK_BYTES) { s ->
            val token = ReflectionHelpers.getField<Long>(s.coordinator, "session")
            fun captured(bytes: Int) = Thread {
                ReflectionHelpers.callInstanceMethod<Unit>(s.coordinator, "realtimeAudioSent",
                    ClassParameter.from(Long::class.javaPrimitiveType, token), ClassParameter.from(Int::class.javaPrimitiveType, bytes))
            }.apply { start(); join() }
            captured(RealtimeProtocol.CHUNK_BYTES / 2); ui().idle()
            assertTrue("Below the limit", s.coordinator.active)
            s.statuses.clear()
            captured(RealtimeProtocol.CHUNK_BYTES / 2)
            assertTrue("The capture thread only counts; the coordinator stops on its own thread", s.coordinator.active)
            captured(RealtimeProtocol.CHUNK_BYTES)
            ui().idle()
            assertFalse(s.coordinator.active); assertFalse(s.coordinator.finishing)
            assertEquals("Stopped • daily limit of 5 minutes reached • finishing captured speech…", s.statuses.first())
            assertTrue(s.statuses.all { it.startsWith("Stopped • daily limit of 5 minutes reached") })
            assertEquals(limit + RealtimeProtocol.CHUNK_BYTES.toLong(), s.store.usage.today().realtimeBytes)
            s.coordinator.start()
            assertFalse(s.coordinator.active)
            assertEquals(DictationCoordinator.dailyLimitStatus(5), s.coordinator.status)
        }
    }
}
