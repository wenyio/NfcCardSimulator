package com.iscolt.cardsimulator.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.view.WindowCompat

/** 统一控件样式；不包含卡片、文件或 NFC 业务。 */
class UiKit(val context: Context) {
    val ink = Color.rgb(21, 37, 56)
    val muted = Color.rgb(100, 116, 139)
    val blue = Color.rgb(37, 99, 235)

    /** 设置状态栏、导航栏及状态栏文字明暗，让应用窗口外观一致。 */
    fun systemBars(activity: Activity) = with(activity.window) {
        statusBarColor = ink; navigationBarColor = ink
        WindowCompat.getInsetsController(this, decorView).isAppearanceLightStatusBars = false
    }

    /**
     * 安装工具栏与占满剩余空间的内容容器，返回工具栏供 Activity 填充操作。
     * 内容区边距、滚动和生命周期由页面管理。
     */
    fun shell(activity: Activity, content: View): LinearLayout {
        systemBars(activity)
        val toolbar =
            row().apply { setBackgroundColor(ink); setPadding(dp(8), dp(4), dp(8), dp(4)) }
        activity.setContentView(column().apply {
            setBackgroundColor(Color.rgb(241, 245, 249)); addView(toolbar)
            addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        return toolbar
    }

    /** 把 dp（Density-independent Pixel，密度无关像素）按屏幕密度换算为整数 px（Pixel，像素）。 */
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    /** 创建统一圆角的纯色背景；尺寸由 dp 转为实际像素。 */
    fun background(color: Int) =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }

    /**
     * 创建带统一内边距的文字控件，按参数设置字号、颜色和粗体。
     * 字号使用 sp（Scale-independent Pixel，缩放无关像素），随屏幕密度和用户字体设置缩放。
     */
    fun label(text: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) =
        TextView(context).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    /** 创建纵向线性布局，供页面按从上到下的顺序添加子控件。 */
    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    /** 创建水平线性布局并让子控件垂直居中。 */
    fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }

    /** 在当前行添加等权重文字按钮，点击时执行调用方动作，并返回按钮实例。 */
    fun LinearLayout.action(title: String, action: () -> Unit): Button {
        val button = Button(context).apply {
            text = title; isAllCaps = false; textSize = 13f; minWidth = 0
            setPadding(dp(4), 0, dp(4), 0); setOnClickListener { action() }
        }
        addView(button, LinearLayout.LayoutParams(0, dp(50), 1f)); return button
    }

    /** 创建固定触控尺寸的图标按钮，同时设置无障碍说明和悬浮提示。 */
    fun icon(resource: Int, description: String, click: (android.view.View) -> Unit) =
        ImageButton(context).apply {
            setImageResource(resource); contentDescription = description; tooltipText = description
            setBackgroundColor(Color.TRANSPARENT); layoutParams =
            LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener(click)
        }

    /** 创建白色圆角纵向面板并挂到父容器，设置统一内边距与底部间距。 */
    fun panel(parent: LinearLayout): LinearLayout = column().apply {
        background = background(Color.WHITE); setPadding(dp(16), dp(12), dp(16), dp(12))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
}
