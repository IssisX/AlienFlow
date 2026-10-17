package io.github.issisx.alienflow

import android.os.Looper
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class)
class ApiSpendingSafetyTest {
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        assertTrue("API cancellation did not complete", condition())
    }
    private fun upload(stop: (DictationCoordinator) -> Unit) {
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
        val audio = PendingAudio(File(app.store.audioDir, "cancel-upload.wav"))
        audio.append(ByteArray(4_800), RealtimeProtocol.SAMPLE_RATE, 1); audio.close()
        try {
            coordinator.recover(audio.file)
            assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            assertTrue(coordinator.auxiliaryWork)
            stop(coordinator)
            await { canceled.get() && !coordinator.auxiliaryWork }
            assertTrue(audio.file.exists()); assertEquals(1, server.requestCount)
            assertTrue(app.store.history().isEmpty())
        } finally {
            coordinator.cancel(); http.dispatcher.cancelAll(); http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdownNow(); server.shutdown(); audio.file.delete()
        }
    }
    @Test fun stopCancelsAnUploadWhileDictationIsIdle() = upload { it.stop() }
    @Test fun lockingScreenCancelsAnUploadWhileDictationIsIdle() = upload { it.screenUnavailable() }
    @Test fun serviceLossCancelsAnUploadWhileDictationIsIdle() = upload { it.serviceLost() }
    @Test fun pauseCutsOffTheUploadAndPersistsAcrossCoordinatorRecreation() = upload {
        it.pausePaidApi()
        val app = RuntimeEnvironment.getApplication() as AlienApp
        assertFalse(it.paidApiEnabled)
        assertFalse(DictationCoordinator(app, app.store).paidApiEnabled)
    }
    @Test fun leavingTheActivityCancelsRecoveryWithoutRestartingIt() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            upload { controller.pause() }
        }
    }
    @Test fun freshOrUpgradedInstallAndOpeningSettingsCannotMakePaidRequests() {
        val app = RuntimeEnvironment.getApplication() as AlienApp
        app.store.prefs.edit().clear().putString("api_key", "fixture-existing-encrypted-key").commit()
        val calls = AtomicInteger()
        val api = OpenAiClient(
            socketFactory = WebSocket.Factory { _, _ -> calls.incrementAndGet(); error("Unexpected socket") },
            callFactory = Call.Factory { calls.incrementAndGet(); error("Unexpected HTTP request") })
        val coordinator = DictationCoordinator(app, app.store, api) { "fixture-not-a-key" }
        ReflectionHelpers.setField(app, "coordinator", coordinator)
        Robolectric.buildActivity(MainActivity::class.java).setup().use {
            assertFalse(coordinator.paidApiEnabled)
            coordinator.start(); coordinator.testKey(); coordinator.recover(File(app.cacheDir, "unused.wav"))
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(10))
            assertEquals(0, calls.get()); assertFalse(coordinator.active); assertFalse(coordinator.auxiliaryWork)
        }
    }
    @Test fun quietCaptureCannotDisableTheSafetyCutoffAndUsesElapsedTime() {
        val app = RuntimeEnvironment.getApplication() as AlienApp
        val audio = PendingAudio(File(app.cacheDir, "silence-policy.wav"))
        val transport = RealtimeTransport(app, audio, app.coordinator)
        try {
            assertEquals(30, transport.silenceStopSeconds)
            transport.silenceStopSeconds = 0; assertEquals(30, transport.silenceStopSeconds)
            transport.silenceStopSeconds = Int.MAX_VALUE; assertEquals(30, transport.silenceStopSeconds)
            val vad = ClientSideVad(); val silence = ByteArray(RealtimeProtocol.CHUNK_BYTES)
            assertFalse(vad.inspect(silence, 1_000, 30).silenceStop)
            assertFalse(vad.inspect(silence, 30_999, 30).silenceStop)
            assertTrue(vad.inspect(silence, 31_000, 30).silenceStop)
        } finally { transport.close(); audio.close() }
    }
    @Test fun openingTheAppWhileApiAccessIsEnabledStillMakesZeroRequests() {
        val app = RuntimeEnvironment.getApplication() as AlienApp
        app.store.prefs.edit().clear().putBoolean("paid_api_enabled", true).commit()
        val calls = AtomicInteger()
        val api = OpenAiClient(
            socketFactory = WebSocket.Factory { _, _ -> calls.incrementAndGet(); error("Unexpected socket") },
            callFactory = Call.Factory { calls.incrementAndGet(); error("Unexpected request") })
        val coordinator = DictationCoordinator(app, app.store, api) { "fixture-not-a-key" }
        ReflectionHelpers.setField(app, "coordinator", coordinator)
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            controller.pause().resume()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(10))
            assertEquals(0, calls.get()); assertFalse(coordinator.active); assertFalse(coordinator.auxiliaryWork)
        }
    }
    @Test fun stopCancelsTheActualConnectionCheckSocketWhileDictationIsIdle() {
        val app = RuntimeEnvironment.getApplication() as AlienApp
        app.store.prefs.edit().clear().putBoolean("paid_api_enabled", true).commit()
        val server = MockWebServer(); server.start()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
        val http = OkHttpClient(); val canceled = AtomicBoolean()
        val api = OpenAiClient(socketFactory = WebSocket.Factory { request, listener ->
            val socket = http.newWebSocket(request.newBuilder().url(server.url(request.url.encodedPath)).build(), listener)
            object : WebSocket by socket { override fun cancel() { canceled.set(true); socket.cancel() } }
        })
        val coordinator = DictationCoordinator(app, app.store, api) { "fixture-not-a-key" }
        try {
            coordinator.testKey(); assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            coordinator.stop(); await { canceled.get() && !coordinator.auxiliaryWork }
            assertEquals(1, server.requestCount)
        } finally {
            coordinator.cancel(); http.dispatcher.cancelAll(); http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdownNow(); server.shutdown()
        }
    }
}
