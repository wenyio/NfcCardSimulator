package com.iscolt.cardsimulator.ui.cards

import android.view.View
import android.widget.LinearLayout
import com.iscolt.cardsimulator.R
import com.iscolt.cardsimulator.ui.UiKit

/** 条件、来源等低频信息使用与规则一致的矢量箭头和可访问点击区域。 */
internal fun expandableSection(ui: UiKit, parent: LinearLayout, title: String): LinearLayout = with(ui) {
    val body = column().apply { visibility = View.GONE }
    val arrow = android.widget.ImageView(context).apply {
        setImageResource(R.drawable.ic_expand_more)
        imageTintList = android.content.res.ColorStateList.valueOf(muted)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val header = row().apply {
        minimumHeight = dp(48); isFocusable = true
        addView(label(title, 14f, blue), LinearLayout.LayoutParams(0, -2, 1f))
        addView(arrow, LinearLayout.LayoutParams(dp(24), dp(24)))
        contentDescription = "$title，展开"
        setOnClickListener {
            val expanded = body.visibility != View.VISIBLE
            body.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.rotation = if (expanded) 180f else 0f
            contentDescription = "$title，" + if (expanded) "收起" else "展开"
        }
    }
    parent.addView(header); parent.addView(body)
    body
}
