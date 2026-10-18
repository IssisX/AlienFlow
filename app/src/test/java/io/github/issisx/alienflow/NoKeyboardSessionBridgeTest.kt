package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Android 11-12 branch on the shared node-route harness. Kept out of EditorBridgeNodeRouteTest so
 * scripts/prove_app_switch_regression.py can still replay that file on shipped source. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class, shadows = [EditorBridgeNodeRouteTest.Broker::class,
    EditorBridgeNodeRouteTest.NodeIpc::class, EditorBridgeNodeRouteTest.Ime::class, EditorBridgeNodeRouteTest.KeyboardIpc::class])
class NoKeyboardSessionBridgeTest {
    private val harness = EditorBridgeNodeRouteTest()
    @Before fun reset() = harness.reset()
    @Test fun beforeAndroid13NoKeyboardSessionSoOnlyEmptyFieldsBindAndTheReasonIsExplicit() {
        val actual = android.os.Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(android.os.Build.VERSION::class.java, "SDK_INT", 30)
        try {
            harness.withField("draft", keyboard = true) { service ->
                assertFalse("Android 11 exposes no accessibility keyboard session", service.keyboardAttached)
                assertFalse(service.bridge.begin())
                assertTrue(service.bridge.lastError, service.bridge.lastError.startsWith("[AF-E17]"))
                assertEquals("draft", EditorBridgeNodeRouteTest.field!!.text.toString()); assertEquals(0, EditorBridgeNodeRouteTest.mutations)
            }
            harness.withField("", keyboard = true) { service ->
                assertTrue(service.bridge.begin()); harness.activate(service)
                assertEquals(EditorWriteResult.APPLIED, harness.write(service, "hello"))
                assertEquals(EditorWriteResult.APPLIED, harness.write(service, "Hello there."))
                assertEquals("Hello there.", EditorBridgeNodeRouteTest.field!!.text.toString())
                assertTrue("Node actions only; no keyboard IPC", EditorBridgeNodeRouteTest.committedTexts.isEmpty() && EditorBridgeNodeRouteTest.selections.isEmpty())
            }
        } finally { ReflectionHelpers.setStaticField(android.os.Build.VERSION::class.java, "SDK_INT", actual) }
    }
}
