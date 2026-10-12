package io.github.issisx.alienflow

import android.os.Looper
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File

/** Real coordinator + cancellable HTTP on loopback. Transcript events are fixtures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class)
class StopDeliveryTest {
    private fun exercise(live: Boolean, check: (DictationCoordinator, MockWebServer, AtomicBoolean) -> Unit) {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as AlienApp
        app.store.prefs.edit().clear().commit()
        val server = MockWebServer(); val http = OkHttpClient(); val canceled = AtomicBoolean()
        server.start(); server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val api = OpenAiClient(callFactory = Call.Factory { request ->
            val actual = http.newCall(request.newBuilder().url(server.url(request.url.encodedPath)).build())
            object : Call by actual { override fun cancel() { canceled.set(true); actual.cancel() } }
        })
        val coordinator = DictationCoordinator(app, app.store, api) { "fixture-not-a-key" }
        ReflectionHelpers.setField(app, "coordinator", coordinator)
        ReflectionHelpers.setField(coordinator, "active", true)
        ReflectionHelpers.setField(coordinator, "cleanLive", live)
        try {
            repeat(3) { index -> coordinator.event(JSONObject()
                .put("type", "conversation.item.input_audio_transcription.completed").put("item_id", "item$index")
                .put("event_id", "event$index")
                .put("previous_item_id", if (index == 0) "" else "item${index - 1}").put("transcript", "phrase $index")) }
            check(coordinator, server, canceled)
        } finally {
            coordinator.cancel(); shadowOf(Looper.getMainLooper()).idle()
            http.dispatcher.cancelAll(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdownNow(); server.shutdown()
        }
    }
    private fun awaitComplete(coordinator: DictationCoordinator, maximumMs: Long) {
        val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maximumMs)
        while (coordinator.finishing && System.nanoTime() < until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        assertFalse("Stop stayed blocked: ${coordinator.status}", coordinator.finishing)
        assertEquals("phrase 0 phrase 1 phrase 2", coordinator.result)
    }
    @Test fun stopCancelsTheActualLiveCleanupAndDoesNotDrainQueuedPhraseRequests() = exercise(true) { coordinator, server, canceled ->
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        coordinator.stop(); awaitComplete(coordinator, 1_000)
        assertTrue(canceled.get()); assertEquals(1, server.requestCount)
    }
    @Test fun afterStopPolishUsesOneWholeTranscriptRequestAndCancelsAtItsBudget() = exercise(false) { coordinator, server, canceled ->
        assertEquals(0, server.requestCount)
        coordinator.stop()
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (server.requestCount == 0 && System.nanoTime() < until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("phrase 0 phrase 1 phrase 2", JSONObject(request.body.readUtf8()).getString("input"))
        awaitComplete(coordinator, 3_500)
        assertTrue(canceled.get()); assertEquals(1, server.requestCount)
    }
    @Test fun stopCannotFinishBeforeACommitAcknowledgementWhoseUiCounterIsStillQueued() = exercise(false) { coordinator, server, _ ->
        val app = org.robolectric.RuntimeEnvironment.getApplication() as AlienApp
        val audio = PendingAudio(File(app.store.audioDir, "held-commit.wav"))
        val transport = RealtimeTransport(app, audio, coordinator)
        val coverage = ReflectionHelpers.getField<AudioCoverage>(transport, "coverage")
        coverage.sent("last-frame", 960L) // Controlled network/Ui callback ordering, no microphone fixture.
        ReflectionHelpers.setField(coordinator, "transport", transport)
        ReflectionHelpers.setField(coordinator, "audio", audio)
        ReflectionHelpers.setField(coordinator, "correctionMode", "A")
        coordinator.stop()
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (ReflectionHelpers.getField<Boolean>(coordinator, "audioClosing") && System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5)
        }
        assertFalse(ReflectionHelpers.getField<Boolean>(coordinator, "audioClosing"))
        assertTrue(coordinator.finishing)
        assertFalse(ReflectionHelpers.getField<Boolean>(coordinator, "finalizing"))
        coverage.acknowledge("item2")
        coordinator.turnCommitted()
        coordinator.event(JSONObject().put("type", "input_audio_buffer.committed").put("event_id", "ack").put("item_id", "item2"))
        awaitComplete(coordinator, 1_000)
        assertEquals(0, server.requestCount)
    }
}
