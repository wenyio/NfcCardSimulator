package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import com.iscolt.cardsimulator.R
import com.iscolt.cardsimulator.ui.UiKit

/** 统一展示真实模拟状态和启动问题，问题弹窗不会暂停或重置当前会话。 */
class SimulationControl(private val ui: UiKit) {
    private var toggle: SwitchCompat? = null
    private var indicator: View? = null
    private var issue: ImageButton? = null
    private var error: String? = null
    private var updating = false

    fun show(parent: LinearLayout, switch: (Boolean) -> Unit) = with(ui) {
        panel(parent).apply {
            // 加上内容容器原有的 4dp 顶部留白，控制卡距工具栏共 16dp。
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(12)
            addView(row().apply {
                indicator = View(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }
                addView(indicator, LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(10) })
                addView(label("卡片模拟", 16f))
                issue = icon(R.drawable.ic_error_outline, "查看模拟问题") {
                    error?.let { reason ->
                        AlertDialog.Builder(context).setTitle("模拟未启动").setMessage(reason)
                            .setPositiveButton("知道了", null).show()
                    }
                }.apply { imageTintList = ColorStateList.valueOf(Color.rgb(180, 83, 9)) }
                addView(issue)
                addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                toggle = SwitchCompat(context).apply {
                    contentDescription = "卡片模拟开关"; minHeight = dp(48)
                    setOnCheckedChangeListener { _, checked -> if (!updating) switch(checked) }
                }
                addView(toggle, LinearLayout.LayoutParams(-2, dp(48)))
            })
        }
    }

    fun update(enabled: Boolean, problem: String?) = with(ui) {
        // 实际启用状态优先；失败后开关复位，问题入口只在有明确错误时出现。
        error = if (enabled) null else problem
        updating = true; toggle?.isChecked = enabled; updating = false
        indicator?.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (enabled) setColor(Color.rgb(5, 122, 85)) else {
                    setColor(Color.TRANSPARENT); setStroke(dp(1), muted)
                }
            }
            contentDescription = if (enabled) "正在模拟" else "模拟未运行"
        }
        issue?.visibility = if (error == null) View.GONE else View.VISIBLE
    }
    fun detach() { toggle = null; indicator = null; issue = null; error = null }
}
