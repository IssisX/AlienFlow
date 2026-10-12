package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

class StartupTimingsTest {
    @Test fun reportsFirstActualObservationsAndResetsForEachTap() {
        val timing = StartupTimings()
        timing.begin(1_000)
        timing.captured(1_038); timing.connected(2_123)
        assertTrue(timing.text(2_489)); assertFalse(timing.text(2_700))
        timing.connected(5_000); timing.captured(8_000)
        assertEquals("tap_to_microphone_ms=38; tap_to_server_ms=1123; tap_to_first_text_ms=1489", timing.metadata())
        timing.begin(10_000)
        assertEquals("tap_to_microphone_ms=pending; tap_to_server_ms=pending; tap_to_first_text_ms=pending", timing.metadata())
    }
    @Test fun readinessDoesNotBecomeGreenBecauseTimePassedOrOnlyServerConnected() {
        assertEquals(DictationReadiness.STARTING, DictationReadiness.from(true, false, false, false, false))
        assertEquals(DictationReadiness.STARTING, DictationReadiness.from(true, false, false, true, false))
        assertEquals(DictationReadiness.BUFFERING, DictationReadiness.from(true, false, true, false, false))
        assertEquals(DictationReadiness.LIVE, DictationReadiness.from(true, false, true, true, false))
        assertEquals(DictationReadiness.LOCAL, DictationReadiness.from(true, false, true, false, true))
        assertEquals(DictationReadiness.LIVE, DictationReadiness.from(true, false, true, true, true))
        assertEquals(DictationReadiness.FINISHING, DictationReadiness.from(false, true, true, true, false))
        assertEquals(DictationReadiness.IDLE, DictationReadiness.from(false, false, true, true, false))
    }
}
