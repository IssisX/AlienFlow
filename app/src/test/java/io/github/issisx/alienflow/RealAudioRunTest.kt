package io.github.issisx.alienflow

import android.app.Application
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections

/** The evaluation path against a scripted provider: same frames, commit policy, ledger and merge as production. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RealAudioRunTest {
    private fun pcm(vararg segments: Pair<Boolean, Int>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for ((voiced, ms) in segments) repeat(RealtimeProtocol.SAMPLE_RATE * ms / 1_000) { index ->
            val value = if (voiced) (if (index / 24 % 2 == 0) 3_000 else -3_000) else 0
            out.write(value and 255); out.write(value shr 8 and 255)
        }
        return out.toByteArray()
    }

    @Test fun recordingStreamsThroughProductionCommitPolicyAndIsScoredSeparatelyBeforeAndAfterPolish() {
        val sessions = Collections.synchronizedList(mutableListOf<JSONObject>())
        val sent = Collections.synchronizedList(mutableListOf<String>())
        val transcripts = listOf("So I was thinking.", "We could go tomorrow.")
        val provider = object : WebSocketListener() {
            var turn = 0; var streaming = false
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = JSONObject(text); val type = event.getString("type"); sent += type
                when (type) {
                    "session.update" -> { sessions += event.getJSONObject("session")
                        webSocket.send(JSONObject().put("type", "session.updated").put("session", event.getJSONObject("session")).toString()) }
                    "input_audio_buffer.append" -> if (!streaming) { streaming = true
                        webSocket.send(JSONObject().put("type", "conversation.item.input_audio_transcription.delta")
                            .put("event_id", "d$turn").put("item_id", "item-$turn").put("delta", "so").toString()) }
                    "input_audio_buffer.commit" -> { streaming = false
                        webSocket.send(JSONObject().put("type", "input_audio_buffer.committed").put("item_id", "item-$turn")
                            .put("previous_item_id", if (turn == 0) JSONObject.NULL else "item-${turn - 1}").toString())
                        webSocket.send(JSONObject().put("type", "conversation.item.input_audio_transcription.completed")
                            .put("event_id", "f$turn").put("item_id", "item-$turn").put("transcript", transcripts[turn]).toString())
                        turn++ }
                }
            }
        }
        val cleanups = Collections.synchronizedList(mutableListOf<JSONObject>())
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/realtime" -> MockResponse().withWebSocketUpgrade(provider)
                    "/v1/responses" -> { cleanups += JSONObject(request.body.readUtf8())
                        MockResponse().setBody(JSONObject().put("status", "completed").put("output", JSONArray().put(JSONObject()
                            .put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", "So I was thinking we could go tomorrow."))))).toString()) }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val http = OkHttpClient()
            val api = OpenAiClient(
                WebSocket.Factory { request, listener -> http.newWebSocket(request.newBuilder().url(server.url("/realtime")).build(), listener) },
                Call.Factory { request -> http.newCall(request.newBuilder().url(server.url("/v1/responses")).build()) })
            val run = RealAudioRun(api, "test-placeholder-not-a-key", RealAudioRun.Variant(delay = "high", prompt = "Dictation."),
                paced = false, timeoutMs = 5_000)
            val result = run.run(pcm(true to 500, false to 1_000, true to 500, false to 300), WritingContext("Earlier text. ", ""))

            assertEquals(emptyList<String>(), result.problems)
            assertEquals(2, result.turns)
            assertEquals("Audio must follow the session update; one VAD commit plus the final commit",
                2, sent.count { it == "input_audio_buffer.commit" })
            assertEquals("session.update", sent.first())
            val transcription = sessions.single().getJSONObject("audio").getJSONObject("input").getJSONObject("transcription")
            assertEquals("high", transcription.getString("delay")); assertEquals("Dictation.", transcription.getString("prompt"))
            assertEquals(RealtimeProtocol.MODEL, transcription.getString("model"))
            assertEquals("So I was thinking. We could go tomorrow.", result.raw)
            assertEquals("So I was thinking we could go tomorrow.", result.polished)
            assertEquals("Earlier text. ", JSONObject(cleanups.single().getString("input")).getString("before"))
            assertEquals(2, result.commitToFinalMs.size); assertEquals(0L, result.firstSpeechAudioMs)
            assertNull("Unpaced runs do not claim live latency", result.firstTextLatencyMs)

            val report = run.report("pause", "So I was thinking we could go tomorrow.", result)
            val raw = report.getJSONObject("raw"); val polished = report.getJSONObject("polished")
            assertEquals(1, raw.getInt("case_errors")); assertEquals(1, raw.getInt("extra_sentence_breaks"))
            assertEquals(0, polished.getInt("case_errors")); assertEquals(0.0, polished.getDouble("word_error_rate"), 0.0)
        }
    }

    @Test fun onlyTheAppCaptureFormatIsAccepted() {
        fun wav(rate: Int, channels: Int, data: ByteArray): File = File.createTempFile("eval", ".wav").apply {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray()).put("fmt ".toByteArray())
                .putInt(16).putShort(1).putShort(channels.toShort()).putInt(rate).putInt(rate * channels * 2)
                .putShort((channels * 2).toShort()).putShort(16).put("data".toByteArray()).putInt(data.size)
            writeBytes(header.array() + data); deleteOnExit()
        }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), RealAudioRun.readWav(wav(24_000, 1, byteArrayOf(1, 2, 3, 4))))
        val refused = runCatching { RealAudioRun.readWav(wav(16_000, 1, byteArrayOf(1, 2))) }.exceptionOrNull()
        assertTrue(refused?.message.orEmpty().contains("expected PCM16 mono 24000 Hz"))
        assertNull(RealAudioRun.firstVoicedMs(ByteArray(4_800)))
    }
}
