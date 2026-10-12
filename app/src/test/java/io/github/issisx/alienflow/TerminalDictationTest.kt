package io.github.issisx.alienflow

import android.app.Activity
import android.app.Application
import android.text.InputType
import android.view.View
import android.view.inputmethod.BaseInputConnection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Framework input-connection fixture reproduces Termux's consume-and-clear semantics.
 * This proves the adapter contract, not installed Termux or physical-device acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TerminalDictationTest {
    @Test fun defaultTermuxViewIsAvailableEvenThoughItIsNotAnEditableDocument() {
        assertTrue(TerminalDictation.supported("android.view.View", "com.termux", "com.termux", InputType.TYPE_NULL))
        assertTrue(TerminalDictation.supported("android.view.View", "com.termux", "com.termux",
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
    }
    @Test fun unrelatedViewsAndWrongKeyboardTargetsStayExcluded() {
        assertFalse(TerminalDictation.supported("android.view.View", "com.example.app", "com.example.app", 0))
        assertFalse(TerminalDictation.supported("android.view.View", "com.termux", "com.other.app", 0))
        assertFalse(TerminalDictation.supported("android.widget.EditText", "com.termux", "com.termux", 0))
        assertFalse(TerminalDictation.supported("android.view.View", "com.termux", "com.termux", InputType.TYPE_CLASS_TEXT))
    }
    @Test fun terminalCommitPreservesExistingInputAndNeverSendsControlActions() {
        Robolectric.buildActivity(Activity::class.java).setup().use { activity ->
            val terminal = StringBuilder("existing draft ")
            val connection = object : BaseInputConnection(View(activity.get()), true) {
                override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                    super.commitText(text, newCursorPosition)
                    val content = checkNotNull(editable)
                    terminal.append(content); content.clear(); return true
                }
            }
            val insertion = TerminalInsertion()
            assertTrue(insertion.send("Cory's café 😀\nkeep $42\r\t\u001b\u0003", true) {
                connection.commitText(it, 1)
            })
            assertEquals("existing draft Cory's café 😀 keep $42    ", terminal.toString())
            assertEquals("", connection.editable.toString())
            assertFalse(terminal.any { it.isISOControl() })
            assertFalse(insertion.send("duplicate", true) { error("Must not retry") })
        }
    }
    @Test fun focusLossAndBlankResultsDoNotWrite() {
        val insertion = TerminalInsertion()
        assertFalse(insertion.send("words", false) { error("Stale target") })
        assertFalse(insertion.send("", true) { error("Empty input") })
        assertFalse(insertion.sent)
    }
    @Test fun uncertainCommitIsNeverRetriedAutomatically() {
        val insertion = TerminalInsertion()
        try { insertion.send("words", true) { throw IllegalStateException("fixture") }; fail() }
        catch (_: IllegalStateException) { }
        assertTrue(insertion.sent)
        assertFalse(insertion.send("words", true) { error("Duplicate terminal input") })
    }
    @Test fun completedInlineTextDoesNotAcquireAFailureBannerAfterFocusDetaches() {
        assertTrue(CompletionFeedback.inlineDelivered("Final words.", "Final words."))
        assertFalse(CompletionFeedback.inlineDelivered("Final words.", "Final"))
        assertFalse(CompletionFeedback.inlineDelivered("", ""))
        assertNull(CompletionFeedback.bubble("Complete • ready for another dictation"))
        assertNull(CompletionFeedback.bubble("Sent to Termux • press Enter yourself"))
        assertEquals("Text saved in AlienFlow", CompletionFeedback.bubble("Result saved • inline editing unavailable; copy the transcript"))
        assertEquals("[AF-N01] Live connection failed", CompletionFeedback.bubble("[AF-N01] Live connection failed"))
    }
}
