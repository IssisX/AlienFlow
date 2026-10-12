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
    @Test fun nativeAccessibilityRouteInsertsAndRevisesWithoutAnyTerminalWarmup() = runTest {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val field = EditText(controller.get()).apply { setText("left selected right"); setSelection(5, 13) }
            controller.get().setContentView(field); field.requestFocus()
            fun read(): EditorSnapshot? {
                val node = field.createAccessibilityNodeInfo()
                return EditorEvidence.fromNode(3, EditorEvidence.NodeText(node.text?.toString(), node.isShowingHintText, node.textSelectionStart, node.textSelectionEnd), false).snapshot
            }
            val owner = EditorOwnership(read()!!)
            val actions = field.createAccessibilityNodeInfo().actionList.map { it.id }
            assertTrue(AccessibilityNodeInfo.ACTION_SET_TEXT in actions)
            assertTrue(AccessibilityNodeInfo.ACTION_SET_SELECTION in actions)
            suspend fun insert(value: String): EditorWriteResult {
                val before = read()!!
                val outcome = NodeEditorWriter.write(before, owner.proposedSnapshot(value), { true }, ::read,
                    { text -> field.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }) },
                    { cursor -> field.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply { putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor); putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor) }) })
                if (outcome == EditorWriteResult.APPLIED) assertTrue(owner.confirm(read()!!, value))
                return outcome
            }
            assertEquals(EditorWriteResult.APPLIED, insert("hello"))
            assertEquals("left hello right", field.text.toString()); assertEquals(10, field.selectionStart)
            assertEquals(EditorWriteResult.APPLIED, insert("Hello."))
            assertEquals("left Hello. right", field.text.toString()); assertEquals(11, field.selectionStart)
        }
    }
    @Test fun delayedPublicationCommitsOnceAndManualChangesOrFocusLossNeverRetry() = runTest {
        val before = EditorSnapshot(1, "draft ", 6, 6)
        val expected = EditorSnapshot(1, "draft words", 11, 11)
        var snapshot = before; var reads = 0; var commits = 0
        val result = NodeEditorWriter.write(before, expected, { true }, { reads++; if (reads > 5) snapshot = expected; snapshot },
            { commits++; true }, { error("No selection mutation needed") })
        assertEquals(EditorWriteResult.APPLIED, result); assertEquals(1, commits)
        var current = true
        assertEquals(EditorWriteResult.USER_EDIT, NodeEditorWriter.write(before, expected, { current }, { before }, { current = false; true }, { error("Changed target") }))
        snapshot = before
        assertEquals(EditorWriteResult.USER_EDIT, NodeEditorWriter.write(before, expected, { true }, { snapshot },
            { snapshot = EditorSnapshot(1, "my manual words", 3, 3); true }, { error("User edit") }))
    }
    @Test fun rejectedAndUnconfirmedMutationsDoNotFallThroughToAnotherWriter() = runTest {
        val before = EditorSnapshot(1, "", 0, 0); val expected = EditorSnapshot(1, "words", 5, 5)
        var commits = 0
        assertEquals(EditorWriteResult.FAILED, NodeEditorWriter.write(before, expected, { true }, { before }, { commits++; false }, { error("Rejected text") }))
        assertEquals(1, commits)
        commits = 0
        assertEquals(EditorWriteResult.FAILED, NodeEditorWriter.write(before, expected, { true }, { before }, { commits++; true }, { error("Unconfirmed text") }))
        assertEquals(1, commits)
    }
}
