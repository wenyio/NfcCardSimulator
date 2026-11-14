package com.iscolt.cardsimulator.ui.cards

import android.graphics.Typeface
import android.widget.*
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R

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
                action(context.getString(if (card.builtIn) R.string.card_copy_edit else R.string.card_edit), edit)
            })
        }
        CardScenarioPanel(ui).show(body, card)
        panel(body).apply {
            addView(row().apply {
                addView(label(context.getString(R.string.detail_rule_count, card.rules.size), 18f, ink, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(icon(R.drawable.ic_info, context.getString(R.string.detail_execution_help)) {
                    MatchingLabels.showExecutionHelp(ui)
                })
            })
            addView(label(context.getString(R.string.detail_fallback, MatchingLabels.execution(context, card.ordered), card.fallback), 13f, muted))
            RuleList(ui).show(this, card.rules)
        }
    }
}
