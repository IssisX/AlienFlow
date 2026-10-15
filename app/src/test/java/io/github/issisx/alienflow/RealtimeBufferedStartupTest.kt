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
import java.io.IOException
import java.time.Duration
import java.util.Base64

/** Real transport/PCM/WAV pipeline with explicitly simulated hardware and network boundaries. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class)
class RealtimeBufferedStartupTest {
    private class Input : MicrophoneInput {
        val frames = ArrayDeque<ShortArray>()
        var started = false; var stopped = false; var released = false
        var duringRead: (() -> Unit)? = null
        override fun start() { started = true }
        override fun read(samples: ShortArray): Int {
            if (stopped || frames.isEmpty()) return 0
            val frame = frames.removeFirst()
            duringRead?.also { duringRead = null; it() }
            frame.copyInto(samples)
            return frame.size
        }
        override fun stop() { stopped = true }
        override fun release() { released = true }
        fun speech(count: Int = 5) { repeat(count) { frames.addLast(ShortArray(RealtimeProtocol.CHUNK_SAMPLES) { (1_000 + it).toShort() }) } }
    }
    private class Socket(private val req: Request) : WebSocket {
        val sent = mutableListOf<JSONObject>()
        var cancelled = false; var failAppend = false
        override fun request() = req
        override fun queueSize() = 0L
        override fun send(text: String): Boolean {
            val row = JSONObject(text)
            if (failAppend && row.optString("type") == "input_audio_buffer.append") return false
            sent += row; return true
        }
        override fun send(bytes: ByteString) = false
        override fun close(code: Int, reason: String?) = true
        override fun cancel() { cancelled = true }
    }
    private class Boundary(scope: TestScope) : AutoCloseable {
        val app = RuntimeEnvironment.getApplication() as AlienApp
        val input = Input()
        val file = File.createTempFile("startup-fixture-", ".wav", app.cacheDir).also { check(it.delete()) }
        val audio = PendingAudio(file)
        lateinit var socket: Socket
        lateinit var network: WebSocketListener
        var captured = 0; var connected = 0; var commits = 0; var interrupted = 0
        val failures = mutableListOf<String>()
        val events = mutableListOf<JSONObject>()
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val transport = RealtimeTransport(app, audio, object : RealtimeTransport.Listener {
            override fun event(value: JSONObject) { events += value }
            override fun captured() { captured++ }
            override fun connected() { connected++ }
            override fun turnCommitted() { commits++ }
            override fun interrupted() { interrupted++ }
            override fun reconnected() {}
            override fun failure(message: String) { failures += message }
        }, { input }, dispatcher, dispatcher)
        init {
            val api = OpenAiClient(WebSocket.Factory { request, listener ->
                network = listener; Socket(request).also { socket = it }
            })
            transport.connect(api, "test-placeholder-not-a-key", "en", "")
        }
        fun open() { network.onOpen(socket, Response.Builder().request(socket.request()).protocol(Protocol.HTTP_1_1).code(101).message("Switching Protocols").build()) }
        fun ready() { network.onMessage(socket, JSONObject().put("type", "session.updated").put("session", RealtimeProtocol.sessionConfig("en", "")).toString()) }
        fun pumpUi() { shadowOf(Looper.getMainLooper()).idle() }
        fun uploadedAudio(): ByteArray = socket.sent.filter { it.optString("type") == "input_audio_buffer.append" }
            .flatMap { Base64.getDecoder().decode(it.getString("audio")).toList() }.toByteArray()
        override fun close() { transport.close(); audio.close(); file.delete() }
    }

    @Test fun microphoneAndRecoveryCaptureStartBeforeNetworkIsOpen() = runTest {
        Boundary(this).use { b ->
            b.input.speech(); runCurrent(); b.pumpUi()
            assertTrue(b.input.started); assertEquals(1, b.captured); assertEquals(0, b.connected)
            assertTrue("No audio may precede authenticated session configuration", b.socket.sent.isEmpty())
            b.open(); b.ready(); runCurrent(); b.pumpUi()
            assertEquals(1, b.connected)
            val uploaded = b.uploadedAudio()
            assertEquals(RealtimeProtocol.MIN_COMMIT_BYTES, uploaded.size)
            var finished = false; b.transport.finishCapture { finished = true }
            runCurrent(); assertFalse("Finalization must await the capture job", finished)
            advanceTimeBy(5); runCurrent(); b.pumpUi(); assertTrue(finished); b.audio.close()
            assertArrayEquals(uploaded, b.file.readBytes().drop(44).toByteArray())
            assertEquals(1, b.commits); assertFalse(b.transport.hasPendingCoverage)
        }
    }
    @Test fun switchingBeforeHandshakePreservesAudioBoundariesAndLateResultDestinations() = runTest {
        Boundary(this).use { b ->
            b.input.speech(); runCurrent()
            b.transport.switchEditor(2)
            b.input.speech(); advanceTimeBy(5); runCurrent()
            b.open(); b.ready(); runCurrent(); b.pumpUi()
            assertEquals(1, b.commits)
            assertTrue(b.transport.commit()); b.pumpUi(); assertEquals(2, b.commits)
            for (item in listOf("old", "new")) b.network.onMessage(b.socket,
                JSONObject().put("type", "input_audio_buffer.committed").put("item_id", item).toString())
            b.pumpUi()
            b.network.onMessage(b.socket, JSONObject().put("type", "conversation.item.input_audio_transcription.completed")
                .put("item_id", "old").put("transcript", "delayed old result").toString())
            b.pumpUi()
            assertEquals(listOf(0L, 2L, 0L), b.events.map { it.getLong("_editor_epoch") })
            assertTrue(b.input.started); assertFalse(b.input.stopped); assertEquals(0, b.interrupted)
            assertEquals(0, b.failures.size)
        }
    }
    @Test fun switchingAReadyConnectionCommitsOldFramesBeforeNewFramesWithoutStoppingCapture() = runTest {
        Boundary(this).use { b ->
            b.open(); b.ready(); b.input.speech(); runCurrent(); b.pumpUi()
            b.transport.switchEditor(2); b.pumpUi()
            b.input.speech(); advanceTimeBy(5); runCurrent()
            assertTrue(b.transport.commit()); b.pumpUi()
            val messages = b.socket.sent.map { it.getString("type") }
            assertEquals(listOf("session.update") + List(5) { "input_audio_buffer.append" } +
                listOf("input_audio_buffer.commit") + List(5) { "input_audio_buffer.append" } +
                listOf("input_audio_buffer.commit"), messages)
            assertFalse(b.input.stopped); assertEquals(2, b.commits)
            for (item in listOf("old", "new")) b.network.onMessage(b.socket,
                JSONObject().put("type", "input_audio_buffer.committed").put("item_id", item).toString())
            b.pumpUi(); assertEquals(listOf(0L, 2L), b.events.map { it.getLong("_editor_epoch") })
        }
    }
    @Test fun liveSpeechBeforeCommitBelongsToTheFocusedFieldUnlessAnotherFieldsCommitIsInFlight() = runTest {
        Boundary(this).use { b ->
            fun delta(item: String) = b.network.onMessage(b.socket, JSONObject()
                .put("type", "conversation.item.input_audio_transcription.delta").put("item_id", item).put("delta", "words").toString())
            fun committed(item: String) = b.network.onMessage(b.socket,
                JSONObject().put("type", "input_audio_buffer.committed").put("item_id", item).toString())
            b.open(); b.ready(); b.input.speech(); runCurrent(); b.pumpUi()
            // The live model streams a turn before its commit; no earlier field can own it.
            delta("first"); b.pumpUi()
            b.transport.switchEditor(2); b.input.speech(); advanceTimeBy(5); runCurrent(); b.pumpUi()
            // The old field's commit is unacknowledged: either field could own these words.
            delta("first"); delta("second"); b.pumpUi()
            committed("first"); delta("second"); b.pumpUi()
            assertEquals(listOf(0L, -1L, -1L, 0L, 2L), b.events.map { it.getLong("_editor_epoch") })
            assertEquals(listOf(false, false, false, true, false), b.events.map { it.optBoolean("_editor_epoch_acknowledged") })
        }
    }
    @Test fun stopBeforeHandshakeStillFlushesTheFirstWordsExactlyOnce() = runTest {
        Boundary(this).use { b ->
            b.input.speech(); runCurrent(); b.pumpUi()
            var finished: Boolean? = null
            b.transport.finishCapture { finished = it }
            runCurrent(); advanceTimeBy(5); runCurrent(); assertNull(finished); assertTrue(b.input.stopped)
            b.open(); b.ready(); runCurrent(); b.pumpUi()
            assertEquals(true, finished); assertEquals(1, b.commits)
            b.ready(); runCurrent(); b.pumpUi(); assertEquals(1, b.commits)
            b.audio.close(); assertArrayEquals(b.file.readBytes().drop(44).toByteArray(), b.uploadedAudio())
        }
    }
    @Test fun stopDuringUnresponsiveSetupRetainsAudioAndRevokesLateAcknowledgement() = runTest {
        Boundary(this).use { b ->
            b.input.speech(); runCurrent(); b.pumpUi()
            var covered: Boolean? = null
            b.transport.finishCapture { covered = it }
            runCurrent(); advanceTimeBy(5); runCurrent()
            assertNull("The drained recording still waits for its server", covered)
            advanceTimeBy(15_001); runCurrent(); b.audio.close()
            assertEquals(false, covered); assertTrue(b.socket.cancelled)
            assertEquals(4_800L, PendingAudio.repair(b.file).dataBytes)
            b.open(); b.ready(); b.pumpUi()
            assertEquals(0, b.connected); assertTrue(b.socket.sent.isEmpty())
        }
    }
    @Test fun vadTurnRequestsDuringStartupKeepTheirPlaceBetweenAudioFrames() = runTest {
        Boundary(this).use { b ->
            b.input.speech(); runCurrent(); b.transport.commit(); b.pumpUi()
            assertEquals(0, b.commits)
            b.input.speech(); advanceTimeBy(5); runCurrent()
            b.open(); b.ready(); runCurrent(); b.pumpUi()
            assertEquals(1, b.commits)
            assertEquals(listOf("session.update") + List(5) { "input_audio_buffer.append" } +
                listOf("input_audio_buffer.commit") + List(5) { "input_audio_buffer.append" },
                b.socket.sent.map { it.getString("type") })
            b.transport.finishCapture {}; runCurrent(); advanceTimeBy(5); runCurrent(); b.pumpUi(); assertEquals(2, b.commits)
            b.audio.close(); assertArrayEquals(b.file.readBytes().drop(44).toByteArray(), b.uploadedAudio())
        }
    }
    @Test fun shortStopAddsOnlySilenceToNetworkEnvelopeAndKeepsOriginalRecoveryBytes() = runTest {
        Boundary(this).use { b ->
            b.input.speech(1); runCurrent(); b.open(); b.ready(); runCurrent(); b.pumpUi()
            b.transport.finishCapture {}; runCurrent(); advanceTimeBy(5); runCurrent(); b.pumpUi(); b.audio.close()
            val captured = b.file.readBytes().drop(44).toByteArray(); val uploaded = b.uploadedAudio()
            assertEquals(960, captured.size); assertEquals(4_800, uploaded.size)
            assertArrayEquals(captured, uploaded.take(captured.size).toByteArray())
            assertTrue(uploaded.drop(captured.size).all { it == 0.toByte() })
            assertEquals(1, b.commits)
        }
    }
    @Test fun inFlightFinalReadIsIncludedBeforeCommitAndWaveFinalization() = runTest {
        Boundary(this).use { b ->
            b.open(); b.ready(); b.input.speech(1)
            var finished = false
            b.input.duringRead = { b.transport.finishCapture { finished = true } }
            runCurrent(); b.pumpUi(); assertTrue(finished); b.audio.close()
            assertEquals(960L, PendingAudio.repair(b.file).dataBytes)
            assertArrayEquals(b.file.readBytes().drop(44).toByteArray(), b.uploadedAudio().take(960).toByteArray())
            assertEquals(1, b.commits); assertEquals(0, b.captured)
        }
    }
    @Test fun failedStartupReplayRetainsTheCompleteWaveAndDoesNotSignalConnected() = runTest {
        Boundary(this).use { b ->
            b.input.speech(); runCurrent(); b.pumpUi()
            b.socket.failAppend = true; b.open(); b.ready(); runCurrent(); b.pumpUi()
            assertTrue(b.transport.hasPendingCoverage); assertTrue(b.socket.cancelled)
            assertEquals(1, b.interrupted); assertEquals(0, b.connected)
            var covered = true; b.transport.finishCapture { covered = it }
            runCurrent(); advanceTimeBy(5); runCurrent(); b.audio.close(); assertFalse(covered)
            assertEquals(4_800L, PendingAudio.repair(b.file).dataBytes)
        }
    }
    @Test fun accessDeniedAfterCaptureStopsMicrophoneAndPreservesAlreadyCapturedWords() = runTest {
        Boundary(this).use { b ->
            b.input.speech(); runCurrent(); b.pumpUi()
            val denied = Response.Builder().request(b.socket.request()).protocol(Protocol.HTTP_1_1)
                .code(403).message("Forbidden").build()
            b.network.onFailure(b.socket, IOException("fixture denied"), denied)
            b.pumpUi(); advanceTimeBy(5); runCurrent(); b.audio.close()
            assertTrue(b.input.stopped); assertTrue(b.input.released); assertTrue(b.socket.cancelled)
            assertEquals(1, b.failures.size); assertTrue(b.failures.single().contains("HTTP 403"))
            assertEquals(4_800L, PendingAudio.repair(b.file).dataBytes)
            b.ready(); b.pumpUi(); assertEquals(0, b.connected)
        }
    }
    @Test fun cancelledStartupCannotReviveCaptureOrSendBufferedAudio() = runTest {
        Boundary(this).use { b ->
            b.input.speech(); runCurrent(); b.transport.close(); runCurrent()
            b.open(); b.ready(); b.pumpUi()
            assertTrue(b.input.released); assertTrue(b.socket.cancelled)
            assertEquals(0, b.connected); assertEquals(0, b.captured)
            assertTrue(b.socket.sent.isEmpty())
        }
    }
    @Test fun lateSessionAcknowledgementAfterSetupTimeoutDoesNotBecomeConnected() = runTest {
        Boundary(this).use { b ->
            b.input.speech(); runCurrent(); b.open()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
            b.ready(); b.pumpUi()
            assertTrue(b.socket.cancelled); assertEquals(0, b.connected)
            assertEquals(1, b.interrupted); assertTrue(b.transport.hasPendingCoverage)
        }
    }
}
