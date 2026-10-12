package io.github.issisx.alienflow

import android.os.Looper
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/** Production transport with an explicitly failing test network boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class)
class RealtimeStartupFailureTest {
    private class FailedSocket(private val req: Request) : WebSocket {
        var cancelled = false
        override fun request() = req
        override fun queueSize() = 0L
        override fun send(text: String) = false
        override fun send(bytes: ByteString) = false
        override fun close(code: Int, reason: String?) = true
        override fun cancel() { cancelled = true }
    }
    @Test fun failureBeforeFactoryReturnsCannotBeIgnoredOrReviveTheSocket() {
        val app = (RuntimeEnvironment.getApplication() as AlienApp)
        val file = File(app.cacheDir, "never-captured-test.wav")
        val audio = PendingAudio(file)
        val failures = mutableListOf<String>()
        var captured = false
        lateinit var failed: FailedSocket
        val api = OpenAiClient(WebSocket.Factory { request, listener ->
            failed = FailedSocket(request)
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(403).message("Forbidden").build()
            listener.onFailure(failed, IOException("fixture failure"), response)
            failed
        })
        val transport = RealtimeTransport(app, audio, object : RealtimeTransport.Listener {
            override fun event(value: JSONObject) { error("No server event expected") }
            override fun captured() { captured = true }
            override fun turnCommitted() { error("No audio commit expected") }
            override fun interrupted() { error("Startup failure is terminal") }
            override fun reconnected() { error("No reconnect expected") }
            override fun failure(message: String) { failures.add(message) }
        })
        try {
            transport.connect(api, "test-placeholder-not-a-key", "en", "")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, failures.size)
            assertTrue(failures.single().contains("HTTP 403"))
            assertFalse(captured)
            assertTrue(failed.cancelled)
        } finally { transport.close(); audio.close() }
        assertFalse("No capture means no recovery recording", file.exists())
    }
}
