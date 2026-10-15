package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
