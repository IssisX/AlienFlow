package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

class StartupAudioBufferTest {
    @Test fun firstWordsAndTurnBoundariesSurviveHandshakeInTheirOriginalOrder() {
        val buffer = StartupAudioBuffer(16)
        val first = byteArrayOf(1, 2, 3, 4)
        assertTrue(buffer.append(first)); first[0] = 99
        assertTrue(buffer.commit()); assertFalse(buffer.commit())
        assertTrue(buffer.append(byteArrayOf(5, 6)))
        val seen = mutableListOf<String>()
        assertTrue(buffer.drain { row ->
            seen += when (row) {
                is StartupAudioBuffer.Entry.Audio -> row.pcm.joinToString()
                StartupAudioBuffer.Entry.Commit -> "commit"
            }; true
        })
        assertEquals(listOf("1, 2, 3, 4", "commit", "5, 6"), seen)
        assertEquals(0, buffer.bytes)
        assertTrue(buffer.drain { error("Audio must not replay twice") })
    }
    @Test fun overflowIsExplicitAndNeverEvictsTheBeginningOfAnUtterance() {
        val buffer = StartupAudioBuffer(4)
        assertTrue(buffer.append(byteArrayOf(1, 2, 3, 4)))
        assertFalse(buffer.append(byteArrayOf(5, 6)))
        assertEquals(4, buffer.bytes)
        buffer.drain { row -> assertArrayEquals(byteArrayOf(1, 2, 3, 4), (row as StartupAudioBuffer.Entry.Audio).pcm); true }
    }
    @Test fun partialSendFailureDoesNotReplayAcceptedFramesOrClaimAnIntactStream() {
        val buffer = StartupAudioBuffer(8)
        buffer.append(byteArrayOf(1, 2)); buffer.commit(); buffer.append(byteArrayOf(3, 4))
        var sent = 0
        assertFalse(buffer.drain { ++sent < 2 })
        assertEquals(2, sent); assertEquals(0, buffer.bytes)
        assertTrue(buffer.drain { error("The private WAV, not a partial queue, owns recovery") })
    }
}
