package io.github.issisx.alienflow

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Pure state selection; elapsed animation time never decides whether it is safe to speak. */
enum class DictationReadiness { IDLE, STARTING, BUFFERING, LIVE, LOCAL, FINISHING;
    companion object {
        fun from(active: Boolean, finishing: Boolean, captured: Boolean,
                 connected: Boolean, interrupted: Boolean): DictationReadiness = when {
            finishing -> FINISHING
            !active -> IDLE
            !captured -> STARTING
            interrupted && !connected -> LOCAL
            connected -> LIVE
            else -> BUFFERING
        }
    }
}

/** Small, dual-contrast status jewel over the approved artwork. No text or focusable surface. */
class DictationIndicator {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()
    fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, density: Float,
             state: DictationReadiness, elapsedMs: Long): Boolean {
        if (state == DictationReadiness.IDLE) return false
        val dotRadius = 3f * density
        val dx = cx + radius * 0.66f
        val dy = cy + radius * 0.66f
        if (state == DictationReadiness.LIVE) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * density
            paint.color = Color.rgb(8, 27, 34)
            canvas.drawCircle(cx, cy, radius + 0.3f * density, paint)
            paint.strokeWidth = 0.8f * density
            paint.color = Color.rgb(76, 222, 219)
            canvas.drawCircle(cx, cy, radius, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(245, 248, 251)
        canvas.drawCircle(dx, dy, dotRadius + 1.5f * density, paint)
        paint.color = Color.rgb(14, 27, 39)
        canvas.drawCircle(dx, dy, dotRadius + 0.8f * density, paint)
        val spinning = state == DictationReadiness.STARTING || state == DictationReadiness.FINISHING
        if (spinning) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f * density
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = if (state == DictationReadiness.FINISHING) Color.rgb(251, 199, 107) else Color.rgb(214, 228, 239)
            arc.set(dx - dotRadius + density, dy - dotRadius + density,
                dx + dotRadius - density, dy + dotRadius - density)
            canvas.drawArc(arc, (elapsedMs % 1_200L) * 360f / 1_200f - 90f, 225f, false, paint)
            paint.strokeCap = Paint.Cap.BUTT
        } else {
            paint.color = if (state == DictationReadiness.LOCAL) Color.rgb(251, 199, 107) else Color.rgb(99, 239, 176)
            canvas.drawCircle(dx, dy, dotRadius, paint)
        }
        return spinning
    }
}
