package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

class WritingQualityTest {
    @Test fun lightPolishCanRepairReportedTypoWithoutChangingMeaning() {
        assertTrue(CorrectionGuard.accepts("i think thet is wrong", "I think that is wrong.", "B"))
        assertFalse(CorrectionGuard.accepts("I might not pay $15", "I will pay $50", "B"))
        assertFalse(CorrectionGuard.accepts("Call Thet", "Call That", "B"))
        assertFalse(CorrectionGuard.accepts("path/thet", "path/that", "B"))
    }
    @Test fun insertingInsideSentenceSeparatesBothUnownedAnchors() {
        val owner = EditorOwnership(EditorSnapshot(1, "I thinktoday", 7, 7))
        assertEquals(" that works ", owner.separateFromTypedWord("that works"))
        assertEquals("I think that works today", owner.proposedSnapshot(owner.separateFromTypedWord("that works")).text)
    }
}
