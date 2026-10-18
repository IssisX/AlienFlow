package io.github.issisx.alienflow

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Native controls with one restrained visual language. All dimensions are dp/sp. */
internal class AppUi(val context: Context) {
    val background = Color.rgb(11, 13, 16)
    val surface = Color.rgb(20, 23, 28)
    val border = Color.rgb(38, 43, 51)
    val ink = Color.rgb(236, 239, 243)
    val muted = Color.rgb(150, 159, 171)
    val accent = Color.rgb(92, 152, 255)
    val onAccent = Color.WHITE
    val primaryFill = Color.rgb(46, 110, 230)
    val danger = Color.rgb(255, 128, 116)
    fun dp(value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun shape(color: Int, radius: Int = 10, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    fun ripple(color: Int, radius: Int = 10, stroke: Int? = null) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(40, 92, 152, 255)), shape(color, radius, stroke), shape(Color.WHITE, radius))
    fun text(value: String, size: Int = 15, color: Int = ink, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(2).toFloat(), 1f)
    }
    fun text(parent: LinearLayout, value: String, size: Int = 15, color: Int = ink, bold: Boolean = false): TextView {
        return text(value, size, color, bold).also { parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }) }
    }
    fun card(parent: LinearLayout): LinearLayout = column().apply {
        background = shape(surface, 12, border); setPadding(dp(16), dp(16), dp(16), dp(16))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    fun button(parent: LinearLayout, label: String, primary: Boolean = false, destructive: Boolean = false, action: () -> Unit): Button {
        return Button(context).apply {
            text = label; isAllCaps = false; textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            minHeight = dp(48); minimumHeight = dp(48); setPadding(dp(16), dp(10), dp(16), dp(10))
            setTextColor(if (primary) onAccent else if (destructive) danger else ink)
            background = ripple(if (primary) primaryFill else surface, 10, if (primary) null else border)
            // Starting dictation must keep the test editor focused. Other controls retain
            // ordinary keyboard / TalkBack navigation.
            if (primary && label == "Start dictation") isFocusable = false
            setOnClickListener { action() }
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }
    fun row(parent: LinearLayout, label: String, description: String, action: () -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL
            minimumHeight = dp(68); setPadding(dp(16), dp(14), dp(16), dp(14))
            background = ripple(surface, 12, border)
            val words = column()
            words.addView(text(label, 16, bold = true))
            words.addView(text(description, 13, muted).apply { setPadding(0, dp(3), dp(8), 0) })
            addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text("›", 22, muted))
            isClickable = true; isFocusable = true
            contentDescription = "$label. $description"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            words.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setOnClickListener { action() }
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }
    /** Reading width on a Fold/tablet without fixed heights or screen-width assumptions. */
    fun readingFrame() = object : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = View.MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(dp(720))
            super.onMeasure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), heightMeasureSpec)
        }
    }
}
