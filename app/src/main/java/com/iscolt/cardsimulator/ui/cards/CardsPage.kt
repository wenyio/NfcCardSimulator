package com.iscolt.cardsimulator.ui.cards

import android.view.View
import android.widget.*
import com.iscolt.cardsimulator.core.CardScript
import com.iscolt.cardsimulator.core.CardSchemes
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.ui.swipePages
import com.iscolt.cardsimulator.R

/** 列表只展示识别信息，整张卡进入详情，低频操作收进菜单。 */
class CardsPage(
    private val ui: UiKit, private val store: SimulatorStore,
    private val open: (CardScript) -> Unit,
    private val more: (View, CardScript) -> Unit,
    private val create: () -> Unit
) {
    private var filter = "全部"
    private var packId = ""

    /** 按当前包生成组织筛选页；包改变时重置为全部分类，页面切换只更新筛选。 */
    fun show(content: LinearLayout) = with(ui) {
        if (packId != store.activePackId) {
            packId = store.activePackId; filter = "全部"
        }
        val schemes = CardSchemes.categories(store.currentCards().map { it.scheme })
        val labels =
            schemes.map { if (it == "全部") context.getString(R.string.category_all) else it }
        swipePages(
            content, labels, schemes.indexOf(filter).coerceAtLeast(0), scrollableTabs = true,
            onSelected = { filter = schemes[it] }) { index, page ->
            showCards(page, schemes[index])
        }
    }

    /** 显示指定分类卡片或空包引导，点击打开详情，更多按钮交给共用卡片菜单。 */
    private fun showCards(content: LinearLayout, category: String) = with(ui) {
        val list = column()
        content.addView(
            ScrollView(context).apply { addView(list) },
            LinearLayout.LayoutParams(-1, 0, 1f)
        )
        val cards =
            store.currentCards().filter { category == "全部" || it.scheme.equals(category, true) }
        if (cards.isEmpty()) {
            if (!store.currentPack().builtIn) {
                // 空包引导直接添加卡片；文件导入会创建或更新整包，由顶部入口提供。
                panel(list).apply {
                    addView(label(context.getString(R.string.cards_empty_pack), 16f, ink, true))
                    addView(row().apply {
                        action(
                            context.getString(R.string.action_new_card),
                            create
                        )
                    })
                }
            } else list.addView(label(context.getString(R.string.cards_empty)))
        }
        cards.forEach { card ->
            panel(list).apply {
                val heading = row()
                heading.addView(
                    label(card.name, 18f, ink, true),
                    LinearLayout.LayoutParams(0, -2, 1f)
                )
                heading.addView(
                    icon(
                        R.drawable.ic_more,
                        context.getString(R.string.card_more, card.name)
                    ) { more(it, card) })
                addView(heading)
                addView(
                    label(
                        context.getString(R.string.card_rules, card.scheme, card.rules.size),
                        13f,
                        muted
                    )
                )
                contentDescription = context.getString(R.string.card_open, card.name)
                isFocusable = true; setOnClickListener { open(card) }
            }
        }
    }
}
