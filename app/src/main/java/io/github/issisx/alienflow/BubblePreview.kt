package io.github.issisx.alienflow

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Actual approved drawable, dp diameter, alpha and status painter; this is a preview, not a live connection. */
internal class BubblePreview(context: Context, private val prefs: SharedPreferences) : View(context) {
    private val artwork = context.getDrawable(R.drawable.alien_approved)!!
    private val indicator = DictationIndicator()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription = "Live appearance preview. Idle and recording icons on light and dark backgrounds." }
    internal val diameterPixels get() = (prefs.getInt("size", 32) * resources.displayMetrics.density).toInt()
    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val half = width / 2f
        paint.color = Color.rgb(234, 239, 245); canvas.drawRoundRect(0f, 0f, half - 3 * density, height.toFloat(), 12 * density, 12 * density, paint)
        paint.color = Color.rgb(9, 13, 20); canvas.drawRoundRect(half + 3 * density, 0f, width.toFloat(), height.toFloat(), 12 * density, 12 * density, paint)
        val diameter = diameterPixels
        for (side in 0..1) for (active in 0..1) {
            val cx = (side + .25f + active * .5f) * half
            val cy = height / 2f - 8 * density
            val alpha = BubbleOpacity.alpha(prefs.getInt(if (active == 0) "opacity" else "active_opacity", if (active == 0) BubbleOpacity.DEFAULT_IDLE_PERCENT else 100))
            val layer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (alpha * 255).toInt())
            artwork.setBounds((cx - diameter / 2f).toInt(), (cy - diameter / 2f).toInt(), (cx + diameter / 2f).toInt(), (cy + diameter / 2f).toInt()); artwork.draw(canvas)
            indicator.draw(canvas, cx, cy, diameter / 2f, density, if (active == 0) DictationReadiness.IDLE else DictationReadiness.LIVE, 0)
            canvas.restoreToCount(layer)
            paint.color = if (side == 0) Color.rgb(50, 62, 78) else Color.rgb(195, 209, 225)
            paint.textAlign = Paint.Align.CENTER; paint.textSize = 12 * resources.displayMetrics.scaledDensity
            canvas.drawText(if (active == 0) "Idle" else "Recording", cx, height - 12 * density, paint)
        }
    }
}
