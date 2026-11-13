package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.view.View
import android.widget.*
import com.iscolt.cardsimulator.core.CardSchemes
import com.iscolt.cardsimulator.data.CardSchemeStore
import com.iscolt.cardsimulator.ui.UiKit

/** 固定选项和已导入组织共用同一下拉入口，添加失败时保留输入与错误。 */
class SchemeSelector(private val ui: UiKit, parent: LinearLayout,
    private val store: CardSchemeStore, initial: String) {
    private val spinner = Spinner(ui.context)
    private var selected = CardSchemes.canonical(initial, store.options()).ifBlank { "其他" }
    private var names = emptyList<String>()
    init {
        parent.addView(ui.label("卡组织", 13f, ui.muted))
        spinner.contentDescription = "卡组织"
        parent.addView(spinner, LinearLayout.LayoutParams(-1, ui.dp(48)))
        refresh()
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == names.size) { spinner.setSelection(names.indexOf(selected)); add() }
                else selected = names[position]
            }
        }
    }
    fun value() = selected
    private fun refresh() {
        names = CardSchemes.options(store.options() + selected)
        spinner.adapter = ArrayAdapter(ui.context, android.R.layout.simple_spinner_dropdown_item, names + "＋ 添加卡组织")
        spinner.setSelection(names.indexOf(selected))
    }
    private fun add() {
        val input = EditText(ui.context).apply { hint = "卡组织名称"; maxLines = 1 }
        val dialog = AlertDialog.Builder(ui.context).setTitle("添加卡组织").setView(input)
            .setNegativeButton("取消", null).setPositiveButton("添加", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            runCatching { store.add(input.text.toString()) }.onSuccess {
                selected = it; refresh(); dialog.dismiss()
            }.onFailure { input.error = it.message }
        }
    }
}
