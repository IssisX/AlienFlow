package io.github.issisx.alienflow

import android.app.Activity
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real EditText accessibility actions, with no accessibility-IME connection attached. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NodeEditorWriterTest {
    private suspend fun writeFresh(before: EditorSnapshot, expected: EditorSnapshot, emptySession: Boolean,
                                  current: () -> Boolean, read: () -> EditorSnapshot?,
                                  setText: (String) -> Boolean) = NodeEditorWriter.writeMerged(before, expected,
        EditorOwnership(before), true, emptySession, current, read, setText, null, {})

    @Test fun nativeAccessibilityRouteInsertsAndRevisesWithoutAnyTerminalWarmup() = runTest {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val field = EditText(controller.get()).apply { setText(""); setSelection(0) }
            controller.get().setContentView(field); field.requestFocus()
            fun read(): EditorSnapshot? {
                val node = field.createAccessibilityNodeInfo()
                return EditorEvidence.fromNode(3, EditorEvidence.NodeText(node.text?.toString(), node.isShowingHintText, node.textSelectionStart, node.textSelectionEnd), false).snapshot
            }
            val owner = EditorOwnership(read()!!)
            val actions = field.createAccessibilityNodeInfo().actionList.map { it.id }
            assertTrue(AccessibilityNodeInfo.ACTION_SET_TEXT in actions)
            assertFalse("Empty fields do not advertise a selection action", AccessibilityNodeInfo.ACTION_SET_SELECTION in actions)
            suspend fun insert(value: String): EditorWriteResult {
                val before = read()!!
                val outcome = NodeEditorWriter.writeMerged(before, owner.proposedSnapshot(value), owner, owner.owned.isEmpty(), true, { true }, ::read,
                    { text -> field.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }) }, null, {})
                if (outcome == EditorWriteResult.APPLIED) assertTrue(owner.confirm(read()!!, value))
                return outcome
            }
            assertEquals(EditorWriteResult.APPLIED, insert("hello"))
            assertEquals("hello", field.text.toString()); assertEquals(5, field.selectionStart)
            assertEquals(EditorWriteResult.APPLIED, insert("Hello."))
            assertEquals("Hello.", field.text.toString()); assertEquals(6, field.selectionStart)
        }
    }
    @Test fun delayedPublicationCommitsOnceAndManualChangesOrFocusLossNeverRetry() = runTest {
        val before = EditorSnapshot(1, "", 0, 0)
        val expected = EditorSnapshot(1, "words", 5, 5)
        var snapshot = before; var reads = 0; var commits = 0
        val result = writeFresh(before, expected, true, { true }, { reads++; if (reads > 5) snapshot = expected; snapshot },
            { commits++; true })
        assertEquals(EditorWriteResult.APPLIED, result); assertEquals(1, commits)
        var current = true
        assertEquals(EditorWriteResult.USER_EDIT, writeFresh(before, expected, true, { current }, { before }, { current = false; true }))
        snapshot = before
        assertEquals(EditorWriteResult.RETRY, writeFresh(before, expected, true, { true }, { snapshot },
            { snapshot = EditorSnapshot(1, "my manual words", 3, 3); true }))
    }
    @Test fun rejectedAndUnconfirmedMutationsDoNotFallThroughToAnotherWriter() = runTest {
        val before = EditorSnapshot(1, "", 0, 0); val expected = EditorSnapshot(1, "words", 5, 5)
        var commits = 0
        assertEquals(EditorWriteResult.FAILED, writeFresh(before, expected, true, { true }, { before }, { commits++; false }))
        assertEquals(1, commits)
        commits = 0
        assertEquals(EditorWriteResult.FAILED, writeFresh(before, expected, true, { true }, { before }, { commits++; true }))
        assertEquals(1, commits)
    }
    @Test fun existingExternalTextNeverUsesTheWholeFieldAction() = runTest {
        val before = EditorSnapshot(1, "left selected right", 5, 13)
        val owner = EditorOwnership(before)
        assertFalse(owner.ownsWholeField())
        assertEquals(EditorWriteResult.FAILED, writeFresh(before, owner.proposedSnapshot("words"),
            owner.ownsWholeField(), { true }, { before }, { error("Must not rebuild an unrelated field") }))
    }
}
