package io.github.issisx.alienflow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import kotlin.math.abs
import kotlin.math.max

class AlienAccessibilityService : AccessibilityService() {
    val handler = Handler(Looper.getMainLooper())
    lateinit var bridge: EditorBridge; private set
    private val app get() = application as AlienApp
    private lateinit var wm: WindowManager
    private var bubble: AlienView? = null
    private var params: WindowManager.LayoutParams? = null
    private var menu: View? = null
    private var hidden = false
    private var imeBottom = 0
    private var safeTop = 0
    private var safeBottom = 0
    var compositionKnown = false; private set
    var composing = false; private set
    private var layoutKey = "folded"
    fun confirmEmptyComposition() { compositionKnown = true; composing = false }
    override fun onCreateInputMethod(): android.accessibilityservice.InputMethod = object : android.accessibilityservice.InputMethod(this) {
        override fun onStartInput(info: android.view.inputmethod.EditorInfo, restarting: Boolean) {
            super.onStartInput(info, restarting); compositionKnown = false; composing = false
            if (::bridge.isInitialized) { bridge.invalidate(); handler.post { app.coordinator.targetEvent() } }
        }
        override fun onFinishInput() {
            super.onFinishInput(); compositionKnown = false; composing = false
            if (::bridge.isInitialized) { bridge.invalidate(); handler.post { app.coordinator.targetEvent() } }
        }
        override fun onUpdateSelection(oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
            super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, candidatesStart, candidatesEnd)
            compositionKnown = true; composing = candidatesStart >= 0 && candidatesEnd >= candidatesStart
            handler.post { app.coordinator.targetEvent() }
        }
    }
    override fun onServiceConnected() {
        super.onServiceConnected(); bridge = EditorBridge(this); app.coordinator.service = this
        wm = getSystemService(WindowManager::class.java); showBubble(); refreshBubble()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun bounds() = wm.currentWindowMetrics.bounds
    private fun usableHeight() = max(dp(48), bounds().height() - imeBottom - safeBottom - safeTop)
    private fun showBubble() {
        if (bubble != null) return
        val touch = dp(max(48, app.store.prefs.getInt("size", 32)))
        val p = WindowManager.LayoutParams(touch, touch, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.LEFT
        params = p
        bubble = AlienView().also { view ->
            view.contentDescription = "AlienFlow. Tap to start or stop dictation; drag to move; long press for actions"
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            view.setOnApplyWindowInsetsListener { _, insets ->
                imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
                val system = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()); safeTop = system.top; safeBottom = system.bottom
                clamp(); insets
            }
            wm.addView(view, p)
        }
        restorePosition()
    }
    fun refreshBubble() {
        val view = bubble ?: return
        val insets = view.rootWindowInsets ?: wm.currentWindowMetrics.windowInsets
        imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
        val system = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()); safeTop = system.top; safeBottom = system.bottom
        clamp()
        val active = app.coordinator.active || app.coordinator.finishing
        view.contentDescription = "AlienFlow. ${app.coordinator.status}. Tap to start or stop; drag to move; long press for actions"
        view.alpha = if (active) app.store.prefs.getInt("active_opacity", 100) / 100f else app.store.prefs.getInt("opacity", 25) / 100f
        view.visibility = if (active || (!hidden && (app.store.prefs.getBoolean("always", false) || bridge.focused() != null))) View.VISIBLE else View.GONE
        val touch = dp(max(48, app.store.prefs.getInt("size", 32)))
        if (params?.width != touch) { params?.width = touch; params?.height = touch; clamp() }
        val key = if (bounds().width() / resources.displayMetrics.density > 600) "unfolded" else "folded"
        if (key != layoutKey) restorePosition()
        view.invalidate()
    }
    fun resetPosition() { app.store.prefs.edit().remove("x_folded").remove("y_folded").remove("x_unfolded").remove("y_unfolded").apply(); hidden = false; restorePosition(); refreshBubble() }
    private fun restorePosition() {
        layoutKey = if (bounds().width() / resources.displayMetrics.density > 600) "unfolded" else "folded"
        val p = params ?: return
        p.x = ((bounds().width() - p.width) * app.store.prefs.getFloat("x_$layoutKey", .85f)).toInt()
        p.y = safeTop + ((usableHeight() - p.height) * app.store.prefs.getFloat("y_$layoutKey", .9f)).toInt()
        clamp()
    }
    private fun clamp() {
        val p = params ?: return; val view = bubble ?: return
        p.x = p.x.coerceIn(0, (bounds().width() - p.width).coerceAtLeast(0))
        p.y = p.y.coerceIn(safeTop, (safeTop + usableHeight() - p.height).coerceAtLeast(safeTop))
        if (view.isAttachedToWindow) wm.updateViewLayout(view, p)
    }
    private fun remember() { val p = params ?: return; app.store.prefs.edit().putFloat("x_$layoutKey", p.x.toFloat() / max(1, bounds().width() - p.width)).putFloat("y_$layoutKey", (p.y - safeTop).toFloat() / max(1, usableHeight() - p.height)).apply() }
    private fun openMenu() {
        if (menu != null) return
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(26, 38, 27)); setPadding(dp(8), dp(8), dp(8), dp(8)) }
        fun item(label: String, action: () -> Unit) { column.addView(Button(this).apply { text = label; setOnClickListener { closeMenu(); action() } }) }
        if (app.coordinator.active || app.coordinator.finishing) item("Cancel dictation") { app.coordinator.cancel() }
        item("Settings") { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        item("Hide alien") { hidden = true; refreshBubble() }
        item("Close") { }
        val p = WindowManager.LayoutParams(dp(220), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.LEFT; p.x = (params!!.x - dp(170)).coerceIn(0, max(0, bounds().width() - dp(220))); p.y = params!!.y.coerceAtMost(max(safeTop, safeTop + usableHeight() - dp(250)))
        menu = column; wm.addView(column, p)
        handler.postDelayed({ closeMenu() }, 10000)
    }
    private fun closeMenu() { menu?.let { wm.removeView(it) }; menu = null }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::bridge.isInitialized) return
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED || event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) bubble?.requestApplyInsets()
        app.coordinator.targetEvent(); refreshBubble()
    }
    override fun onInterrupt() { if (app.coordinator.active) app.coordinator.failure("Accessibility feedback interrupted; recording stopped and audio preserved") }
    override fun onDestroy() { closeMenu(); bubble?.let { wm.removeView(it) }; bubble = null; app.coordinator.serviceLost(); super.onDestroy() }
    private inner class AlienView : View(this@AlienAccessibilityService) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var rawX = 0f; private var rawY = 0f; private var x0 = 0; private var y0 = 0
        private var dragged = false; private var longPressed = false; private var holdStarted = false
        private val threshold = ViewConfiguration.get(context).scaledTouchSlop
        private val longPress = Runnable {
            if (!dragged) {
                longPressed = true
                if (app.store.prefs.getBoolean("hold", false)) { holdStarted = true; app.coordinator.start() }
                else openMenu()
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val diameter = dp(app.store.prefs.getInt("size", 32)).toFloat(); val cx = width / 2f; val cy = height / 2f; val r = diameter / 2
            paint.color = Color.rgb(166, 242, 123)
            val shape = Path().apply { moveTo(cx, cy + r * .9f); cubicTo(cx - r * 1.4f, cy - r * .05f, cx - r, cy - r, cx, cy - r); cubicTo(cx + r, cy - r, cx + r * 1.4f, cy - r * .05f, cx, cy + r * .9f); close() }
            canvas.drawPath(shape, paint)
            paint.color = Color.rgb(13, 23, 15)
            canvas.save(); canvas.rotate(25f, cx - r * .4f, cy - r * .13f); canvas.drawOval(cx - r * .75f, cy - r * .4f, cx - r * .12f, cy + r * .06f, paint); canvas.restore()
            canvas.save(); canvas.rotate(-25f, cx + r * .4f, cy - r * .13f); canvas.drawOval(cx + r * .12f, cy - r * .4f, cx + r * .75f, cy + r * .06f, paint); canvas.restore()
            canvas.drawCircle(cx, cy + r * .4f, r * .09f, paint)
            if (app.coordinator.active || app.coordinator.finishing) { paint.color = Color.rgb(255, 117, 95); canvas.drawCircle(cx + r * .75f, cy - r * .8f, dp(4).toFloat(), paint) }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { rawX = event.rawX; rawY = event.rawY; x0 = params!!.x; y0 = params!!.y; dragged = false; longPressed = false; holdStarted = false; handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong()); return true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - rawX; val dy = event.rawY - rawY
                    if (!holdStarted && (abs(dx) > threshold || abs(dy) > threshold)) { dragged = true; handler.removeCallbacks(longPress); params!!.x = x0 + dx.toInt(); params!!.y = y0 + dy.toInt(); clamp() }
                    return true
                }
                MotionEvent.ACTION_UP -> { handler.removeCallbacks(longPress); if (dragged) remember() else if (holdStarted) app.coordinator.stop() else if (!longPressed) performClick(); return true }
                MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(longPress); if (holdStarted) app.coordinator.stop(); return true }
            }
            return super.onTouchEvent(event)
        }
        override fun performClick(): Boolean { super.performClick(); app.coordinator.toggle(); return true }
    }
}
