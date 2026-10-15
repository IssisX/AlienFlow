package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Activity
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.SurroundingText
import android.widget.EditText
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

/** Keyboard session changes racing a write. Separate from EditorBridgeNodeRouteTest, which is
 * replayed on shipped source by scripts/prove_app_switch_regression.py. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class, shadows = [EditorBridgeNodeRouteTest.Broker::class,
    EditorBridgeNodeRouteTest.NodeIpc::class, EditorBridgeNodeRouteTest.Ime::class, EditorBridgeNodeRouteTest.KeyboardIpc::class])
class KeyboardSessionRestartTest {
    private val harness = EditorBridgeNodeRouteTest()
    @Before fun reset() = harness.reset()

    @Test fun aReadFromASupersededKeyboardSessionIsRetriedNotReportedAsAFailure() =
        harness.withField("draft ", keyboard = true) { service ->
            assertTrue(service.bridge.begin()); harness.activate(service)
            // The keyboard restarts this same field while the first read is in flight; the
            // superseded session answers nothing (observed as AF-E09 on Android 13 and 16 emulators).
            EditorBridgeNodeRouteTest.interceptRead = {
                EditorBridgeNodeRouteTest.inputMethod!!.onStartInput(EditorBridgeNodeRouteTest.info!!, true); null
            }
            assertEquals(EditorWriteResult.RETRY, harness.write(service, "spoken"))
            assertEquals("", service.bridge.lastError)
            assertEquals("draft ", EditorBridgeNodeRouteTest.field!!.text.toString())
            assertEquals(EditorWriteResult.APPLIED, harness.write(service, "spoken"))
            assertEquals("draft spoken", EditorBridgeNodeRouteTest.field!!.text.toString())
        }

    @Test fun switchingAppsWhileAWriteIsConfirmedStillLetsNewSpeechFollowTheNewField() =
        harness.withField("", keyboard = true) { service ->
            val app = service.application as AlienApp
            app.store.prefs.edit().putString("correction", "A").commit()
            val coordinator = DictationCoordinator(app, app.store, keyProvider = { "fixture-not-a-key" })
            ReflectionHelpers.setField(app, "coordinator", coordinator); coordinator.service = service
            shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
            coordinator.start(); assertTrue(coordinator.active)
            val first = EditorBridgeNodeRouteTest.field!!
            fun moveTo(next: EditText, fieldId: Int) {
                (first.context as Activity).setContentView(next); next.requestFocus()
                EditorBridgeNodeRouteTest.field = next; EditorBridgeNodeRouteTest.focused = next.createAccessibilityNodeInfo()
                EditorBridgeNodeRouteTest.info = EditorInfo().apply {
                    packageName = EditorBridgeNodeRouteTest.focused!!.packageName.toString(); this.fieldId = fieldId
                    inputType = next.inputType; initialSelStart = next.length(); initialSelEnd = next.length()
                }
                EditorBridgeNodeRouteTest.nativeKeyboard = next.onCreateInputConnection(EditorInfo())!!
                EditorBridgeNodeRouteTest.inputMethod!!.onStartInput(EditorBridgeNodeRouteTest.info!!, false)
            }
            fun settle() {
                val until = System.currentTimeMillis() + 3_000
                do { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
                while (ReflectionHelpers.getField<Boolean>(coordinator, "writing") && System.currentTimeMillis() < until)
            }
            val next = EditText(first.context).apply { setText("draft "); setSelection(length()) }
            // The user leaves for another app while the first insertion is still being confirmed.
            var reads = 0
            // Deterministic order: the write learns its field is gone (USER_EDIT) before any focus event.
            EditorBridgeNodeRouteTest.readFilter = { real ->
                if (++reads == 3) android.os.Handler(Looper.getMainLooper()).post { service.bridge.invalidate() }; real
            }
            coordinator.event(org.json.JSONObject().put("type", "conversation.item.input_audio_transcription.completed")
                .put("event_id", "a").put("item_id", "a").put("_editor_epoch", 0).put("transcript", "first words"))
            settle()
            EditorBridgeNodeRouteTest.readFilter = null
            moveTo(next, 77); coordinator.targetEvent(); settle()
            assertTrue(coordinator.active)
            val epoch = ReflectionHelpers.getField<Long>(coordinator, "editorEpoch")
            coordinator.event(org.json.JSONObject().put("type", "conversation.item.input_audio_transcription.completed")
                .put("event_id", "b").put("item_id", "b").put("_editor_epoch", epoch).put("transcript", "new field words"))
            settle()
            fun state() = "status=${coordinator.status}; epoch=$epoch; waiting=${ReflectionHelpers.getField<Boolean>(coordinator, "waitingForTarget")}; " +
                "editor=${ReflectionHelpers.getField<Any?>(coordinator, "editor") != null}; reads=$reads; first=${first.text}; lastError=${service.bridge.lastError}"
            assertEquals("New speech follows the newly focused field: ${state()}", "draft new field words", next.text.toString())
            assertEquals("first words", first.text.toString())
            coordinator.cancel()
        }

    @Test fun theKeyboardRecomposingUnchangedDictationIsNotTheUserTyping() =
        harness.withField("", keyboard = true) { service ->
            assertTrue(service.bridge.begin()); harness.activate(service)
            assertEquals(EditorWriteResult.APPLIED, harness.write(service, "Browser fixture speech"))
            // Gboard marks the word before the cursor as composing after an outside insertion
            // (Android 13/14 emulators: adopt-composing with text and cursor exactly ours).
            EditorBridgeNodeRouteTest.nativeKeyboard!!.setComposingRegion(16, 22)
            EditorBridgeNodeRouteTest.publishSelection()
            assertTrue(service.composing)
            assertEquals(EditorWriteResult.APPLIED, harness.write(service, "Browser final speech"))
            assertEquals("Browser final speech", EditorBridgeNodeRouteTest.field!!.text.toString())
            assertFalse(service.bridge.hasUserInteraction)
        }
    @Test fun theCursorRelativeRouteNeverDeletesAroundAComposingRegion() =
        harness.withField("", keyboard = true) { service ->
            assertTrue(service.bridge.begin()); harness.activate(service)
            EditorBridgeNodeRouteTest.readFilter = { real -> SurroundingText(real.text, real.selectionStart, real.selectionEnd, -1) }
            assertEquals(EditorWriteResult.APPLIED, harness.write(service, "Browser fixture speech"))
            EditorBridgeNodeRouteTest.nativeKeyboard!!.setComposingRegion(16, 22)
            EditorBridgeNodeRouteTest.publishSelection()
            harness.write(service, "Browser final speech")
            // Deleting before the cursor would count from the composing start ("Browspeech").
            assertEquals("Browser fixture speech", EditorBridgeNodeRouteTest.field!!.text.toString())
        }
    @Test fun aComposingWordTheUserIsTypingIsStillTheirs() =
        harness.withField("", keyboard = true) { service ->
            assertTrue(service.bridge.begin()); harness.activate(service)
            EditorBridgeNodeRouteTest.readFilter = { real -> SurroundingText(real.text, real.selectionStart, real.selectionEnd, -1) }
            assertEquals(EditorWriteResult.APPLIED, harness.write(service, "Browser fixture"))
            // The user types a new word: extra characters, composing.
            EditorBridgeNodeRouteTest.nativeKeyboard!!.setComposingText(" hel", 1)
            EditorBridgeNodeRouteTest.publishSelection()
            harness.write(service, "Browser fixture speech")
            assertTrue(service.bridge.hasUserInteraction)
            assertTrue(EditorBridgeNodeRouteTest.field!!.text.toString().startsWith("Browser fixture hel"))
        }

    @Test fun aHalfUpdatedEditorAfterInsertionIsNotMistakenForTheUserTyping() =
        harness.withField("", keyboard = true) { service ->
            assertTrue(service.bridge.begin()); harness.activate(service)
            // Chromium 109/113 report an unknown offset and, right after a commit, the new text with
            // the cursor not yet moved (Android 13/14 emulators); the next read is consistent.
            var reads = 0
            EditorBridgeNodeRouteTest.readFilter = { real ->
                reads++
                if (reads == 3) SurroundingText(real.text, 0, 0, -1)
                else SurroundingText(real.text, real.selectionStart, real.selectionEnd, -1)
            }
            assertEquals(EditorWriteResult.APPLIED, harness.write(service, "Browser fixture speech"))
            assertFalse("A transient editor state is not user typing", service.bridge.hasUserInteraction)
            assertEquals(EditorWriteResult.APPLIED, harness.write(service, "Browser final speech"))
            assertEquals("Browser final speech", EditorBridgeNodeRouteTest.field!!.text.toString())
        }
}
