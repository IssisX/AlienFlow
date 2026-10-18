package io.github.issisx.alienflow

import android.os.Looper
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Runs unchanged against shipped .28 Claude. HTTP is loopback; no API key/provider is used. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class)
class BillingShutdownRegressionTest {
    private fun exercise(check: (DictationCoordinator, PendingAudio, MockWebServer, AtomicBoolean) -> Unit) {
        val app = RuntimeEnvironment.getApplication() as AlienApp
        app.store.prefs.edit().clear().putBoolean("paid_api_enabled", true).commit()
        val server = MockWebServer(); server.start()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val http = OkHttpClient(); val canceled = AtomicBoolean()
        val api = OpenAiClient(callFactory = Call.Factory { request ->
            val call = http.newCall(request.newBuilder().url(server.url(request.url.encodedPath)).build())
            object : Call by call { override fun cancel() { canceled.set(true); call.cancel() } }
        })
        val coordinator = DictationCoordinator(app, app.store, api) { "fixture-not-a-key" }
        ReflectionHelpers.setField(app, "coordinator", coordinator)
        val audio = PendingAudio(File(app.store.audioDir, "billing-fixture.wav"))
        audio.append(ByteArray(4_800), RealtimeProtocol.SAMPLE_RATE, RealtimeProtocol.CHANNELS)
        try { check(coordinator, audio, server, canceled) }
        finally {
            coordinator.cancel(); audio.close()
            http.dispatcher.cancelAll(); http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdownNow(); server.shutdown()
            shadowOf(Looper.getMainLooper()).idle()
            audio.file.delete()
        }
    }
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        assertTrue("Expected lifecycle transition did not complete", condition())
    }
    @Test fun stoppedInterruptedSessionDoesNotAutomaticallyUploadTheRecordingAgain() = exercise { coordinator, audio, server, _ ->
        ReflectionHelpers.setField(coordinator, "active", true)
        ReflectionHelpers.setField(coordinator, "interrupted", true)
        ReflectionHelpers.setField(coordinator, "audio", audio)
        coordinator.stop()
        await { !coordinator.finishing }
        assertNull("Stopping must not start a second paid transcription of the saved WAV", server.takeRequest(500, TimeUnit.MILLISECONDS))
        assertTrue(audio.file.exists())
    }
    @Test fun cancelClosesRecoveryHttpEvenWhenDictationIsAlreadyIdle() = exercise { coordinator, audio, server, canceled ->
        audio.close(); coordinator.recover(audio.file)
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        assertFalse(coordinator.active); assertFalse(coordinator.finishing)
        coordinator.cancel()
        await { canceled.get() }
        assertTrue("Cancel must retain the source recording", audio.file.exists())
        assertEquals(1, server.requestCount)
    }
}
