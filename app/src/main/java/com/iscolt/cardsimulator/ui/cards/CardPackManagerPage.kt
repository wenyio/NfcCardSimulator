package com.iscolt.cardsimulator.ui.cards

import android.graphics.Color
import android.text.TextUtils
import android.view.View
import android.widget.*
import com.iscolt.cardsimulator.R
import com.iscolt.cardsimulator.core.CardPack
import com.iscolt.cardsimulator.ui.UiKit

/** 点击整张卡包切换；行尾菜单单独处理，避免管理操作触发切包。 */
class CardPackManagerPage(private val ui: UiKit) {
    fun show(parent: LinearLayout, packs: List<CardPack>, activeId: String,
        use: (CardPack) -> Unit, more: (View, CardPack) -> Unit) = with(ui) {
        parent.removeAllViews()
        listOf("内置卡包" to true, "我的卡包" to false).forEach { (title, builtIn) ->
            parent.addView(label(title, 13f, muted, true).apply { setPadding(dp(4), dp(8), 0, dp(10)) })
            packs.filter { it.builtIn == builtIn }.forEach { pack ->
                val panel = panel(parent).apply {
                    tag = "card-pack:" + pack.id
                    contentDescription = "使用卡包：" + pack.name
                    isFocusable = true
                    setOnClickListener { use(pack) }
                    val ripple = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                    foreground = androidx.core.content.ContextCompat.getDrawable(context, ripple.resourceId)
                }
                val header = row()
                header.addView(label(pack.name, 17f, ink, true).apply {
                    maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (pack.id == activeId) {
                    header.addView(label("当前使用", 12f, blue, true).apply {
                        setPadding(dp(10), dp(6), dp(10), dp(6))
                        background = background(Color.rgb(239, 246, 255))
                        contentDescription = "当前使用卡包：" + pack.name
                    })
                }
                header.addView(icon(R.drawable.ic_more, "卡包操作：" + pack.name) { more(it, pack) })
                panel.addView(header)
                panel.addView(label(pack.cards.size.toString() + " 张卡片", 13f, muted))
                if (pack.description.isNotBlank()) panel.addView(label(pack.description, 13f, muted).apply {
                    maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                })
            }
        }
    }
}
