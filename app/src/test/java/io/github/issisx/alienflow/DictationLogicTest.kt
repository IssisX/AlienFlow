package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DictationLogicTest {
    @Test fun commitEpochsFollowAcknowledgedAudioRatherThanTheCurrentlyFocusedApp() {
        val coverage = AudioCoverage()
        coverage.sent("old-commit", 4800, 0); coverage.sent("new-commit", 4800, 2)
        coverage.acknowledge("old-item"); coverage.acknowledge("old-item")
        assertEquals(0L, coverage.editorEpochFor("old-item")); assertTrue(coverage.hasUnacknowledgedCommit)
        coverage.acknowledge("new-item")
        assertEquals(2L, coverage.editorEpochFor("new-item")); assertFalse(coverage.hasUnacknowledgedCommit)
        assertNull(coverage.editorEpochFor("unacknowledged"))
    }
    @Test fun lateFinalsAndCleanupStayInTheOriginalFieldSegment() {
        val ledger = TranscriptLedger()
        ledger.event("old", "old-item", delta = "old provisional", editorEpoch = 0)
        ledger.event("new", "new-item", delta = "new speech", editorEpoch = 2)
        ledger.event("old-final", "old-item", final = "Old final.", editorEpoch = 0)
        val cleanup = mapOf("old-item" to "Old polished.")
        assertEquals("new speech", ledger.displayedFor(cleanup, 2))
        assertEquals("Old polished.", ledger.displayedFor(cleanup, 0))
        assertEquals("Old polished. new speech", ledger.displayed(cleanup))
        assertEquals("Old polished. New speech.", ledger.displayedReplacingSegment(cleanup, 2, "New speech."))
    }
    @Test fun editorFocusLossDetachesOnlyFieldNotRecording() {
        assertEquals(InlineEditorContinuity.Action.DETACH,
            InlineEditorContinuity.action(hasEditor = true, writePending = false, editorValid = false))
        assertEquals(InlineEditorContinuity.Action.KEEP,
            InlineEditorContinuity.action(hasEditor = true, writePending = false, editorValid = true))
    }

    @Test fun noEditorOrInFlightWriteCannotCauseRepeatedDetach() {
        assertEquals(InlineEditorContinuity.Action.KEEP,
            InlineEditorContinuity.action(hasEditor = false, writePending = false, editorValid = false))
        assertEquals(InlineEditorContinuity.Action.KEEP,
            InlineEditorContinuity.action(hasEditor = true, writePending = true, editorValid = false))
    }

    @Test fun alienDefaultsTo75PercentOpaqueAndSliderReflectsActualOpacity() {
        assertEquals(75, BubbleOpacity.DEFAULT_IDLE_PERCENT)
        assertEquals(.75f, BubbleOpacity.alpha(BubbleOpacity.DEFAULT_IDLE_PERCENT), .0001f)
        assertEquals(1f, BubbleOpacity.alpha(100), .0001f)
        assertEquals(.25f, BubbleOpacity.alpha(25), .0001f)
    }

    @Test fun emptyProviderCommitNeverAuthorizesDeletingCapturedLocalAudio() {
        val coverage = AudioCoverage()
        coverage.sent("last-commit")
        assertTrue(coverage.rejectEmpty("last-commit"))
        assertTrue(coverage.uncertain)
        assertFalse(coverage.rejectEmpty("last-commit")) // duplicate cannot decrement twice
        assertTrue(coverage.uncertain)
    }
    @Test fun duplicateCommitAckCannotConsumeTheNextOutstandingCommit() {
        val coverage = AudioCoverage()
        coverage.sent("first"); coverage.acknowledge("item1"); coverage.sent("second")
        coverage.acknowledge("item1")
        assertTrue(coverage.rejectEmpty("second"))
    }
    @Test fun completionReplacesProvisionalAndRejectsDuplicateOrStaleEvents() {
        val ledger = TranscriptLedger()
        assertTrue(ledger.event("d1", "one", delta = "I wan"))
        assertFalse(ledger.event("d1", "one", delta = "I wan"))
        ledger.event("d2", "one", delta = "t it")
        assertEquals("I want it", ledger.text())
        ledger.event("f1", "one", final = "I don't want it.")
        assertEquals("I don't want it.", ledger.text())
        assertFalse(ledger.event("late", "one", delta = "oops"))
        assertFalse(ledger.event("late-final", "one", final = "I want it."))
        assertEquals("I don't want it.", ledger.stable())
    }
    @Test fun predecessorEvidenceOrdersOutOfOrderFinals() {
        val ledger = TranscriptLedger()
        ledger.event("f2", "two", final = "second")
        ledger.event("f1", "one", final = "first")
        ledger.event("commit2", "two", previous = "one")
        assertEquals("first second", ledger.text())
        assertEquals("First. second", ledger.displayed(mapOf("one" to "First.")))
    }
    @Test fun unknownPredecessorIsReorderedWhenItArrives() {
        val ledger = TranscriptLedger()
        ledger.event("2", "two", "one", final = "two")
        ledger.event("1", "one", final = "one")
        assertEquals("one two", ledger.text())
        assertFalse(ledger.pending())
    }
    @Test fun bAllowsOnlyPunctuationCaseAndUnambiguousExplicitCorrection() {
        assertTrue(CorrectionGuard.accepts("i think we can do it tomorrow", "I think we can do it tomorrow.", "B"))
        assertTrue(CorrectionGuard.accepts("i dont want to go", "I don't want to go.", "B"))
        assertTrue(CorrectionGuard.accepts("schedule it for Tuesday sorry Wednesday", "Schedule it for Wednesday.", "B"))
        assertFalse(CorrectionGuard.accepts("he said Tuesday and then he said sorry on Wednesday", "He said Wednesday.", "B"))
        assertFalse(CorrectionGuard.accepts("I'm sorry John", "I'm John.", "B"))
    }
    @Test fun bProtectsNegationProfanityUncertaintyNumbersSignsAndIdentifiers() {
        val bad = listOf(
            "I might not be able to come" to "I can come.",
            "that's fucking ridiculous" to "That's ridiculous.",
            "pay 15 dollars" to "Pay 50 dollars.",
            "temperature -10" to "Temperature 10.",
            "pay $15" to "Pay 15.",
            "we're ready" to "Were ready.",
            "value .5" to "Value 5.",
            "../tmp" to "/tmp", ":w" to "w", "run foo_bar" to "Run fooBar."
        )
        bad.forEach { (a, b) -> assertFalse("$a => $b", CorrectionGuard.accepts(a, b, "B")) }
        assertTrue(CorrectionGuard.accepts("that's fucking ridiculous", "That's fucking ridiculous.", "B"))
        assertTrue(CorrectionGuard.accepts("I might not come", "I might not come.", "B"))
    }
    @Test fun fillersAndRepetitionRemainUnlessExplicitlyEnabled() {
        assertFalse(CorrectionGuard.accepts("um I I think maybe we should wait", "I think maybe we should wait.", "B"))
        assertTrue(CorrectionGuard.accepts("um I I think maybe we should wait", "I think maybe we should wait.", "B", true))
        assertFalse(CorrectionGuard.accepts("I might not come", "I come", "B", true))
    }
    @Test fun cStillProtectsNegationQuantityUncertaintyAndProfanity() {
        assertFalse(CorrectionGuard.accepts("I will not pay $15", "I will pay $50", "C"))
        assertFalse(CorrectionGuard.accepts("Maybe it's fucking wrong", "It's wrong", "C"))
        assertTrue(CorrectionGuard.accepts("I might not finish the task today", "I might not complete the task today", "C"))
        assertFalse(CorrectionGuard.accepts("pay fifteen dollars", "Pay fifty dollars", "C"))
        assertFalse(CorrectionGuard.accepts("John will arrive", "Jane will arrive", "C"))
    }
    @Test fun cleanupChunksRetainLongIdentifiersAndUnicodeExactly() {
        val identifier = "https://example.test/" + "z".repeat(8000)
        assertEquals(listOf(identifier), CleanupChunks.split(identifier))
        val source = "👽 word ".repeat(1200) + "end"
        val chunks = CleanupChunks.split(source)
        assertTrue(chunks.size > 1)
        assertEquals(source, chunks.joinToString(""))
        assertTrue(chunks.dropLast(1).all { it.endsWith(" ") })
    }
    @Test fun cancelDuringFirstCleanupRequestCannotLaunchLaterChunkRequests() = runBlocking {
        val started = CountDownLatch(1); val release = CountDownLatch(1); val calls = AtomicInteger()
        val job = launch(Dispatchers.IO) {
            cleanChunks("word ".repeat(2500), "B", false) { chunk ->
                calls.incrementAndGet(); started.countDown(); check(release.await(5, TimeUnit.SECONDS)); chunk
            }
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS)); job.cancel(); release.countDown(); job.join()
            assertEquals(1, calls.get())
        } finally { release.countDown(); job.cancelAndJoin() }
    }
    @Test fun selectedTextCanBeReplacedWithoutTouchingAnchors() {
        val initial = EditorSnapshot(9, "before selected after", 7, 15)
        val owner = EditorOwnership(initial)
        assertTrue(owner.canFirstWrite(initial))
        assertTrue(owner.confirm(EditorSnapshot(9, "before dictated after", 15, 15), "dictated"))
        assertTrue(owner.matches(EditorSnapshot(9, "before dictated after", 15, 15)))
        assertEquals("selected", owner.originalSelection())
        assertFalse(owner.matches(EditorSnapshot(9, "before dictatedX after", 16, 16)))
    }
    @Test fun uncertainWriteOrFocusChangePermanentlyInvalidatesOwnership() {
        val owner = EditorOwnership(EditorSnapshot(1, "prefix suffix", 7, 7))
        assertFalse(owner.confirm(EditorSnapshot(1, "prefix suffix", 7, 7), "new"))
        assertFalse(owner.certain)
        assertFalse(owner.matches(EditorSnapshot(1, "prefix new suffix", 10, 10)))
        val other = EditorOwnership(EditorSnapshot(2, "", 0, 0))
        assertFalse(other.canFirstWrite(EditorSnapshot(3, "", 0, 0)))
    }
    @Test fun unfinishedKeyboardCompositionCannotBeOwnedOrOverwritten() {
        val snapshot = EditorSnapshot(1, "unfinished", 10, 10, composing = true)
        val owner = EditorOwnership(snapshot)
        assertFalse(owner.certain)
        assertFalse(owner.canFirstWrite(snapshot))
        assertFalse(owner.confirm(EditorSnapshot(1, "dictated", 8, 8), "dictated"))
    }
}
