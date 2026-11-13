package com.iscolt.cardsimulator.ui
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*

/** 统一控件样式；不包含卡片、文件或 NFC 业务。 */
class UiKit(val context: Context) {
    val ink = Color.rgb(21, 37, 56)
    val muted = Color.rgb(100, 116, 139)
    val blue = Color.rgb(37, 99, 235)
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun background(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }
    fun label(text: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(context).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }
    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun LinearLayout.action(title: String, action: () -> Unit): Button {
        val button = Button(context).apply {
            text = title; isAllCaps = false; textSize = 13f; minWidth = 0
            setPadding(dp(4), 0, dp(4), 0); setOnClickListener { action() }
        }
        addView(button, LinearLayout.LayoutParams(0, dp(50), 1f)); return button
    }
    fun icon(resource: Int, description: String, click: (android.view.View) -> Unit) = ImageButton(context).apply {
        setImageResource(resource); contentDescription = description; tooltipText = description
        setBackgroundColor(Color.TRANSPARENT); layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setOnClickListener(click)
    }
    fun panel(parent: LinearLayout): LinearLayout = column().apply {
        background = background(Color.WHITE); setPadding(dp(16), dp(12), dp(16), dp(12))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
}
