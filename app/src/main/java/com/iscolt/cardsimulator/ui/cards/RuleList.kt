package com.iscolt.cardsimulator.ui.cards

import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.content.res.ColorStateList
import android.view.View
import android.widget.LinearLayout
import android.widget.ImageView
import com.iscolt.cardsimulator.R
import com.iscolt.cardsimulator.core.ApduRule
import com.iscolt.cardsimulator.core.MatchMode
import com.iscolt.cardsimulator.ui.UiKit

/** 规则摘要使用紧凑行，长 APDU 仅在展开后构造，避免挤占脚本总览。 */
class RuleList(private val ui: UiKit) {
    /** 构建规则摘要和折叠明细，展开时才拼接完整命令与回复；展开不执行规则或改变游标。 */
    fun show(parent: LinearLayout, rules: List<ApduRule>) = with(ui) {
        rules.forEachIndexed { index, rule ->
            if (index > 0) parent.addView(
                View(context).apply { setBackgroundColor(Color.rgb(226, 232, 240)) },
                LinearLayout.LayoutParams(-1, dp(1))
            )
            val details = label("", 12f).apply {
                visibility = View.GONE; typeface = Typeface.MONOSPACE
                setTextIsSelectable(true); setPadding(dp(8), dp(4), dp(8), dp(12))
            }
            // 使用固定尺寸的矢量图标，避免文字箭头受字体基线和字形影响。
            val arrow = ImageView(context).apply {
                setImageResource(R.drawable.ic_expand_more)
                imageTintList = ColorStateList.valueOf(muted)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val title = rule.label.ifBlank { context.getString(R.string.rule_unnamed) }
            val mode = MatchingLabels.mode(context, rule.mode)
            val header = row().apply {
                minimumHeight = dp(48); isFocusable = true
                val ripple = TypedValue()
                context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground,
                    ripple,
                    true
                )
                setBackgroundResource(ripple.resourceId)
                addView(label("${index + 1}", 13f, muted), LinearLayout.LayoutParams(dp(28), -2))
                addView(label(title, 14f).apply {
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(label(mode, 12f, muted).apply { setPadding(dp(8), 0, dp(4), 0) })
                addView(
                    arrow,
                    LinearLayout.LayoutParams(dp(24), dp(24)).apply { leftMargin = dp(8) })
                contentDescription = context.getString(
                    R.string.accessibility_expand,
                    context.getString(R.string.rule_accessibility, index + 1, title, mode)
                )
                setOnClickListener {
                    // 展开只影响视图，不触发匹配引擎或改变当前交易步骤。
                    val expanded = details.visibility != View.VISIBLE
                    if (expanded) details.text = buildList {
                        add("T: ${rule.command}"); add("C: ${rule.response}")
                        if (rule.mode == MatchMode.MASK) add("MASK: ${rule.mask}")
                    }.joinToString(System.lineSeparator())
                    details.visibility = if (expanded) View.VISIBLE else View.GONE
                    arrow.rotation = if (expanded) 180f else 0f
                    contentDescription = context.getString(
                        if (expanded) R.string.accessibility_collapse else R.string.accessibility_expand,
                        context.getString(R.string.rule_accessibility, index + 1, title, mode)
                    )
                }
            }
            parent.addView(header, LinearLayout.LayoutParams(-1, -2))
            parent.addView(details)
        }
    }
}
