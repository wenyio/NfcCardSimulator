package com.iscolt.cardsimulator.ui.cards

import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R

/** 短说明直接显示；超过三行的说明默认收起，按实际排版判断是否需要展开入口。 */
class CardDescription(private val ui: UiKit) {
    fun show(parent: LinearLayout, description: String) = with(ui) {
        if (description.isBlank()) return@with
        val text = label(description, 14f, muted).apply {
            maxLines = 3; ellipsize = TextUtils.TruncateAt.END
        }
        val expand = label(context.getString(R.string.description_expand), 13f, blue).apply {
            visibility = View.GONE; minHeight = dp(48); gravity = android.view.Gravity.CENTER_VERTICAL
            isFocusable = true; var expanded = false
            setOnClickListener {
                expanded = !expanded
                text.maxLines = if (expanded) Int.MAX_VALUE else 3
                text.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
                (it as TextView).text = context.getString(if (expanded) R.string.description_collapse else R.string.description_expand)
            }
        }
        parent.addView(text); parent.addView(expand)
        text.post {
            val layout = text.layout ?: return@post
            // 依据最终行数及省略状态判断，兼容中英文、换行及不同屏幕宽度。
            val truncated = layout.lineCount > 3 ||
                (layout.lineCount > 0 && layout.getEllipsisCount(layout.lineCount - 1) > 0)
            expand.visibility = if (truncated) View.VISIBLE else View.GONE
        }
    }
}
