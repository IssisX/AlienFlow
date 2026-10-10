package io.github.issisx.alienflow

import android.app.Activity
import android.app.Application
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Framework-widget tests, not a physical accessibility-service/microphone integration test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class)
class EditorWidgetTest {
    private fun evidence(edit: EditText): EditorSnapshot {
        val info = edit.createAccessibilityNodeInfo()
        return EditorEvidence.fromNode(4, EditorEvidence.NodeText(info.text?.toString(),
            info.isShowingHintText, info.textSelectionStart, info.textSelectionEnd), false).snapshot!!
    }
    private fun keyboard(connection: InputConnection): EditorEvidence.KeyboardText {
        val text = connection.getSurroundingText(100000, 100000, InputConnection.GET_TEXT_WITH_STYLES)!!
        return EditorEvidence.KeyboardText(text.text.toString(), text.selectionStart, text.selectionEnd, text.offset)
    }
    private fun withEditor(block: (EditText, InputConnection) -> Unit) {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val edit = EditText(controller.get()).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                hint = "Tap here, then tap the alien or Start below"
            }
            controller.get().setContentView(edit)
            edit.requestFocus()
            edit.setSelection(0)
            val connection = edit.onCreateInputConnection(EditorInfo())!!
            block(edit, connection)
        }
    }
    @Test fun androidEmptyHintReproducesOldRefusalAndNowAllowsInsertion() = withEditor { edit, connection ->
        val raw = edit.createAccessibilityNodeInfo()
        val input = keyboard(connection)
        assertTrue(raw.isShowingHintText)
        assertEquals(edit.hint.toString(), raw.text.toString())
        assertEquals("", input.text)
        assertNotEquals(input.text, raw.text.toString())
        val initial = evidence(edit)
        assertNotNull(EditorEvidence.corroborate(initial, input).snapshot)
        val owner = EditorOwnership(initial)
        assertTrue(owner.canFirstWrite(initial))
        assertTrue(connection.commitText("Hello from AlienFlow", 1))
        assertEquals("Hello from AlienFlow", edit.text.toString())
        val after = evidence(edit)
        assertNotNull(EditorEvidence.corroborate(after, keyboard(connection)).snapshot)
        assertTrue(owner.confirm(after, "Hello from AlienFlow"))
    }
    @Test fun androidSelectedTextCanBeReplacedAndRevisedWithoutLosingAnchors() = withEditor { edit, connection ->
        edit.setText("before selected after")
        edit.setSelection(7, 15)
        val initial = evidence(edit)
        assertNotNull(EditorEvidence.corroborate(initial, keyboard(connection)).snapshot)
        val owner = EditorOwnership(initial)
        assertTrue(connection.setSelection(initial.start, initial.end))
        assertTrue(connection.commitText("dictated", 1))
        assertEquals("before dictated after", edit.text.toString())
        assertTrue(owner.confirm(evidence(edit), "dictated"))
        val range = owner.range()
        assertTrue(connection.setSelection(range.first, range.last + 1))
        assertTrue(connection.commitText("Dictated.", 1))
        assertEquals("before Dictated. after", edit.text.toString())
        assertTrue(owner.confirm(evidence(edit), "Dictated."))
    }
    @Test fun androidTextMatchingItsPlaceholderIsNotErased() = withEditor { edit, connection ->
        edit.setText(edit.hint)
        edit.setSelection(edit.length())
        assertFalse(edit.createAccessibilityNodeInfo().isShowingHintText)
        val snapshot = evidence(edit)
        assertEquals(edit.hint.toString(), snapshot.text)
        assertNotNull(EditorEvidence.corroborate(snapshot, keyboard(connection)).snapshot)
    }
}
