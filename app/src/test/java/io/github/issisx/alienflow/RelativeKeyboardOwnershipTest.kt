package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

class RelativeKeyboardOwnershipTest {
    private fun read(text: String, start: Int = text.length, end: Int = start) =
        EditorEvidence.KeyboardText(text, start, end, -1, EditorEvidence.Selection(0, 0))
    @Test fun staleAbsoluteCursorDoesNotOverrideActualLocalTextAndCursor() {
        val owner = RelativeKeyboardOwnership(read(""), 1)
        val edit = owner.edit(read(""), "hi")!!
        assertEquals(0, edit.deleteBefore)
        assertTrue(owner.confirm(read("hi"), "hi", edit.confirmationLength, false))
        val revised = owner.edit(read("hi"), "Hi.")!!
        assertEquals(2, revised.deleteBefore)
        assertTrue(owner.confirm(read("Hi."), "Hi.", revised.confirmationLength, false))
    }
    @Test fun selectedReplacementAndCancelKeepBothDraftAnchors() {
        val owner = RelativeKeyboardOwnership(read("left selected right", 5, 13), 2)
        val edit = owner.edit(read("left selected right", 5, 13), "hello")!!
        assertEquals(0, edit.deleteBefore); assertEquals("hello", edit.text)
        assertTrue(owner.confirm(read("left hello right", 10), "hello", 5, false))
        val cancel = owner.edit(read("left hello right", 10), owner.originalSelection())!!
        assertEquals(5, cancel.deleteBefore); assertEquals("selected", cancel.text)
    }
    @Test fun selectedUserTextIsPreservedWhenReanchoringAfterAnInteraction() {
        val owner = RelativeKeyboardOwnership(read("left selected right", 5, 13), 3, false)
        val edit = owner.edit(read("left selected right", 5, 13), " speech")!!
        assertEquals(0, edit.deleteBefore); assertEquals("selected speech", edit.text)
        assertTrue(owner.confirm(read("left selected speech right", 20), " speech", 15, false))
    }
    @Test fun boundedWindowCanAppendButCannotRewriteUnseenOwnedSpeech() {
        val owner = RelativeKeyboardOwnership(read("draft "), 4)
        val words = "hello ".repeat(100)
        assertTrue(owner.confirm(read("draft $words"), words, words.length, false))
        val short = read(words.takeLast(24))
        assertNotNull(owner.edit(short, words + "new"))
        assertNull(owner.edit(short, "different"))
        assertNull(owner.edit(short, ""))
    }
    @Test fun wrongTextChangedSelectionAndIncompleteConfirmationCannotPass() {
        val owner = RelativeKeyboardOwnership(read("draft "), 5)
        assertFalse(owner.confirm(read("draft XX"), "hi", 2, false))
        assertFalse(owner.confirm(read("draft hi", 7), "hi", 2, false))
        assertFalse(owner.confirm(read("i"), "hi", 2, false))
        assertFalse(owner.confirm(read("draft hi"), "hi", 2, true))
        assertFalse(RelativeKeyboardOwnership.usable(read("x", -1)))
    }
}
