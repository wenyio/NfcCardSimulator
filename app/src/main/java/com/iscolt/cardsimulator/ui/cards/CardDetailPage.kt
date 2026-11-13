package com.iscolt.cardsimulator.ui.cards

import android.graphics.Typeface
import android.widget.*
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.UiKit

/** 详情展示元数据和可展开规则，原始 JSON 作为辅助视图。 */
class CardDetailPage(private val ui: UiKit) {
    fun show(content: LinearLayout, card: CardScript, edit: () -> Unit) = with(ui) {
        val body = column()
        content.addView(ScrollView(context).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        panel(body).apply {

            addView(label(card.scheme, 13f, muted))
            addView(label("AID\n" + card.aids.joinToString("\n"), 13f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) })
            CardDescription(ui).show(this, card.description)
            addView(row().apply {
                action(if (card.builtIn) "复制后编辑" else "编辑卡片", edit)
            })
        }
        CardScenarioPanel(ui).show(body, card)
        panel(body).apply {
            addView(row().apply {
                addView(label("APDU 规则 · ${card.rules.size} 条", 18f, ink, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(icon(com.iscolt.cardsimulator.R.drawable.ic_info, "执行方式说明") {
                    MatchingLabels.showExecutionHelp(ui)
                })
            })
            addView(label("${MatchingLabels.execution(card.ordered)} · 未匹配时返回 ${card.fallback}", 13f, muted))
            RuleList(ui).show(this, card.rules)
        }
    }
}
