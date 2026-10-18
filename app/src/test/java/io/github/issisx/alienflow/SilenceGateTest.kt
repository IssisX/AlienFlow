package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

/** Pure frame-selection policy: which captured frames reach the paid stream, and in what order. */
class SilenceGateTest {
    private val frameMs = RealtimeProtocol.CHUNK_MILLIS.toLong()
    private var serial = 0
    /** Distinct arrays so identity proves which captured frame was returned. */
    private fun frame(bytes: Int = RealtimeProtocol.CHUNK_BYTES) = ByteArray(bytes) { (serial + it).toByte() }.also { serial++ }
    private fun pcm(value: Short) = ByteArray(RealtimeProtocol.CHUNK_BYTES).also {
        for (i in it.indices step 2) { it[i] = value.toByte(); it[i + 1] = (value.toInt() ushr 8).toByte() }
    }

    @Test fun speechPassesThroughUnchanged() {
        val gate = SilenceGate()
        repeat(500) { // 10 s of continuous speech
            val f = frame()
            val out = gate.offer(f, voiced = true, durationMs = frameMs)
            assertEquals(1, out.size); assertSame(f, out[0])
        }
        assertEquals(0, gate.heldPreRollBytes)
    }

    @Test fun theFirstIdlePeriodOfSilencePassesThroughThenSilenceIsHeld() {
        val gate = SilenceGate(idleAfterMs = 2_000, preRollMs = 500)
        gate.offer(frame(), true, frameMs)
        repeat(100) { // exactly 2 000 ms of silence still streams
            val f = frame()
            assertSame(f, gate.offer(f, false, frameMs).single())
        }
        repeat(1_000) { assertTrue(gate.offer(frame(), false, frameMs).isEmpty()) }
        // A session that starts silent also streams its first idle period (startup is unchanged).
        val fresh = SilenceGate(idleAfterMs = 60, preRollMs = 500)
        repeat(3) { assertEquals(1, fresh.offer(frame(), false, frameMs).size) }
        assertTrue(fresh.offer(frame(), false, frameMs).isEmpty())
    }

    @Test fun resumedSpeechFlushesTheNewestPreRollInOrderThenTheVoicedFrame() {
        val gate = SilenceGate(idleAfterMs = 2_000, preRollMs = 500)
        gate.offer(frame(), true, frameMs)
        repeat(100) { gate.offer(frame(), false, frameMs) }
        val held = List(60) { frame() }
        held.forEach { assertTrue(gate.offer(it, false, frameMs).isEmpty()) }
        assertEquals(RealtimeProtocol.BYTES_PER_SECOND / 2, gate.heldPreRollBytes)
        val voice = frame()
        val out = gate.offer(voice, true, frameMs)
        assertEquals(26, out.size) // 500 ms = 25 frames of 20 ms, then the speech frame
        held.takeLast(25).forEachIndexed { index, f -> assertSame(f, out[index]) }
        assertSame(voice, out.last())
        assertEquals(0, gate.heldPreRollBytes)
        // Pass-through resumes, including the next full idle period.
        repeat(100) { val f = frame(); assertSame(f, gate.offer(f, false, frameMs).single()) }
        assertTrue(gate.offer(frame(), false, frameMs).isEmpty())
    }

    @Test fun preRollIsByteBoundedEvenForOversizedOrIrregularFrames() {
        val gate = SilenceGate(idleAfterMs = 0, preRollMs = 100)
        val bound = RealtimeProtocol.BYTES_PER_SECOND / 10
        repeat(37) { gate.offer(frame(1_234), false, 25); assertTrue(gate.heldPreRollBytes <= bound) }
        val big = frame(bound * 3)
        gate.offer(big, false, 300)
        assertEquals(bound, gate.heldPreRollBytes)
        val out = gate.offer(frame(), true, frameMs)
        assertEquals(2, out.size)
        assertArrayEquals(big.copyOfRange(big.size - bound, big.size), out[0])
        assertEquals(0, SilenceGate(idleAfterMs = 0, preRollMs = 0).also { it.offer(frame(), false, frameMs) }.heldPreRollBytes)
    }

    @Test fun noVoicedFrameIsEverDroppedInAnyInterleaving() {
        val random = java.util.Random(7)
        val gate = SilenceGate(idleAfterMs = 200, preRollMs = 60)
        val position = java.util.IdentityHashMap<ByteArray, Int>()
        val voiced = mutableListOf<ByteArray>(); val sent = mutableListOf<ByteArray>()
        var speaking = false
        while (position.size < 20_000) {
            speaking = !speaking
            repeat(1 + random.nextInt(if (speaking) 15 else 40)) { // short and long pauses alike
                val f = frame()
                position[f] = position.size; if (speaking) voiced += f
                sent += gate.offer(f, speaking, frameMs)
            }
        }
        val sentCount = java.util.IdentityHashMap<ByteArray, Int>()
        sent.forEach { sentCount[it] = (sentCount[it] ?: 0) + 1 }
        voiced.forEach { assertEquals(1, sentCount[it]) }
        assertTrue("some long pauses were actually held", sent.size < position.size)
        // Everything sent is in capture order, without duplicates.
        val order = sent.map { position.getValue(it) }
        assertEquals(order.sorted().distinct(), order)
    }

    @Test fun vadAndGateSendEveryTurnsSpeechBeforeItsCommit() {
        val vad = ClientSideVad(); val gate = SilenceGate()
        val loud = List(10) { pcm(1_000) } + List(150) { pcm(0) } + List(10) { pcm(-1_200) } + List(60) { pcm(0) }
        val events = mutableListOf<Any>() // captured index of each appended frame, or "commit"
        var now = 50_000L
        loud.forEachIndexed { index, chunk ->
            // Same order as RealtimeTransport.captureLoop: decide, send gated frames, then commit.
            val decision = vad.inspect(chunk, now, 0)
            assertEquals(index < 10 || index in 160 until 170, decision.voiced)
            gate.offer(chunk, decision.voiced, RealtimeProtocol.durationMs(chunk.size)).forEach { f ->
                events += loud.indexOfFirst { it === f }
            }
            if (decision.commit) events += "commit"
            now += RealtimeProtocol.CHUNK_MILLIS
        }
        val commits = events.withIndex().filter { it.value == "commit" }.map { it.index }
        assertEquals(2, commits.size)
        // Each commit follows its own turn's last voiced frame and 850+ ms of streamed trailing silence.
        assertEquals(52, events[commits[0] - 1]); assertEquals(212, events[commits[1] - 1])
        assertTrue((0 until 10).all { it in events.subList(0, commits[0]) })
        assertTrue((160 until 170).all { it in events.subList(commits[0], commits[1]) })
        // Silence 110..134 is never sent; 135..159 is the pre-roll flushed before the second turn.
        val appended = events.filterIsInstance<Int>()
        assertEquals((0 until 110) + (135 until 230), appended)
    }
}
