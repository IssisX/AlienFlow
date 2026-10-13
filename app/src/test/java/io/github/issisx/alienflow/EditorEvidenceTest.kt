package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

class EditorEvidenceTest {
    @Test fun misreportedHintUsesIndependentlyEmptyEditorNotLabelAnchors() {
        val candidate = EditorSnapshot(7, "Start a note", 1, 1)
        val empty = EditorEvidence.KeyboardText("", 0, 0, 0)
        assertNull(EditorEvidence.corroborate(candidate, empty).snapshot)
        val resolved = EditorEvidence.corroborateWithHint(candidate, "Start a note", empty).snapshot!!
        assertEquals(EditorSnapshot(7, "", 0, 0), resolved)
        assertEquals("spoken words", EditorOwnership(resolved).proposedSnapshot("spoken words").text)
        assertNull(EditorEvidence.corroborateWithHint(candidate, null, empty).snapshot)
        assertNull(EditorEvidence.corroborateWithHint(candidate, "Start a note", empty.copy(offset = 20)).snapshot)
        assertNull(EditorEvidence.corroborateWithHint(candidate, "Start a note", empty.copy(start = 1, end = 1)).snapshot)
    }
    @Test fun typedTextMatchingAHintRemainsUserContent() {
        val candidate = EditorSnapshot(7, "Start a note", 5, 5)
        val keyboard = EditorEvidence.KeyboardText(candidate.text, 5, 5, 0)
        assertEquals(candidate, EditorEvidence.corroborateWithHint(candidate, candidate.text, keyboard).snapshot)
        assertEquals("Start words a note", EditorOwnership(candidate).proposedSnapshot(" words").text)
    }
    private fun node(text: String?, hint: Boolean = false, start: Int = 0, end: Int = start,
                     composing: Boolean = false) = EditorEvidence.fromNode(
        7, EditorEvidence.NodeText(text, hint, start, end), composing)

    @Test fun emptyPlaceholderIsNotUserText() {
        val input = node("Type a message", hint = true)
        assertEquals(EditorSnapshot(7, "", 0, 0), input.snapshot)
        assertEquals(input.snapshot, EditorEvidence.corroborate(input.snapshot!!,
            EditorEvidence.KeyboardText("", 0, 0, 0)).snapshot)
    }
    @Test fun emptyFieldHasOneUnambiguousCursorEvenIfAccessibilityOmitsSelection() {
        assertEquals(EditorSnapshot(7, "", 0, 0), node("Tap here", true, -1, -1).snapshot)
        assertEquals(EditorSnapshot(7, "", 0, 0), node(null, false, -1, -1).snapshot)
    }
    @Test fun zeroLengthCandidateRangeIsNotAComposingWord() {
        assertFalse(EditorEvidence.hasComposingRange(0, 0))
        assertFalse(EditorEvidence.hasComposingRange(-1, -1))
        assertTrue(EditorEvidence.hasComposingRange(0, 1))
        assertTrue(EditorEvidence.hasComposingRange(4, 8))
    }
    @Test fun absenceOfCompositionEvidenceDoesNotBlockAnExistingDraft() {
        val snapshot = node("draft", start = 5, composing = false).snapshot!!
        assertTrue(EditorOwnership(snapshot).certain)
        assertFalse(EditorOwnership(node("draft", start = 5, composing = true).snapshot!!).certain)
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
    @Test fun defaultImePartialWindowNeedsStableAbsoluteSelectionAndExactAnchors() {
        val text = "earlier draft\n".repeat(20) + "left selected right"
        val start = text.indexOf("selected"); val end = start + 8
        val snapshot = node(text, start = start, end = end).snapshot!!
        val window = EditorEvidence.KeyboardText(text.substring(start - 8, end + 6), 8, 16, -1,
            EditorEvidence.Selection(start, end))
        assertEquals(snapshot, EditorEvidence.corroborate(snapshot, window, start until end).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, window.copy(selection = null)).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, window.copy(selection = EditorEvidence.Selection(start + 1, end + 1))).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, window.copy(end = 15)).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, window.copy(text = "X" + window.text.drop(1))).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, window, (start - 9) until end).snapshot)
    }
    @Test fun nonzeroAndInvalidOffsetsAreNotGuessed() {
        val snapshot = node("text", start = 4).snapshot!!
        for (offset in listOf(-2, 1, 200)) {
            assertNull(EditorEvidence.corroborate(snapshot,
                EditorEvidence.KeyboardText("text", 4, 4, offset)).snapshot)
        }
    }
    @Test fun explicitWindowOffsetCoversAMultilineUtf16MutationWithoutReadingTheFullDraft() {
        val text = "older draft\n".repeat(100) + "emoji 🛸 owned words suffix"
        val cursor = text.indexOf(" suffix")
        val snapshot = node(text, start = cursor).snapshot!!
        val offset = cursor - 24
        val window = EditorEvidence.KeyboardText(text.substring(offset, cursor + 7), 24, 24, offset)
        assertEquals(snapshot, EditorEvidence.corroborate(snapshot, window, (cursor - 11) until cursor).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, window.copy(offset = -1)).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, window.copy(text = "X" + window.text.drop(1))).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, window.copy(start = 23)).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, window, (offset - 1) until cursor).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, window.copy(offset = Int.MAX_VALUE)).snapshot)
    }
    @Test fun anEmptyWindowCannotCorroborateANonemptyDraft() {
        val snapshot = node("draft", start = 0).snapshot!!
        assertNull(EditorEvidence.corroborate(snapshot, EditorEvidence.KeyboardText("", 0, 0, 0)).snapshot)
    }
    @Test fun imeOnlySuffixOutsideTheEditDoesNotInvalidateMatchingTextAndCursor() {
        val snapshot = node("abcdefghijklmnopqrstuvwxy", start = 25).snapshot!!
        val keyboard = EditorEvidence.KeyboardText(snapshot.text + "XY", 25, 25, 0)
        assertEquals(snapshot, EditorEvidence.corroborate(snapshot, keyboard).snapshot)
        assertEquals(snapshot, EditorEvidence.corroborate(snapshot,
            EditorEvidence.KeyboardText(snapshot.text.takeLast(8) + "XY", 8, 8, 17), 20 until 25).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, keyboard, 25 until 27).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, keyboard.copy(offset = -1)).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, keyboard.copy(start = 27, end = 27)).snapshot)
        assertNull(EditorEvidence.corroborate(snapshot, keyboard.copy(text = "X" + keyboard.text.drop(1))).snapshot)
        assertNull(EditorEvidence.corroborate(EditorSnapshot(7, "", 0, 0),
            EditorEvidence.KeyboardText("XY", 0, 0, 0)).snapshot)
    }
    @Test fun aChangedTailPreservesTheConfirmedPrefixAndNeverSplitsASurrogatePair() {
        val initial = EditorSnapshot(7, "left  right", 5, 5)
        val owner = EditorOwnership(initial)
        assertTrue(owner.confirm(owner.proposedSnapshot("hello 🛸"), "hello 🛸"))
        assertEquals(EditorReplacement(13 until 13, " tail"), owner.replacement("hello 🛸 tail", false))
        assertEquals(EditorReplacement(11 until 13, "🚀"), owner.replacement("hello 🚀", false))
        assertEquals(EditorReplacement(5 until 13, "Hello 🛸"), owner.replacement("Hello 🛸", false))
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
