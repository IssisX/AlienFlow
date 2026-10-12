package io.github.issisx.alienflow

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RealtimeCapturePolicyTest {
    @Test fun sessionUsesDocumentedLiveTranscriptionContract() {
        val update = JSONObject(RealtimeProtocol.sessionUpdate("en", "ScraperX AC-42"))
        assertEquals("session.update", update.getString("type"))
        val session = update.getJSONObject("session")
        assertEquals("transcription", session.getString("type"))
        val input = session.getJSONObject("audio").getJSONObject("input")
        assertEquals("audio/pcm", input.getJSONObject("format").getString("type"))
        assertEquals(24_000, input.getJSONObject("format").getInt("rate"))
        assertTrue(input.isNull("turn_detection"))
        val transcription = input.getJSONObject("transcription")
        assertEquals("gpt-live-transcribe", transcription.getString("model"))
        assertEquals("minimal", transcription.getString("delay"))
        assertEquals("en", transcription.getJSONArray("languages").getString(0))
        assertEquals("ScraperX AC-42", transcription.getString("prompt"))
    }

    @Test fun pcmShortsBecomeLittleEndianBytes() {
        val bytes = RealtimeProtocol.pcm16LittleEndian(
            shortArrayOf(0x1234.toShort(), (-2).toShort(), 0),
            2,
        )
        assertArrayEquals(
            byteArrayOf(0x34, 0x12, 0xfe.toByte(), 0xff.toByte()),
            bytes,
        )
    }

    @Test fun appendAndCommitEventsContainOnlyExpectedProtocolFields() {
        val append = JSONObject(RealtimeProtocol.appendEvent(byteArrayOf(0, 1, 0xff.toByte())))
        assertEquals("input_audio_buffer.append", append.getString("type"))
        assertEquals("AAH/", append.getString("audio"))
        assertEquals(2, append.length())

        val commit = JSONObject(RealtimeProtocol.commitEvent("commit-7-3"))
        assertEquals("input_audio_buffer.commit", commit.getString("type"))
        assertEquals("commit-7-3", commit.getString("event_id"))
        assertEquals(2, commit.length())
    }

    @Test fun clientVadCommitsOnceAfterRealSpeechAndTrailingSilence() {
        val vad = ClientSideVad()
        val loud = RealtimeProtocol.pcm16LittleEndian(
            ShortArray(RealtimeProtocol.CHUNK_SAMPLES) { 1_000 },
            RealtimeProtocol.CHUNK_SAMPLES,
        )
        val quiet = ByteArray(RealtimeProtocol.CHUNK_BYTES)
        var now = 10_000L
        repeat(10) {
            assertFalse(vad.inspect(loud, now, 0).commit)
            now += RealtimeProtocol.CHUNK_MILLIS
        }
        repeat(42) {
            assertFalse(vad.inspect(quiet, now, 0).commit)
            now += RealtimeProtocol.CHUNK_MILLIS
        }
        assertTrue(vad.inspect(quiet, now, 0).commit)
        now += RealtimeProtocol.CHUNK_MILLIS
        assertFalse(vad.inspect(quiet, now, 0).commit)
    }

    @Test fun directTransportHasNoNativeWebRtcOwnershipPolicy() {
        assertEquals(
            "Android AudioRecord + OpenAI Realtime WebSocket",
            RealtimeProtocol.TRANSPORT_NAME,
        )
        assertEquals(24_000, RealtimeProtocol.SAMPLE_RATE)
        assertEquals(1, RealtimeProtocol.CHANNELS)
    }
}
