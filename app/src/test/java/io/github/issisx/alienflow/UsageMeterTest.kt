package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Pure counting, retention, limit and persistence logic with an injected calendar date. */
class UsageMeterTest {
    private class Fixture(var date: LocalDate = LocalDate.of(2026, 10, 18)) {
        var stored: String? = null
        var writes = 0
        fun meter() = UsageMeter({ stored }, { stored = it; writes++ }, { date })
    }
    private val second = RealtimeProtocol.BYTES_PER_SECOND
    private fun day(day: Int) = LocalDate.of(2026, 10, day)

    @Test fun countsStartAfreshWhenTheLocalDateRollsOver() {
        val f = Fixture(); val meter = f.meter()
        meter.addRealtimeAudio(90 * second); meter.addRecoveryAudio(30L * second)
        meter.addPolishRequest(); meter.addConnectionCheck()
        assertEquals(120L * second, meter.today().audioBytes)
        f.date = day(19)
        assertEquals(UsageMeter.Day(day(19)), meter.today())
        assertFalse(meter.limitReached(1)); assertEquals(60L * second, meter.remainingBytes(1))
        meter.addRealtimeAudio(second)
        assertEquals(listOf(UsageMeter.Day(day(19), realtimeBytes = second.toLong()),
            UsageMeter.Day(day(18), 90L * second, 30L * second, 1, 1)), meter.days())
    }

    @Test fun zeroMinutesIsOffAndNeverReachesTheLimit() {
        val meter = Fixture().meter()
        meter.addRealtimeAudio(600 * second); meter.addRecoveryAudio(600L * second)
        assertFalse(meter.limitReached(0))
        assertEquals(Long.MAX_VALUE - 1200L * second, meter.remainingBytes(0))
        assertTrue(meter.diagnostic(0).contains("Daily dictation limit: Off of realtime"))
        assertTrue(meter.limitReached(20))
    }

    @Test fun onlyTheLastSevenDaysAreKeptInMemoryAndOnDisk() {
        val f = Fixture(day(1)); val meter = f.meter()
        for (date in 1..10) { f.date = day(date); meter.addConnectionCheck() }
        assertEquals((10 downTo 4).map(::day), meter.days().map { it.date })
        meter.flush()
        assertEquals((4..10).map(::day), UsageMeter.decode(f.stored!!).map { it.date })
        // A clock moved back drops entries now in the future; they cannot linger in the window.
        f.date = day(8)
        assertEquals((8 downTo 4).map(::day), meter.days().map { it.date })
        // Entries older than the window are dropped when loaded, too.
        val reloaded = Fixture(day(17)).apply { stored = f.stored }.meter()
        assertTrue(reloaded.days().isEmpty())
    }

    @Test fun limitAndRemainingCountRealtimeAndRecoveryAudioOnly() {
        val meter = Fixture().meter()
        assertEquals(5L * 60 * second, meter.remainingBytes(5)); assertFalse(meter.limitReached(5))
        assertEquals(4L * 60 * second, meter.addRealtimeAudio(4 * 60 * second))
        repeat(3) { meter.addPolishRequest(); meter.addConnectionCheck() }
        assertEquals(60L * second, meter.remainingBytes(5))
        assertEquals(5L * 60 * second - 1, meter.addRecoveryAudio(60L * second - 1))
        assertFalse(meter.limitReached(5)); assertEquals(1L, meter.remainingBytes(5))
        meter.addRealtimeAudio(1)
        assertTrue(meter.limitReached(5)); assertEquals(0L, meter.remainingBytes(5))
        meter.addRealtimeAudio(second)
        assertEquals("Remaining allowance never goes negative", 0L, meter.remainingBytes(5))
        assertFalse(meter.limitReached(6))
        assertEquals(30L * 60 * second, UsageMeter.limitBytes(30))
        assertEquals(meter.today().audioBytes, meter.addRealtimeAudio(0))
    }

    @Test fun writesAtMostEveryFiveSecondsOfAudioOrOnFlushAndRoundTrips() {
        val f = Fixture(); val meter = f.meter()
        meter.addPolishRequest()
        repeat(249) { meter.addRealtimeAudio(RealtimeProtocol.CHUNK_BYTES) } // 4.98 s of 20 ms frames
        assertEquals("Polish requests and audio below 5 s stay in memory", 0, f.writes)
        meter.addRealtimeAudio(RealtimeProtocol.CHUNK_BYTES) // 5.00 s
        assertEquals(1, f.writes)
        meter.addPolishRequest(); meter.addConnectionCheck(); assertEquals(1, f.writes)
        meter.flush(); assertEquals(2, f.writes)
        meter.flush(); assertEquals("Nothing new, nothing written", 2, f.writes)
        val expected = UsageMeter.Day(day(18), 5L * second, 0, 2, 1)
        assertEquals(expected, meter.today())
        assertEquals(expected, f.meter().today())
        assertEquals(expected, UsageMeter.decode(UsageMeter.encode(listOf(expected))).single())
    }

    @Test fun storesOnlyNumericMetadataAndSkipsCorruptRecords() {
        val f = Fixture()
        f.stored = "2026-10-17,96000,0,1,0;garbage;2026-10-16,x,1,1,1;secret words,1,2,3,4;2026-10-15,-5,0,0,0"
        val meter = f.meter()
        meter.addRealtimeAudio(3 * second); meter.addRecoveryAudio(second.toLong()); meter.addPolishRequest(); meter.addConnectionCheck()
        meter.flush()
        val record = """\d{4}-\d{2}-\d{2},\d+,\d+,\d+,\d+"""
        assertTrue(f.stored, f.stored!!.matches(Regex("$record(;$record)*")))
        assertEquals("2026-10-17,96000,0,1,0;2026-10-18,144000,48000,1,1", f.stored)
        assertEquals(listOf(day(18), day(17)), meter.days().map { it.date })
        // A failed read or write never stops counting.
        val broken = UsageMeter({ error("unreadable") }, { error("unwritable") }, { day(18) })
        val bytes = UsageMeter.PERSIST_EVERY_BYTES
        assertEquals(bytes, broken.addRealtimeAudio(bytes.toInt()))
        broken.flush(); assertEquals(bytes, broken.today().audioBytes)
    }

    @Test fun summaryAndDiagnosticAreMetadataOnly() {
        val f = Fixture(); val meter = f.meter()
        assertEquals("Used today: 0 s of audio · 0 polish requests", meter.summary())
        assertTrue(meter.diagnostic(30).contains("No usage recorded in the last 7 days."))
        meter.addRealtimeAudio(150 * second); meter.addRecoveryAudio(42L * second); meter.addPolishRequest()
        assertEquals("Used today: 3 min 12 s of audio · 1 polish request", meter.summary())
        f.date = day(19); meter.addConnectionCheck(); meter.addPolishRequest(); meter.addPolishRequest()
        assertEquals("Used today: 0 s of audio · 2 polish requests", meter.summary())
        assertEquals("""
            USAGE ON THIS PHONE (metadata only)
            Daily dictation limit: 45 minutes of realtime and recovery audio. Local counts, not an OpenAI bill.
            2026-10-19 realtime_audio_seconds=0; recovery_audio_seconds=0; polish_requests=2; connection_checks=1
            2026-10-18 realtime_audio_seconds=150; recovery_audio_seconds=42; polish_requests=1; connection_checks=0
            """.trimIndent() + "\n", meter.diagnostic(45))
    }
}
