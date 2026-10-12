package io.github.issisx.alienflow

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorSynchronizationTest {
    private val before = EditorSnapshot(1, "draft ", 6, 6)
    private val expected = EditorSnapshot(1, "draft words", 11, 11)
    private fun reading(node: EditorSnapshot, ime: EditorSnapshot = node, composing: Boolean = false) =
        EditorSynchronization.Reading(node, EditorEvidence.KeyboardText(ime.text, ime.start, ime.end, 0), composing)

    @Test fun delayedBrowserNodeDoesNotPermanentlyInvalidateARealInsertion() = runTest {
        // The old one-shot confirmation invalidates the owner at this actual stale state.
        val oldOwner = EditorOwnership(before)
        assertFalse(oldOwner.confirm(before, "words"))
        assertFalse(oldOwner.certain)
        val owner = EditorOwnership(before)
        val result = EditorSynchronization.await(before, owner.proposedSnapshot("words"), { true }, {
            reading(if (currentTime < 300) before else expected, expected)
        })
        assertEquals(EditorWriteResult.APPLIED, result)
        assertEquals(300L, currentTime)
        assertTrue(owner.confirm(expected, "words"))
        assertTrue(owner.certain)
    }
    @Test fun genuineManualEditIsPreservedRatherThanRetriedAsAnInsertion() = runTest {
        val edited = EditorSnapshot(1, "draft MY words", 9, 9)
        assertEquals(EditorWriteResult.USER_EDIT,
            EditorSynchronization.await(before, expected, { true }, { reading(edited) }))
        assertEquals(0L, currentTime)
    }
    @Test fun keyboardCompositionAndTargetSwitchStopFurtherWritesQuietly() = runTest {
        assertEquals(EditorWriteResult.USER_EDIT,
            EditorSynchronization.await(before, expected, { true }, { reading(expected, composing = true) }))
        var reads = 0
        assertEquals(EditorWriteResult.USER_EDIT,
            EditorSynchronization.await(before, expected, { false }, { reads++; reading(expected) }))
        assertEquals(0, reads)
    }
    @Test fun persistentCrossSourceDisagreementDoesNotAuthorizeMutation() = runTest {
        assertEquals(EditorWriteResult.FAILED,
            EditorSynchronization.await(before, expected, { true }, { reading(before, expected) }, attempts = 3))
        assertEquals(100L, currentTime)
    }
    @Test fun existingDraftMustAgreeBeforeFirstMutation() = runTest {
        assertEquals(EditorWriteResult.APPLIED,
            EditorSynchronization.await(before, before, { true }, { reading(before) }))
        assertEquals(EditorWriteResult.USER_EDIT,
            EditorSynchronization.await(before, before, { true }, { reading(expected) }))
    }
    @Test fun finalAudioCommitAndWaveCloseWaitForTheLastCaptureCallback() = runTest {
        val release = CompletableDeferred<Unit>(); val order = mutableListOf<String>()
        val capture = launch { release.await(); order += "last-frame" }
        val finalize = launch { CaptureDrain.finish(capture, { order += "commit" }, { order += "close" }) }
        runCurrent(); advanceTimeBy(300); runCurrent()
        assertTrue(order.isEmpty()) // A 250ms timer would already have finalized the wrong state.
        release.complete(Unit); advanceUntilIdle(); finalize.join()
        assertEquals(listOf("last-frame", "commit", "close"), order)
    }
    @Test fun bubbleShowsOnlyUsefulFailuresAndCanBeFullyDisabled() {
        for (status in listOf("Starting microphone and realtime connection…", "Listening • tap the alien to stop",
            "Finishing captured speech…", "Cleanup unavailable; recognized words preserved",
            "Inline dictation paused for your edit; recording continues", "Text saved in AlienFlow • your field edits were preserved",
            "[AF-E13] The text or cursor changed; automatic edits stopped"))
            assertNull(status, CompletionFeedback.bubble(status))
        for (status in listOf("[AF-E10] Editor sync timed out; see Diagnostics", "[AF-E14] Insertion sync timed out; see Diagnostics",
            "[AF-N02] Audio commit rejected; recording retained. See Diagnostics")) {
            assertEquals(status, CompletionFeedback.bubble(status))
            assertNull(CompletionFeedback.bubble(status, diagnosticErrors = false))
        }
    }
    @Test fun rejectedCommitRetainsItsTrueByteCountAndCannotDecrementTwice() {
        val coverage = AudioCoverage(); coverage.sent("commit", 960L)
        assertEquals(960L, coverage.bytesFor("commit"))
        assertTrue(coverage.rejectEmpty("commit")); assertTrue(coverage.uncertain)
        assertNull(coverage.bytesFor("commit")); assertFalse(coverage.rejectEmpty("commit"))
    }
}
