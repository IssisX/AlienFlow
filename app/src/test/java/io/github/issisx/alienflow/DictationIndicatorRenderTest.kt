package io.github.issisx.alienflow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Production artwork + production painter under Android native graphics, not a UI mock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DictationIndicatorRenderTest {
    @Test fun productionJewelShowsReadinessOnLightAndDarkBackgrounds() {
        val app = RuntimeEnvironment.getApplication() as AlienApp
        val indicator = DictationIndicator()
        val states = listOf(DictationReadiness.IDLE, DictationReadiness.STARTING, DictationReadiness.BUFFERING,
            DictationReadiness.LIVE, DictationReadiness.LOCAL, DictationReadiness.FINISHING)
        val labels = listOf("Idle", "Starting", "Ready to speak", "Connected", "Local capture", "Finishing")
        for ((name, background) in listOf("light" to Color.rgb(245, 246, 248), "dark" to Color.rgb(14, 19, 26))) {
            val bitmap = Bitmap.createBitmap(480, 300, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); canvas.drawColor(background)
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (name == "light") Color.rgb(34, 46, 58) else Color.rgb(227, 236, 245)
                textSize = 15f; textAlign = Paint.Align.CENTER
            }
            val artwork = app.getDrawable(R.drawable.alien_approved)!!
            for ((index, state) in states.withIndex()) {
                val cx = 80f + (index % 3) * 160f; val cy = 60f + (index / 3) * 150f
                artwork.setBounds((cx-48).toInt(), (cy-48).toInt(), (cx+48).toInt(), (cy+48).toInt())
                artwork.draw(canvas)
                val spinning = indicator.draw(canvas, cx, cy, 48f, 3f, state, 300L)
                assertEquals(state == DictationReadiness.STARTING || state == DictationReadiness.FINISHING, spinning)
                if (state == DictationReadiness.BUFFERING || state == DictationReadiness.LIVE) {
                    assertEquals(Color.rgb(99, 239, 176), bitmap.getPixel((cx+48*0.66f).toInt(), (cy+48*0.66f).toInt()))
                }
                canvas.drawText(labels[index], cx, cy + 72f, label)
            }
            val output = File("build/reports/ui-proof/indicator-$name-0.1.25-chat.1.png")
            output.parentFile!!.mkdirs(); output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
