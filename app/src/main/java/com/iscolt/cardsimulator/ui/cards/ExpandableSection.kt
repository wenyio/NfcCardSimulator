package com.iscolt.cardsimulator.ui.cards

import android.view.View
import android.widget.LinearLayout
import com.iscolt.cardsimulator.R
import com.iscolt.cardsimulator.ui.UiKit

/**
 * 创建默认折叠的标题和正文容器，点击只改变可见性与无障碍描述，返回正文供调用方填充。
 */
internal fun expandableSection(ui: UiKit, parent: LinearLayout, title: String): LinearLayout =
    with(ui) {
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
            contentDescription = context.getString(R.string.accessibility_expand, title)
            setOnClickListener {
                val expanded = body.visibility != View.VISIBLE
                body.visibility = if (expanded) View.VISIBLE else View.GONE
                arrow.rotation = if (expanded) 180f else 0f
                contentDescription = context.getString(
                    if (expanded) R.string.accessibility_collapse else R.string.accessibility_expand,
                    title
                )
            }
        }
        parent.addView(header); parent.addView(body)
        body
    }
