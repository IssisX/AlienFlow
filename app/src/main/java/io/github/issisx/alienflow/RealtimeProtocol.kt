package io.github.issisx.alienflow

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import kotlin.math.sqrt

/** Pure protocol/audio policy shared by production code and JVM tests. */
object RealtimeProtocol {
    const val MODEL = "gpt-live-transcribe"
    const val SAMPLE_RATE = 24_000
    const val CHANNELS = 1
    const val BYTES_PER_SAMPLE = 2
    const val CHUNK_MILLIS = 20
    const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_MILLIS / 1_000
    const val CHUNK_BYTES = CHUNK_SAMPLES * BYTES_PER_SAMPLE
    const val MIN_COMMIT_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE / 10
    const val MAX_WEBSOCKET_QUEUE_BYTES = 2L * 1024 * 1024
    const val TRANSPORT_NAME = "Android AudioRecord + OpenAI Realtime WebSocket"

    fun sessionConfig(language: String, vocabulary: String): JSONObject {
        val transcription = JSONObject()
            .put("model", MODEL)
            .put("delay", "minimal")
        if (language.isNotBlank()) {
            transcription.put("languages", JSONArray().put(language.trim()))
        }
        val hints = VocabularyHints.parse(vocabulary)
        if (hints.isNotEmpty()) transcription.put("keywords", JSONArray(hints))
        val input = JSONObject()
            .put("format", JSONObject().put("type", "audio/pcm").put("rate", SAMPLE_RATE))
            .put("transcription", transcription)
            .put("turn_detection", JSONObject.NULL)
        return JSONObject()
            .put("type", "transcription")
            .put("audio", JSONObject().put("input", input))
    }

    fun sessionUpdate(language: String, vocabulary: String): String = JSONObject()
        .put("type", "session.update")
        .put("session", sessionConfig(language, vocabulary))
        .toString()

    fun appendEvent(pcm16: ByteArray): String = JSONObject()
        .put("type", "input_audio_buffer.append")
        .put("audio", Base64.getEncoder().encodeToString(pcm16))
        .toString()

    fun commitEvent(id: String): String = JSONObject()
        .put("event_id", id)
        .put("type", "input_audio_buffer.commit")
        .toString()

    fun isSessionReadyEvent(type: String): Boolean =
        type == "session.updated" || type == "transcription_session.updated"

    /** Android AudioRecord delivers native-endian shorts; the API requires PCM16 little-endian. */
    fun pcm16LittleEndian(samples: ShortArray, count: Int): ByteArray {
        require(count in 0..samples.size)
        val output = ByteArray(count * BYTES_PER_SAMPLE)
        for (index in 0 until count) {
            val value = samples[index].toInt()
            output[index * 2] = value.toByte()
            output[index * 2 + 1] = (value ushr 8).toByte()
        }
        return output
    }
}

/** Conservative client-side VAD. It decides only when to commit already-buffered audio. */
class ClientSideVad(
    private val threshold: Double = 450.0,
    private val minimumSpeechMs: Long = 120,
    private val trailingSilenceMs: Long = 850,
) {
    data class Decision(val commit: Boolean, val silenceStop: Boolean)

    private var voiced = false
    private var speechMs = 0L
    private var silenceMs = 0L
    private var lastVoiceMs = System.currentTimeMillis()

    fun inspect(pcm16: ByteArray, nowMs: Long, silenceStopSeconds: Int): Decision {
        if (pcm16.isEmpty() || pcm16.size % 2 != 0) return Decision(false, false)
        var sum = 0.0
        var count = 0
        for (index in 0 until pcm16.size - 1 step 2) {
            val value = ((pcm16[index].toInt() and 255) or
                (pcm16[index + 1].toInt() shl 8)).toShort().toDouble()
            sum += value * value
            count++
        }
        val durationMs = pcm16.size * 1_000L /
            (RealtimeProtocol.SAMPLE_RATE * RealtimeProtocol.CHANNELS * RealtimeProtocol.BYTES_PER_SAMPLE)
        if (count > 0 && sqrt(sum / count) > threshold) {
            voiced = true
            speechMs += durationMs
            silenceMs = 0
            lastVoiceMs = nowMs
        } else {
            silenceMs += durationMs
        }
        val shouldCommit = voiced && speechMs >= minimumSpeechMs && silenceMs >= trailingSilenceMs
        if (shouldCommit) {
            voiced = false
            speechMs = 0
            silenceMs = 0
        }
        val shouldStop = silenceStopSeconds > 0 && nowMs - lastVoiceMs >= silenceStopSeconds * 1_000L
        if (shouldStop) lastVoiceMs = nowMs
        return Decision(shouldCommit, shouldStop)
    }
}
