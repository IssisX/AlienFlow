package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class NativeCrashSummaryTest {
    private fun varint(v: Long): ByteArray {
        val bytes = ByteArrayOutputStream(); var n = v
        do { val b = (n and 127).toInt(); n = n ushr 7; bytes.write(b or if (n != 0L) 128 else 0) } while (n != 0L)
        return bytes.toByteArray()
    }
    private fun number(id: Int, n: Long) = varint(id.toLong() shl 3) + varint(n)
    private fun field(id: Int, bytes: ByteArray) = varint((id.toLong() shl 3) or 2) + varint(bytes.size.toLong()) + bytes
    private fun text(id: Int, s: String) = field(id, s.toByteArray())
    @Test fun extractsOnlyCrashingThreadLocationsAndSignal() {
        val frame = number(1, 42) + text(4, "webrtc::AudioRecord::Start()") + text(6, "/private/path/libjingle_peerconnection_so.so") + text(8, "aabbcc")
        val thread = number(1, 9) + field(4, frame) + text(5, "private raw memory and secret-key")
        val input = number(6, 9) + field(10, number(1, 6) + number(3, 1)) +
            field(16, number(1, 9) + field(2, thread)) + text(14, "Authorization Bearer secret-key") + text(18, "private dictated words")
        val result = NativeCrashSummary.render(input)
        assertTrue(result.contains("signal=6")); assertTrue(result.contains("pc=2a"))
        assertTrue(result.contains("libjingle_peerconnection_so.so")); assertTrue(result.contains("aabbcc"))
        assertFalse(result.contains("private/path")); assertFalse(result.contains("secret-key")); assertFalse(result.contains("dictated words"))
    }
    @Test fun corruptedTraceDoesNotThrowOrPretendToKnowCrash() {
        assertTrue(NativeCrashSummary.render(byteArrayOf(0x82.toByte())).contains("could not be decoded"))
        assertTrue(NativeCrashSummary.render(ByteArray(NativeCrashSummary.MAX_BYTES + 1)).contains("exceeds"))
    }
    @Test fun doesNotSubstituteAnotherThreadForMissingCrashThread() {
        val thread = number(1, 8) + field(4, text(6, "other-thread.so"))
        val result = NativeCrashSummary.render(number(6, 9) + field(16, number(1, 8) + field(2, thread)))
        assertFalse(result.contains("other-thread.so")); assertTrue(result.contains("No crashing-thread"))
    }
}
