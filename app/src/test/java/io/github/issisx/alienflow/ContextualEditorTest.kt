package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import io.github.issisx.alienflow.EditorBridgeNodeRouteTest.Companion.field

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class, shadows = [EditorBridgeNodeRouteTest.Broker::class,
    EditorBridgeNodeRouteTest.NodeIpc::class, EditorBridgeNodeRouteTest.Ime::class, EditorBridgeNodeRouteTest.KeyboardIpc::class])
class ContextualEditorTest {
    private val harness = EditorBridgeNodeRouteTest()
    @Before fun reset() = harness.reset()
    @Test fun actualKeyboardReadSuppliesContextAndMidSentenceInsertionPreservesDraft() =
        harness.withField("I thinktoday", keyboard = true) { service ->
            field!!.setSelection(7)
            assertTrue(service.bridge.begin()); harness.activate(service)
            assertEquals(EditorWriteResult.APPLIED, harness.write(service, "That Works"))
            assertEquals("I think That Works today", field!!.text.toString())
            assertEquals(WritingContext("I think", "today"), service.bridge.writingContext)
            assertEquals(EditorWriteResult.APPLIED, harness.write(service, "that works"))
            assertEquals("I think that works today", field!!.text.toString())
            service.bridge.invalidate()
            assertEquals(WritingContext(), service.bridge.writingContext)
        }
}
