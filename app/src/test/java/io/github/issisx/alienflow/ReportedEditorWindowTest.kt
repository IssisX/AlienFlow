package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

/** Replays reported metadata. Placeholder/newline characters are explicit fixtures,
 * not a claim to know the user's private field contents. Real browser IPC is separate. */
class ReportedEditorWindowTest {
    @Test fun reportedTwelveToOneKnownOffsetMismatchUsesKeyboardCoordinates() {
        val presentation = EditorSnapshot(9, "placeholder\n", 11, 11)
        val keyboard = EditorEvidence.KeyboardText("\n", 0, 0, 0, EditorEvidence.Selection(0, 0))
        assertEquals(12, presentation.text.length)
        assertTrue(EditorEvidence.corroborate(presentation, keyboard).error!!.startsWith("[AF-E09]"))
        val initial = KeyboardWindow.read(keyboard, false)!!
        val owner = KeyboardEditorOwnership(initial, presentation.generation)
        assertEquals(0 until 0, owner.replacement("spoken").range)
        assertTrue(owner.matches(initial))
        assertTrue(owner.covers(initial, owner.replacement("spoken").range))
        assertTrue(owner.confirm(KeyboardWindow("spoken\n", 6, 6, 0), "spoken"))
        assertTrue(owner.confirm(KeyboardWindow("Spoken.\n", 7, 7, 0), "Spoken."))
    }

    @Test fun unknownOffsetUsesStableKeyboardSelectionNotAccessibilityCursor() {
        val raw = EditorEvidence.KeyboardText("\n", 0, 0, -1, EditorEvidence.Selection(0, 0))
        val actual = KeyboardWindow.read(raw, false)!!
        assertEquals(0, actual.absoluteStart)
        assertEquals(0, actual.absoluteEnd)
        assertNull(KeyboardWindow.read(raw.copy(selection = null), false))
    }

    @Test fun malformedOffsetsOrUncoveredWritesStayRejected() {
        val raw = EditorEvidence.KeyboardText("\n", 0, 0, -2, EditorEvidence.Selection(0, 0))
        assertNull(KeyboardWindow.read(raw, false))
        assertNull(KeyboardWindow.read(raw.copy(offset = Int.MAX_VALUE), false))
        val owner = KeyboardEditorOwnership(KeyboardWindow("draft spoken", 12, 12, 0), 10)
        val partial = KeyboardWindow("spoken", 6, 6, 6)
        assertFalse(owner.covers(partial, 0 until 12))
        assertFalse(owner.confirm(partial, "unseen replacement"))
    }
}
