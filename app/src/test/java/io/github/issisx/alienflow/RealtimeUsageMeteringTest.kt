package io.github.issisx.alienflow

import android.os.Looper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
import java.util.Base64

/** Real transport/gate/commit path with a simulated microphone and socket. Kept apart from
 * RealtimeBufferedStartupTest, which shipped-source replays compile against older source. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class)
class RealtimeUsageMeteringTest {
    private class Input : MicrophoneInput {
        val frames = ArrayDeque<ShortArray>()
        var stopped = false
        override fun start() {}
        override fun read(samples: ShortArray): Int {
            if (stopped || frames.isEmpty()) return 0
            val frame = frames.removeFirst(); frame.copyInto(samples); return frame.size
        }
        override fun stop() { stopped = true }
        override fun release() {}
        fun speech(count: Int) { repeat(count) { frames.addLast(ShortArray(RealtimeProtocol.CHUNK_SAMPLES) { (1_000 + it).toShort() }) } }
        fun silence(count: Int) { repeat(count) { frames.addLast(ShortArray(RealtimeProtocol.CHUNK_SAMPLES)) } }
    }
    private class Socket(private val req: Request) : WebSocket {
        val sent = mutableListOf<JSONObject>()
        var failAppend = false
        override fun request() = req
        override fun queueSize() = 0L
        override fun send(text: String): Boolean {
            val row = JSONObject(text)
            if (failAppend && row.optString("type") == "input_audio_buffer.append") return false
            sent += row; return true
        }
        override fun send(bytes: ByteString) = false
        override fun close(code: Int, reason: String?) = true
        override fun cancel() {}
    }
    private class Boundary(scope: TestScope) : AutoCloseable {
        val app = RuntimeEnvironment.getApplication() as AlienApp
        val input = Input()
        val file = File.createTempFile("metering-fixture-", ".wav", app.cacheDir).also { check(it.delete()) }
        val audio = PendingAudio(file)
        val metered = mutableListOf<Int>()
        lateinit var socket: Socket
        lateinit var network: WebSocketListener
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val transport = RealtimeTransport(app, audio, object : RealtimeTransport.Listener {
            override fun event(value: JSONObject) {}
            override fun captured() {}
            override fun turnCommitted() {}
            override fun interrupted() {}
            override fun reconnected() {}
            override fun failure(message: String) {}
        }, { input }, dispatcher, dispatcher)
        init {
            transport.onAudioSent = { metered += it }
            transport.connect(OpenAiClient(WebSocket.Factory { request, listener ->
                network = listener; Socket(request).also { socket = it }
            }), "test-placeholder-not-a-key", "en", "")
        }
        fun open() { network.onOpen(socket, Response.Builder().request(socket.request()).protocol(Protocol.HTTP_1_1).code(101).message("Switching Protocols").build()) }
        fun ready() { network.onMessage(socket, JSONObject().put("type", "session.updated").put("session", RealtimeProtocol.sessionConfig("en", "")).toString()) }
        fun pumpUi() { shadowOf(Looper.getMainLooper()).idle() }
        fun uploadedAudio(): ByteArray = socket.sent.filter { it.optString("type") == "input_audio_buffer.append" }
            .flatMap { Base64.getDecoder().decode(it.getString("audio")).toList() }.toByteArray()
        override fun close() { transport.close(); audio.close(); file.delete() }
    }

    @Test fun meteredBytesAreTheBytesSentIncludingStartupReplayAndCommitPadding() = runTest {
        Boundary(this).use { b ->
            b.input.speech(1); runCurrent(); b.pumpUi()
            assertTrue("Buffered startup audio is not sent, so not metered yet", b.metered.isEmpty())
            b.open(); b.ready(); runCurrent(); b.pumpUi()
            assertEquals(listOf(RealtimeProtocol.CHUNK_BYTES), b.metered)
            b.transport.finishCapture {}; runCurrent(); advanceTimeBy(5); runCurrent(); b.pumpUi()
            // The 20 ms frame is padded with silence to the 100 ms commit minimum; that padding is billed too.
            assertEquals(listOf(RealtimeProtocol.CHUNK_BYTES, RealtimeProtocol.MIN_COMMIT_BYTES - RealtimeProtocol.CHUNK_BYTES), b.metered)
            assertEquals(b.uploadedAudio().size, b.metered.sum())
            assertEquals(RealtimeProtocol.MIN_COMMIT_BYTES, b.metered.sum())
        }
    }
    @Test fun silenceHeldByTheGateIsNeverMetered() = runTest {
        Boundary(this).use { b ->
            b.open(); b.ready()
            b.input.speech(10); b.input.silence(150); b.input.speech(10)
            runCurrent(); b.pumpUi()
            b.transport.finishCapture {}; advanceTimeBy(5); runCurrent(); b.pumpUi(); b.audio.close()
            val frame = RealtimeProtocol.CHUNK_BYTES
            assertEquals(170 * frame, b.file.readBytes().size - 44)
            // 10 speech + 100 frames of the 2 s idle allowance, then 25 pre-roll + 10 speech.
            assertEquals(145 * frame, b.metered.sum())
            assertEquals(b.uploadedAudio().size, b.metered.sum())
        }
    }
    @Test fun anAppendTheSocketRefusesIsNotMetered() = runTest {
        Boundary(this).use { b ->
            b.input.speech(5); runCurrent(); b.pumpUi()
            b.socket.failAppend = true; b.open(); b.ready(); runCurrent(); b.pumpUi()
            b.input.speech(5); advanceTimeBy(5); runCurrent(); b.pumpUi()
            assertTrue(b.uploadedAudio().isEmpty()); assertTrue(b.metered.isEmpty())
        }
    }
}
