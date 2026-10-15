package io.github.issisx.alienflow

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ContextualWritingTest {
    @Test fun contextIsBoundedReadOnlyDataAndNeverTrustedInstructions() {
        val context = WritingContext("x".repeat(900) + "I think ", " today" + "y".repeat(900))
        val body = CleanupRequest.body("That Works", "B", false, context)
        val input = JSONObject(body.getString("input"))
        assertEquals(512, input.getString("before").length)
        assertEquals(256, input.getString("after").length)
        assertEquals("That Works", input.getString("dictation"))
        assertFalse(body.getString("instructions").contains("I think"))
        assertFalse(CorrectionGuard.accepts("That Works", "I think that works today", "B"))
    }
    @Test fun memoCannotReuseCapitalizationAcrossDifferentInsertionContexts() = runBlocking {
        val memo = CleanupMemo(); var requests = 0
        suspend fun clean(before: String) = memo.cleanWithContext("That Works", "B", false, WritingContext(before)) { _, context ->
            requests++; if (context.before.isEmpty()) "That works." else "that works"
        }
        assertEquals("That works.", clean("")); assertEquals("that works", clean("I think "))
        assertEquals("that works", clean("I think ")); assertEquals(2, requests)
    }
    @Test fun connectedFinalsPolishTogetherWithoutHidingNewSpeechOrCrossingFields() {
        val ledger = TranscriptLedger()
        ledger.event("1", "a", final = "I Think.", editorEpoch = 0)
        ledger.event("2", "b", previous = "a", final = "That Works.", editorEpoch = 0)
        assertEquals("I Think. That Works.", ledger.stableFor(0))
        assertTrue(ledger.polish(0, ledger.stableFor(0), "I think that works."))
        ledger.event("3", "c", previous = "b", delta = "And", editorEpoch = 0)
        assertEquals("I think that works. And", ledger.displayedFor(emptyMap(), 0))
        ledger.event("4", "d", previous = "c", final = "New field", editorEpoch = 2)
        assertEquals("New field", ledger.displayedFor(emptyMap(), 2))
        assertFalse(ledger.polish(2, "I Think. That Works.", "wrong field"))
        ledger.event("5", "c", final = "And another thought.", editorEpoch = 0)
        assertFalse(ledger.polish(0, "I Think. That Works.", "stale"))
        assertEquals("I think that works. And another thought. New field", ledger.displayed(emptyMap()))
        assertEquals("I Think. That Works. And another thought. New field", ledger.stable())
    }
    @Test fun missingEarlierFinalPreventsOutOfOrderCleanupAndAllWordsSurvive() {
        val ledger = TranscriptLedger()
        ledger.event("1", "a", delta = "I think", editorEpoch = 0)
        ledger.event("2", "b", previous = "a", final = "that works", editorEpoch = 0)
        assertEquals("", ledger.stableFor(0))
        ledger.event("3", "a", final = "I think", editorEpoch = 0)
        assertEquals("I think that works", ledger.stableFor(0))
    }
    @Test fun relativeAndAbsoluteOwnersShareAuthoritativeInsertionContext() {
        val raw = EditorEvidence.KeyboardText("I think today", 8, 8, -1)
        val relative = RelativeKeyboardOwnership(raw, 1)
        val absolute = KeyboardEditorOwnership(KeyboardWindow(raw.text, 8, 8, 20), 1)
        assertEquals(WritingContext("I think ", "today"), relative.writingContext)
        assertEquals(relative.writingContext, absolute.writingContext)
        assertEquals("that works ", relative.separateFromTypedWord("that works"))
    }
}
