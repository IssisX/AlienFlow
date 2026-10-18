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
    const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE

    fun durationMs(pcmBytes: Int): Long = pcmBytes * 1_000L / BYTES_PER_SECOND

    fun sessionConfig(language: String, vocabulary: String): JSONObject {
        val transcription = JSONObject()
            .put("model", MODEL)
            .put("delay", "medium")
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
    /** [voiced] reports only this chunk: its RMS exceeded the speech threshold. */
    data class Decision(val commit: Boolean, val silenceStop: Boolean, val voiced: Boolean)

    private var voiced = false
    private var speechMs = 0L
    private var silenceMs = 0L
    private var lastVoiceMs: Long? = null

    fun inspect(pcm16: ByteArray, nowMs: Long, silenceStopSeconds: Int): Decision {
        if (pcm16.isEmpty() || pcm16.size % 2 != 0) return Decision(false, false, false)
        if (lastVoiceMs == null) lastVoiceMs = nowMs
        var sum = 0.0
        var count = 0
        for (index in 0 until pcm16.size - 1 step 2) {
            val value = ((pcm16[index].toInt() and 255) or
                (pcm16[index + 1].toInt() shl 8)).toShort().toDouble()
            sum += value * value
            count++
        }
        val durationMs = RealtimeProtocol.durationMs(pcm16.size)
        val chunkVoiced = count > 0 && sqrt(sum / count) > threshold
        if (chunkVoiced) {
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
        val shouldStop = silenceStopSeconds > 0 && nowMs - lastVoiceMs!! >= silenceStopSeconds * 1_000L
        if (shouldStop) lastVoiceMs = nowMs
        return Decision(shouldCommit, shouldStop, chunkVoiced)
    }
}

/**
 * Keeps long silences off the paid realtime stream. It never decides commits or touches
 * the local recovery WAV; it only chooses which captured frames are sent, in order.
 *
 * Speech and the first [idleAfterMs] of each pause pass through unchanged, so every
 * VAD commit (850 ms trailing silence) still follows all of its turn's audio. After
 * that, silence is held in a byte-bounded pre-roll; the next voiced chunk sends the
 * most recent [preRollMs] first, so a soft word onset is not clipped. A voiced frame
 * is never held or dropped. Frames are retained by reference: callers pass fresh arrays.
 */
class SilenceGate(private val idleAfterMs: Long = 2_000, preRollMs: Long = 500) {
    private val preRollBytes = (RealtimeProtocol.BYTES_PER_SECOND * preRollMs / 1_000L).toInt() and 1.inv()
    private val held = ArrayDeque<ByteArray>()
    private var heldBytes = 0
    /** Continuous silence since the last voiced chunk, saturated at [idleAfterMs]. */
    private var silentMs = 0L

    val heldPreRollBytes: Int get() = heldBytes

    fun offer(pcm16: ByteArray, voiced: Boolean, durationMs: Long): List<ByteArray> {
        if (voiced) {
            silentMs = 0L
            if (held.isEmpty()) return listOf(pcm16)
            val frames = ArrayList<ByteArray>(held.size + 1)
            frames.addAll(held); frames.add(pcm16)
            held.clear(); heldBytes = 0
            return frames
        }
        if (silentMs < idleAfterMs) {
            silentMs = minOf(idleAfterMs, silentMs + durationMs)
            return listOf(pcm16)
        }
        if (preRollBytes <= 0) return emptyList()
        // Only the newest silence is useful context; keep at most preRollBytes of it.
        val frame = if (pcm16.size > preRollBytes) pcm16.copyOfRange(pcm16.size - preRollBytes, pcm16.size) else pcm16
        held.addLast(frame); heldBytes += frame.size
        while (heldBytes > preRollBytes) heldBytes -= held.removeFirst().size
        return emptyList()
    }
}
