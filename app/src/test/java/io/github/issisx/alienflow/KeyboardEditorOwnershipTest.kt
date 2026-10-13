package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

class KeyboardEditorOwnershipTest {
    private fun window(text: String, cursor: Int, offset: Int = 0) = KeyboardWindow(text, cursor, cursor, offset)
    @Test fun emptyRichEditorUsesKeyboardNewlineAndCursorWithoutPlaceholderPresentation() {
        val raw = EditorEvidence.KeyboardText("\n", 0, 0, -1, EditorEvidence.Selection(0, 0))
        val initial = KeyboardWindow.read(raw, false)!!
        val owner = KeyboardEditorOwnership(initial, 1)
        assertEquals(0 until 0, owner.replacement("spoken").range)
        assertTrue(owner.confirm(window("spoken\n", 6), "spoken"))
        assertTrue(owner.confirm(window("Spoken.\n", 7), "Spoken."))
    }
    @Test fun boundedWindowsHaveIndependentAbsoluteCoordinatesAndPreserveSelectedTextForCancel() {
        val initial = KeyboardWindow("left selected right", 5, 13, 200)
        val owner = KeyboardEditorOwnership(initial, 2)
        assertEquals(205 until 213, owner.replacement("hello").range)
        assertTrue(owner.confirm(window("left hello right", 10, 200), "hello"))
        assertEquals("selected", owner.originalSelection())
        assertEquals(205 until 210, owner.replacement("selected").range)
    }
    @Test fun appendDoesNotRequireReadingAnUnchangedLongSpeechPrefix() {
        val owner = KeyboardEditorOwnership(window("draft ", 6), 3)
        val words = "hello ".repeat(100)
        assertTrue(owner.confirm(window("draft $words", 6 + words.length), words))
        val bounded = window(words.takeLast(32), 32, 6 + words.length - 32)
        assertTrue(owner.matches(bounded))
        assertTrue(owner.covers(bounded, owner.replacement(words + "new").range))
        assertTrue(owner.confirm(window(words.takeLast(29) + "new", 32, 6 + words.length - 29), words + "new"))
    }
    @Test fun unseenSpeechCannotBeRewrittenOrCancelled() {
        val owner = KeyboardEditorOwnership(window("draft ", 6), 4)
        val words = "hello ".repeat(100)
        assertTrue(owner.confirm(window("draft $words", 606), words))
        val short = window(words.takeLast(32), 32, 574)
        assertTrue(owner.matches(short))
        assertFalse(owner.covers(short, owner.replacement("changed").range))
        assertFalse(owner.covers(short, owner.replacement("").range))
    }
    @Test fun knownOffsetDoesNotDependOnStaleSelectionCallbacks() {
        val read = KeyboardWindow.read(EditorEvidence.KeyboardText("text", 4, 4, 100,
            EditorEvidence.Selection(0, 0)), false)!!
        assertEquals(104, read.absoluteEnd)
    }
    @Test fun defaultOffsetNeedsAnUnambiguousStableKeyboardSelection() {
        assertNull(KeyboardWindow.read(EditorEvidence.KeyboardText("text", 4, 4, -1), false))
        assertNull(KeyboardWindow.read(EditorEvidence.KeyboardText("text", 4, 4, -1,
            EditorEvidence.Selection(104, 105)), false))
        assertEquals(100, KeyboardWindow.read(EditorEvidence.KeyboardText("text", 4, 4, -1,
            EditorEvidence.Selection(104, 104)), false)!!.offset)
    }
    @Test fun changedOwnedTextOrCursorCannotBeConfirmed() {
        val owner = KeyboardEditorOwnership(window("draft ", 6), 5)
        assertFalse(owner.confirm(window("draft hello", 10), "hello"))
        assertFalse(owner.confirm(window("draft hullo", 11), "hello"))
        assertTrue(owner.confirm(window("draft hello", 11), "hello"))
        assertFalse(owner.matches(window("draft Cory", 10)))
    }
}
