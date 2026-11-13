package com.iscolt.cardsimulator.ui.cards

import android.view.View
import android.widget.*
import com.iscolt.cardsimulator.core.CardScript
import com.iscolt.cardsimulator.core.CardSchemes
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.ui.swipePages

/** 列表只展示识别信息，整张卡进入详情，低频操作收进菜单。 */
class CardsPage(private val ui: UiKit, private val store: SimulatorStore,
    private val open: (CardScript) -> Unit,
    private val more: (View, CardScript) -> Unit,
    private val create: () -> Unit) {
    private var filter = "全部"
    private var packId = ""
    fun show(content: LinearLayout) = with(ui) {
        if (packId != store.activePackId) { packId = store.activePackId; filter = "全部" }
        val schemes = CardSchemes.categories(store.currentCards().map { it.scheme })
        swipePages(content, schemes, schemes.indexOf(filter).coerceAtLeast(0), scrollableTabs = true,
            onSelected = { filter = schemes[it] }) { index, page ->
            showCards(page, schemes[index])
        }
    }
    private fun showCards(content: LinearLayout, category: String) = with(ui) {
        val list = column()
        content.addView(ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val cards = store.currentCards().filter { category == "全部" || it.scheme.equals(category, true) }
        if (cards.isEmpty()) {
            if (!store.currentPack().builtIn) {
                // 空包引导直接添加卡片；文件导入会创建或更新整包，由顶部入口提供。
                panel(list).apply {
                    addView(label("此卡包暂无卡片", 16f, ink, true))
                    addView(row().apply { action("新建卡片", create) })
                }
            } else list.addView(label("暂无卡片，可新建或导入。"))
        }
        cards.forEach { card ->
            panel(list).apply {
                val heading = row()
                heading.addView(label(card.name, 18f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
                heading.addView(icon(com.iscolt.cardsimulator.R.drawable.ic_more, "更多操作：" + card.name) { more(it, card) })
                addView(heading)
                addView(label("${card.scheme} · ${card.rules.size} 条规则", 13f, muted))
                contentDescription = "打开卡片：" + card.name
                isFocusable = true; setOnClickListener { open(card) }
            }
        }
    }
}
