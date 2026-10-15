package io.github.issisx.alienflow

import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Streams one recording through the production session configuration, client VAD commit policy,
 * transcript ledger and cleanup merge. Only the microphone and the field are replaced: audio comes
 * from a file and the result is compared with a human reference instead of being typed.
 */
internal class RealAudioRun(
    private val api: OpenAiClient,
    private val key: String,
    val variant: Variant,
    private val paced: Boolean = true,
    private val timeoutMs: Long = 60_000,
) {
    data class Variant(val delay: String = "medium", val trailingSilenceMs: Long = 850, val prompt: String = "",
                       val mode: String = "B", val language: String = "en") {
        fun json(): JSONObject = JSONObject().put("delay", delay).put("trailing_silence_ms", trailingSilenceMs)
            .put("prompt", prompt.isNotEmpty()).put("mode", mode).put("language", language)
    }
    data class Result(val raw: String, val polished: String, val turns: Int, val firstSpeechAudioMs: Long?,
                      val firstTextLatencyMs: Long?, val commitToFinalMs: List<Long>, val stopToFinalMs: Long?,
                      val cleanupMs: Long?, val problems: List<String>)

    companion object {
        private const val VOICE_RMS = 450.0 // ClientSideVad's default threshold.

        /** PCM16 mono 24 kHz RIFF/WAVE only: the app's capture format. Anything else is refused, not resampled. */
        fun readWav(file: File): ByteArray {
            val bytes = file.readBytes()
            require(bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "${file.name}: not a RIFF/WAVE file" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var offset = 12; var format: IntArray? = null
            while (offset + 8 <= bytes.size) {
                val id = String(bytes, offset, 4, Charsets.US_ASCII)
                val size = buffer.getInt(offset + 4).toLong() and 0xffffffffL
                val body = offset + 8
                require(body + size <= bytes.size) { "${file.name}: truncated $id chunk" }
                when (id) {
                    "fmt " -> format = intArrayOf(buffer.getShort(body).toInt(), buffer.getShort(body + 2).toInt(),
                        buffer.getInt(body + 4), buffer.getShort(body + 14).toInt())
                    "data" -> {
                        val f = requireNotNull(format) { "${file.name}: data before fmt chunk" }
                        require(f.contentEquals(intArrayOf(1, RealtimeProtocol.CHANNELS, RealtimeProtocol.SAMPLE_RATE, 16))) {
                            "${file.name}: expected PCM16 mono ${RealtimeProtocol.SAMPLE_RATE} Hz, found format=${f[0]} " +
                                "channels=${f[1]} rate=${f[2]} bits=${f[3]}. Convert: ffmpeg -i in -ac 1 -ar 24000 -sample_fmt s16 out.wav"
                        }
                        return bytes.copyOfRange(body, body + size.toInt() - (size.toInt() % 2))
                    }
                }
                offset = body + size.toInt() + (size.toInt() and 1)
            }
            error("${file.name}: no data chunk")
        }

        fun firstVoicedMs(pcm: ByteArray): Long? {
            var offset = 0
            while (offset < pcm.size) {
                val end = minOf(pcm.size, offset + RealtimeProtocol.CHUNK_BYTES)
                var sum = 0.0; var count = 0
                for (k in offset until end - 1 step 2) {
                    val v = ((pcm[k].toInt() and 255) or (pcm[k + 1].toInt() shl 8)).toShort().toDouble(); sum += v * v; count++
                }
                if (count > 0 && sqrt(sum / count) > VOICE_RMS) return offset * 1_000L / (RealtimeProtocol.SAMPLE_RATE * 2)
                offset = end
            }
            return null
        }
    }

    fun sessionUpdate(): String {
        val session = RealtimeProtocol.sessionConfig(variant.language, "")
        val transcription = session.getJSONObject("audio").getJSONObject("input").getJSONObject("transcription")
        transcription.put("delay", variant.delay)
        if (variant.prompt.isNotEmpty()) transcription.put("prompt", variant.prompt)
        return JSONObject().put("type", "session.update").put("session", session).toString()
    }

    fun run(pcm: ByteArray, context: WritingContext): Result {
        val lock = Any()
        val events = mutableListOf<Pair<Long, JSONObject>>() // monotonic ms since stream start
        val problems = mutableListOf<String>()
        val ready = CountDownLatch(1)
        val closing = AtomicBoolean(false)
        val origin = AtomicLong(System.nanoTime())
        fun now() = (System.nanoTime() - origin.get()) / 1_000_000
        val socket = api.realtimeWebSocket(key, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(sessionUpdate()) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = try { JSONObject(text) } catch (_: Exception) { synchronized(lock) { problems += "invalid_event" }; return }
                val type = event.optString("type")
                if (RealtimeProtocol.isSessionReadyEvent(type)) {
                    RealtimeHandshake.acknowledgementProblem(event)?.let { synchronized(lock) { problems += it.diagnostic() } }
                    ready.countDown(); return
                }
                // Keep only metadata from errors; never retain provider messages.
                if (type == "error") synchronized(lock) { problems += LiveConnectionProblem.event(event).diagnostic() }
                synchronized(lock) { events += now() to event }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Our own cancel after the last result is not a provider failure.
                if (!closing.get()) synchronized(lock) { problems += LiveConnectionProblem.http(response).diagnostic() }
                ready.countDown()
            }
        })
        val commitTimes = mutableListOf<Long>()
        var endOfAudio = 0L
        try {
            check(ready.await(20, TimeUnit.SECONDS)) { "session acknowledgement timed out" }
            synchronized(lock) { if (problems.isNotEmpty()) return failed(problems.toList()) }
            origin.set(System.nanoTime())
            val vad = ClientSideVad(trailingSilenceMs = variant.trailingSilenceMs)
            var buffered = 0L; var audioMs = 0L
            fun commit() {
                if (buffered <= 0) return
                val padding = (RealtimeProtocol.MIN_COMMIT_BYTES - buffered).coerceAtLeast(0L).toInt()
                if (padding > 0) socket.send(RealtimeProtocol.appendEvent(ByteArray(padding)))
                socket.send(RealtimeProtocol.commitEvent("eval-${commitTimes.size + 1}"))
                commitTimes += now(); buffered = 0
            }
            var offset = 0
            while (offset < pcm.size) {
                val frame = pcm.copyOfRange(offset, minOf(pcm.size, offset + RealtimeProtocol.CHUNK_BYTES))
                offset += frame.size
                check(socket.send(RealtimeProtocol.appendEvent(frame))) { "audio append refused" }
                buffered += frame.size
                audioMs = offset * 1_000L / (RealtimeProtocol.SAMPLE_RATE * 2)
                if (vad.inspect(frame, audioMs, 0).commit) commit()
                if (paced) { val wait = audioMs - now(); if (wait > 0) Thread.sleep(wait) }
            }
            commit(); endOfAudio = now()
            // Every sent commit is acknowledged (or rejected) and every acknowledged item completes.
            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            while (System.nanoTime() < deadline) {
                val done = synchronized(lock) {
                    val acked = events.filter { it.second.optString("type") == "input_audio_buffer.committed" }.map { it.second.optString("item_id") }
                    val rejected = events.count { it.second.optJSONObject("error")?.optString("code") == "input_audio_buffer_commit_empty" }
                    val completed = events.filter { it.second.optString("type") == "conversation.item.input_audio_transcription.completed" }
                        .map { it.second.optString("item_id") }.toSet()
                    acked.size + rejected >= commitTimes.size && completed.containsAll(acked) ||
                        problems.any { !it.contains("input_audio_buffer_commit_empty") }
                }
                if (done) break
                Thread.sleep(20)
            }
        } finally { closing.set(true); socket.cancel() }
        val ordered = synchronized(lock) { events.toList() }
        val ledger = TranscriptLedger()
        for ((_, e) in ordered) {
            val item = e.optString("item_id", e.optJSONObject("item")?.optString("id").orEmpty())
            if (item.isBlank()) continue
            val previous = e.optString("previous_item_id").takeIf { it.isNotEmpty() }
            when (e.optString("type")) {
                "conversation.item.created", "input_audio_buffer.committed" -> ledger.event(e.optString("event_id"), item, previous, editorEpoch = 0)
                "conversation.item.input_audio_transcription.delta" -> ledger.event(e.optString("event_id"), item, previous, delta = e.optString("delta"), editorEpoch = 0)
                "conversation.item.input_audio_transcription.completed" -> ledger.event(e.optString("event_id"), item, previous, final = e.optString("transcript"), editorEpoch = 0)
            }
        }
        if (ledger.pending()) synchronized(lock) { problems += "unfinished_turns_after_${timeoutMs}ms" }
        val raw = ledger.stable()
        val acked = ordered.filter { it.second.optString("type") == "input_audio_buffer.committed" }.map { it.second.optString("item_id") }
        val completedAt = ordered.filter { it.second.optString("type") == "conversation.item.input_audio_transcription.completed" }
            .associate { it.second.optString("item_id") to it.first }
        val commitToFinal = acked.zip(commitTimes).mapNotNull { (item, sent) -> completedAt[item]?.minus(sent) }
        val firstText = ordered.firstOrNull { (_, e) ->
            (e.optString("type") == "conversation.item.input_audio_transcription.delta" && e.optString("delta").isNotBlank()) ||
                (e.optString("type") == "conversation.item.input_audio_transcription.completed" && e.optString("transcript").isNotBlank())
        }?.first
        val speech = firstVoicedMs(pcm)
        var polished = raw; var cleanupMs: Long? = null
        if (variant.mode != "A" && raw.isNotBlank()) {
            val started = System.nanoTime()
            try {
                polished = runBlocking {
                    CleanupMemo().cleanWithContext(raw, variant.mode, false, context) { chunk, local ->
                        api.cleanup(key, chunk, variant.mode, false, local)
                    }
                }
            } catch (e: SafeApiException) { synchronized(lock) { problems += "cleanup_http_${e.httpCode}" } }
            cleanupMs = (System.nanoTime() - started) / 1_000_000
        }
        return Result(raw, polished, acked.size, speech,
            if (paced && speech != null && firstText != null) firstText - speech else null,
            commitToFinal, completedAt.values.maxOrNull()?.minus(endOfAudio), cleanupMs, synchronized(lock) { problems.toList() })
    }

    private fun failed(problems: List<String>) = Result("", "", 0, null, null, emptyList(), null, null, problems)

    fun report(name: String, reference: String, result: Result): JSONObject = JSONObject()
        .put("recording", name).put("variant", variant.json()).put("turns", result.turns)
        .put("problems", JSONArray(result.problems))
        .put("raw", EvaluationMetrics.compare(reference, result.raw).json())
        .put("polished", EvaluationMetrics.compare(reference, result.polished).json())
        .put("latency_ms", JSONObject().put("first_speech_audio", result.firstSpeechAudioMs ?: JSONObject.NULL)
            .put("first_text_after_speech", result.firstTextLatencyMs ?: JSONObject.NULL)
            .put("commit_to_final", JSONArray(result.commitToFinalMs))
            .put("stop_to_final", result.stopToFinalMs ?: JSONObject.NULL)
            .put("cleanup", result.cleanupMs ?: JSONObject.NULL))
        .put("text", JSONObject().put("reference", reference).put("raw", result.raw).put("polished", result.polished))
}
