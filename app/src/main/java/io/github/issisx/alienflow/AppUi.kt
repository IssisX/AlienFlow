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
    val background = Color.rgb(14, 18, 25)
    val surface = Color.rgb(25, 32, 43)
    val border = Color.rgb(52, 64, 82)
    val ink = Color.rgb(239, 244, 250)
    val muted = Color.rgb(173, 187, 205)
    val accent = Color.rgb(198, 222, 244)
    val danger = Color.rgb(255, 178, 168)
    fun dp(value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun shape(color: Int, radius: Int = 18, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    fun ripple(color: Int, radius: Int = 18, stroke: Int? = null) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(45, 198, 222, 244)), shape(color, radius, stroke), shape(Color.WHITE, radius))
    fun text(value: String, size: Int = 15, color: Int = ink, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    fun text(parent: LinearLayout, value: String, size: Int = 15, color: Int = ink, bold: Boolean = false): TextView {
        return text(value, size, color, bold).also { parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }) }
    }
    fun card(parent: LinearLayout): LinearLayout = column().apply {
        background = shape(surface, 20, border); setPadding(dp(18), dp(18), dp(18), dp(18))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
    }
    fun button(parent: LinearLayout, label: String, primary: Boolean = false, destructive: Boolean = false, action: () -> Unit): Button {
        return Button(context).apply {
            text = label; isAllCaps = false; textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            minHeight = dp(52); minimumHeight = dp(52); setPadding(dp(16), dp(10), dp(16), dp(10))
            setTextColor(if (primary) backgroundColor() else if (destructive) danger else ink)
            background = ripple(if (primary) accent else surface, 14, if (primary) null else border)
            // Starting dictation must keep the test editor focused. Other controls retain
            // ordinary keyboard / TalkBack navigation.
            if (primary && label == "Start dictation") isFocusable = false
            setOnClickListener { action() }
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }
    private fun backgroundColor() = background
    fun row(parent: LinearLayout, label: String, description: String, action: () -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL
            minimumHeight = dp(80); setPadding(dp(18), dp(16), dp(18), dp(16))
            background = ripple(surface, 18, border)
            val words = column()
            words.addView(text(label, 17, bold = true))
            words.addView(text(description, 14, muted).apply { setPadding(0, dp(5), dp(8), 0) })
            addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text("›", 28, muted))
            isClickable = true; isFocusable = true
            contentDescription = "$label. $description"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            words.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setOnClickListener { action() }
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
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
