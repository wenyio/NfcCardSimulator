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
        listOf(context.getString(R.string.pack_built_in) to true, context.getString(R.string.pack_my) to false).forEach { (title, builtIn) ->
            parent.addView(label(title, 13f, muted, true).apply { setPadding(dp(4), dp(8), 0, dp(10)) })
            packs.filter { it.builtIn == builtIn }.forEach { pack ->
                val panel = panel(parent).apply {
                    tag = "card-pack:" + pack.id
                    contentDescription = context.getString(R.string.pack_use, pack.name)
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
                    header.addView(label(context.getString(R.string.pack_active), 12f, blue, true).apply {
                        setPadding(dp(10), dp(6), dp(10), dp(6))
                        background = background(Color.rgb(239, 246, 255))
                        contentDescription = context.getString(R.string.pack_active_description, pack.name)
                    })
                }
                header.addView(icon(R.drawable.ic_more, context.getString(R.string.pack_actions, pack.name)) { more(it, pack) })
                panel.addView(header)
                panel.addView(label(context.getString(R.string.pack_card_count, pack.cards.size), 13f, muted))
                if (pack.description.isNotBlank()) panel.addView(label(pack.description, 13f, muted).apply {
                    maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                })
            }
        }
    }
}
