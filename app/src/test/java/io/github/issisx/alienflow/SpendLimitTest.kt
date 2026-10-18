package io.github.issisx.alienflow

import android.Manifest
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.util.Collections

/** In-process stand-in for the provider: no socket and no network. Polish echoes its input. */
internal class LocalProvider {
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val api = OpenAiClient(callFactory = Call.Factory { request -> requests += request.url.encodedPath; Answer(request) })
    private class Answer(private val request: Request) : Call {
        private var executed = false
        private var canceled = false
        override fun request() = request
        override fun execute(): Response { executed = true; return respond() }
        override fun enqueue(responseCallback: Callback) { executed = true; responseCallback.onResponse(this, respond()) }
        override fun cancel() { canceled = true }
        override fun isExecuted() = executed
        override fun isCanceled() = canceled
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = Answer(request)
        private fun respond(): Response {
            val body = if (request.url.encodedPath == "/v1/responses") {
                val input = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).getString("input")
                JSONObject().put("status", "completed").put("output", JSONArray().put(JSONObject().put("content",
                    JSONArray().put(JSONObject().put("type", "output_text").put("text", input))))).toString()
            } else JSONObject().put("text", "recovered words").toString()
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }
}

/** Real coordinator on the paused main looper with an injected direct IO dispatcher, a fixed
 * calendar date and the in-process provider. No microphone, key or network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class)
class SpendLimitTest {
    private val second = RealtimeProtocol.BYTES_PER_SECOND
    private val date = LocalDate.of(2026, 10, 18)
    private fun ui() = shadowOf(Looper.getMainLooper())
    private fun app() = (RuntimeEnvironment.getApplication() as AlienApp).also { it.store.prefs.edit().clear().commit() }
    private fun coordinator(app: AlienApp, store: LocalStore, provider: LocalProvider) =
        DictationCoordinator(app, store, provider.api, Dispatchers.Unconfined) { "fixture-not-a-key" }
            .also { ReflectionHelpers.setField(app, "coordinator", it) }
    private fun wav(file: File, dataBytes: Int): File {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(RealtimeProtocol.SAMPLE_RATE).putInt(second).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(dataBytes)
        file.writeBytes(header.array() + ByteArray(dataBytes))
        return file
    }

    @Test fun theHundredAndTwentyFirstPolishRequestIsNeverMade() {
        val app = app(); val store = LocalStore(app) { date }; val provider = LocalProvider()
        val coordinator = coordinator(app, store, provider)
        ReflectionHelpers.setField(coordinator, "active", true) // Light polish while speaking: the defaults.
        try {
            repeat(125) { index ->
                coordinator.event(JSONObject().put("type", "conversation.item.input_audio_transcription.completed")
                    .put("event_id", "event$index").put("item_id", "item$index")
                    .put("previous_item_id", if (index == 0) "" else "item${index - 1}").put("transcript", "phrase $index"))
                ui().idle()
                assertEquals("after turn $index", minOf(index + 1, DictationCoordinator.MAX_POLISH_REQUESTS_PER_SESSION), provider.requests.size)
            }
            val spoken = (0 until 125).joinToString(" ") { "phrase $it" }
            assertEquals("Recognized words are kept past the cap", spoken, coordinator.result)
            coordinator.stop(); ui().idle()
            assertFalse(coordinator.status, coordinator.finishing)
            assertEquals("The final pass after Stop counts toward the same cap", 120, provider.requests.size)
            assertEquals(spoken, coordinator.result)
            assertTrue(provider.requests.all { it == "/v1/responses" })
            assertEquals("Persisted at session end", 120, LocalStore(app) { date }.usage.today().polishRequests)
        } finally { coordinator.cancel(); ui().idle() }
    }

    @Test fun startIsRefusedOnceTodaysAudioReachesTheDailyLimit() {
        val app = app(); app.store.prefs.edit().putInt("daily_minutes", 5).commit()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val store = LocalStore(app) { date }; val provider = LocalProvider()
        store.usage.addRealtimeAudio(4 * 60 * second); store.usage.addRecoveryAudio(60L * second - 1)
        val coordinator = coordinator(app, store, provider)
        coordinator.start()
        assertEquals("Below the limit, start goes on to its next requirement", "Enable AlienFlow accessibility first", coordinator.status)
        store.usage.addRecoveryAudio(1)
        coordinator.start()
        assertFalse(coordinator.active)
        assertEquals("Daily limit of 5 minutes reached • change it in Settings", coordinator.status)
        app.store.prefs.edit().putInt("daily_minutes", 6).commit()
        coordinator.start()
        assertEquals("A higher limit allows today's dictation again", "Enable AlienFlow accessibility first", coordinator.status)
        assertTrue(provider.requests.isEmpty())
    }

    @Test fun recoveryLongerThanTheRemainingAllowanceIsRefusedAndKept() {
        val app = app(); app.store.prefs.edit().putInt("daily_minutes", 5).commit()
        val store = LocalStore(app) { date }; val provider = LocalProvider()
        store.usage.addRealtimeAudio((4 * 60 + 59) * second) // One second of today's five minutes remains.
        val coordinator = coordinator(app, store, provider)
        val tooLong = wav(File(store.audioDir, "too-long.wav"), second + 2)
        coordinator.recover(tooLong); ui().idle()
        assertEquals("Recording kept • it is longer than today's remaining allowance (1 s of 5 minutes). Change the daily limit in Settings",
            coordinator.status)
        assertTrue(tooLong.exists()); assertTrue(provider.requests.isEmpty())
        val fits = wav(File(store.audioDir, "fits.wav"), second)
        coordinator.recover(fits); ui().idle()
        assertEquals("Recovered to history • no field was edited", coordinator.status)
        assertEquals(listOf("/v1/audio/transcriptions"), provider.requests.toList())
        assertEquals("Uploaded payload is counted", second.toLong(), store.usage.today().recoveryBytes)
        assertEquals(0L, store.usage.remainingBytes(5))
        assertEquals("Flushed when recovery ends", second.toLong(), LocalStore(app) { date }.usage.today().recoveryBytes)
        assertFalse(fits.exists()); assertTrue(tooLong.exists())
    }
}
