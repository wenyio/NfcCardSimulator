package com.iscolt.cardsimulator.ui.cards

import android.widget.*
import com.iscolt.cardsimulator.core.CardPack
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R

/** 只允许选择用户卡包；按稳定 ID 取值，避免同名卡包归属错误。 */
class CardPackSelector(
    ui: UiKit,
    parent: LinearLayout,
    private val packs: List<CardPack>,
    initial: String
) {
    private val spinner = Spinner(ui.context)

    init {
        parent.addView(ui.label(ui.context.getString(R.string.pack_membership), 13f, ui.muted))
        spinner.contentDescription = ui.context.getString(R.string.pack_membership)
        spinner.adapter = ArrayAdapter(
            ui.context,
            android.R.layout.simple_spinner_dropdown_item,
            packs.map { it.name })
        spinner.setSelection(packs.indexOfFirst { it.id == initial }.coerceAtLeast(0))
        parent.addView(spinner, LinearLayout.LayoutParams(-1, ui.dp(48)))
    }

    /** 返回当前选项对应的稳定包 ID，不使用可能重名的显示名称识别归属。 */
    fun value(): String = packs[spinner.selectedItemPosition].id
}
