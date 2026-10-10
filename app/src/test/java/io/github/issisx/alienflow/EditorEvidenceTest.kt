package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

class EditorEvidenceTest {
    private fun node(text: String?, hint: Boolean = false, start: Int = 0, end: Int = start,
                     known: Boolean = true, composing: Boolean = false) = EditorEvidence.fromNode(
        7, EditorEvidence.NodeText(text, hint, start, end), known, composing)

    @Test fun emptyPlaceholderIsNotUserText() {
        val input = node("Type a message", hint = true, known = false)
        assertEquals(EditorSnapshot(7, "", 0, 0), input.snapshot)
        assertEquals(input.snapshot, EditorEvidence.corroborate(input.snapshot!!,
            EditorEvidence.KeyboardText("", 0, 0, 0)).snapshot)
    }
    @Test fun emptyFieldHasOneUnambiguousCursorEvenIfAccessibilityOmitsSelection() {
        assertEquals(EditorSnapshot(7, "", 0, 0), node("Tap here", true, -1, -1, false).snapshot)
        assertEquals(EditorSnapshot(7, "", 0, 0), node(null, false, -1, -1, false).snapshot)
    }
    @Test fun realTextEqualToAHintIsPreserved() {
        assertEquals("Type a message", node("Type a message", false, 14).snapshot!!.text)
    }
    @Test fun unknownOffsetRequiresExactFullContentsAndCursor() {
        val snapshot = node("prefix text", false, 7).snapshot!!
        assertEquals(snapshot, EditorEvidence.corroborate(snapshot,
            EditorEvidence.KeyboardText("prefix text", 7, 7, -1)).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot,
            EditorEvidence.KeyboardText("text", 0, 0, -1)).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot,
            EditorEvidence.KeyboardText("prefix text", 8, 8, -1)).snapshot)
    }
    @Test fun nonzeroAndInvalidOffsetsAreNotGuessed() {
        val snapshot = node("text", start = 4).snapshot!!
        for (offset in listOf(-2, 1, 200)) {
            assertNull(EditorEvidence.corroborate(snapshot,
                EditorEvidence.KeyboardText("text", 4, 4, offset)).snapshot)
        }
    }
    @Test fun staleHintCannotAuthorizeReplacingNonemptyInput() {
        val snapshot = node("Type here", true).snapshot!!
        assertNull(EditorEvidence.corroborate(snapshot,
            EditorEvidence.KeyboardText("My existing draft", 0, 0, 0)).snapshot)
    }
    @Test fun nonemptyUnknownSelectionAndOutOfBoundsCursorsAreRejected() {
        assertNull(node("draft", start = -1, end = -1).snapshot)
        assertNull(node("draft", start = 6).snapshot)
        assertNull(node("", start = 1).snapshot)
    }
    @Test fun reverseSelectionHasTheSameReplacementRange() {
        val snapshot = node("before selected after", start = 15, end = 7).snapshot!!
        assertEquals(7, snapshot.start)
        assertEquals(15, snapshot.end)
        assertEquals(snapshot, EditorEvidence.corroborate(snapshot,
            EditorEvidence.KeyboardText(snapshot.text, 7, 15, 0)).snapshot)
        val owner = EditorOwnership(snapshot)
        assertTrue(owner.canFirstWrite(snapshot))
        assertTrue(owner.confirm(EditorSnapshot(7, "before new after", 10, 10), "new"))
        assertEquals("selected", owner.originalSelection())
    }
    @Test fun nonemptyUnknownOrActiveCompositionStillPreventsOwnership() {
        assertFalse(EditorOwnership(node("draft", start = 5, known = false).snapshot!!).certain)
        assertFalse(EditorOwnership(node("draft", start = 5, composing = true).snapshot!!).certain)
    }
    @Test fun matchingTextNeverAuthorizesAnInvalidOrDifferentSelection() {
        val snapshot = node("draft", start = 5).snapshot!!
        for ((a, b) in listOf(-1 to -1, 6 to 6, 0 to 5)) {
            assertNull(EditorEvidence.corroborate(snapshot,
                EditorEvidence.KeyboardText("draft", a, b, 0)).snapshot)
        }
    }
    @Test fun firstInsertionAndCorrectionPreserveExternalAnchors() {
        val initial = node("left  right", start = 5).snapshot!!
        val owner = EditorOwnership(initial)
        assertTrue(owner.canFirstWrite(initial))
        val first = node("left hello right", start = 10).snapshot!!
        assertNotNull(EditorEvidence.corroborate(first,
            EditorEvidence.KeyboardText(first.text, 10, 10, -1)).snapshot)
        assertTrue(owner.confirm(first, "hello"))
        val corrected = node("left Hello. right", start = 11).snapshot!!
        assertTrue(owner.confirm(corrected, "Hello."))
        assertFalse(owner.matches(node("left My manual edit right", start = 19).snapshot!!))
    }
}
