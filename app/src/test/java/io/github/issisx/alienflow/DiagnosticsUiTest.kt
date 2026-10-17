package io.github.issisx.alienflow

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Actual activities, resource decoding and click handlers under Robolectric.
 * No microphone/provider is mocked into a claimed integration pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagnosticsUiTest {
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun button(root: View, label: String) = descendants(root).filterIsInstance<Button>().single { it.text.toString() == label }
    private fun screenshot(view: View, name: String) {
        view.measure(View.MeasureSpec.makeMeasureSpec(822, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1782, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 822, 1782)
        val bitmap = Bitmap.createBitmap(822, 1782, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/reports/ui-proof").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun diagnosticsLiveUnderHelpAndShowTheCurrentVersion() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get(); val root = activity.window.decorView
            shadowOf(Looper.getMainLooper()).idle()
            screenshot(root, "main-0.1.26-chat.1.png")
            assertFalse(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Diagnostics / share report" })
            button(root, "Settings").performClick()
            descendants(root).single { it.contentDescription?.toString()?.startsWith("Help & about.") == true }.performClick()
            assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Version 0.1.26-chat.1 (26)" })
            val entry = button(root, "Diagnostics / share report")
            assertTrue(entry.isShown)
            assertTrue(entry.performClick())
            assertEquals(DiagnosticsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
        }
    }
    @Test fun diagnosticCopyAndShareUseTheActualVisibleReport() {
        Robolectric.buildActivity(DiagnosticsActivity::class.java).setup().use { controller ->
            val activity = controller.get(); val root = activity.window.decorView
            val copy = button(root, "Copy diagnostic report")
            val deadline = System.currentTimeMillis() + 10000
            while (!copy.isEnabled && System.currentTimeMillis() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(11) }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Report did not finish loading", copy.isEnabled)
            screenshot(root, "diagnostics-0.1.26-chat.1.png")
            assertTrue(copy.performClick())
            val copied = activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
            assertTrue(copied.contains("ALIENFLOW DIAGNOSTIC REPORT"))
            assertTrue(copied.contains("Version: 0.1.26-chat.1 (26)"))
            assertTrue(copied.contains("Transport: Android AudioRecord + OpenAI Realtime WebSocket"))
            assertTrue(copied.contains("Native WebRTC packaged: false"))
            assertTrue(copied.contains("ANDROID RECORDED PROCESS EXITS"))
            assertTrue(button(root, "Share diagnostics").performClick())
            val chooser = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
            assertEquals(Intent.ACTION_SEND, send.action); assertEquals("text/plain", send.type)
            assertEquals(copied, send.getStringExtra(Intent.EXTRA_TEXT))
            assertTrue(button(root, "Save report as a file").performClick())
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, shadowOf(activity).nextStartedActivityForResult.intent.action)
        }
    }
}
