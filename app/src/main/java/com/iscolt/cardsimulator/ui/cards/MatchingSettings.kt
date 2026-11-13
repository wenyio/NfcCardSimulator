package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.view.View
import android.widget.*
import com.iscolt.cardsimulator.core.MatchMode
import com.iscolt.cardsimulator.ui.UiKit

/** 仅统一界面文案；JSON 字段与引擎枚举保持不变。 */
internal object MatchingLabels {
    const val fixedReply = "回复仍为固定数据，不会重算密文或动态签名。"
    fun execution(ordered: Boolean) = if (ordered) "顺序匹配" else "内容匹配"
    fun executionHelp(ordered: Boolean) = if (ordered)
        "只检查当前步骤，匹配成功后进入下一条；未匹配不推进，全部完成后返回未匹配回复。"
        else "每次从头查找第一条符合条件的规则，不要求按固定顺序发送命令。"
    fun mode(mode: MatchMode) = when (mode) {
        MatchMode.EXACT -> "完整匹配"
        MatchMode.PREFIX -> "前缀匹配"
        MatchMode.MASK -> "掩码匹配"
    }
    fun modeHelp(mode: MatchMode) = when (mode) {
        MatchMode.EXACT -> "命令的每个字节和总长度都必须一致。"
        MatchMode.PREFIX -> "只比较规则中填写的命令前缀，后续内容和长度可以不同。"
        MatchMode.MASK -> "命令长度必须一致，按掩码逐位比较：FF 比较整个字节，00 忽略整个字节。"
    }
    fun showExecutionHelp(ui: UiKit) {
        AlertDialog.Builder(ui.context).setTitle("执行方式")
            .setMessage("内容匹配\n" + executionHelp(false) + "\n\n顺序匹配\n" + executionHelp(true) +
                "\n\n顺序匹配适合严格回放，也可让相同命令在不同步骤返回不同回复。会话重置后从第一条开始。")
            .setPositiveButton("知道了", null).show()
    }
}

/** 执行方式显式二选一；选择只进入草稿，保存卡片时才持久化。 */
internal class ExecutionSelector(ui: UiKit, parent: LinearLayout, ordered: Boolean) {
    private val spinner: Spinner
    init {
        with(ui) {
            parent.addView(label("执行方式", 13f, muted))
            spinner = Spinner(context).apply {
                contentDescription = "执行方式"
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                    listOf(MatchingLabels.execution(false), MatchingLabels.execution(true)))
                setSelection(if (ordered) 1 else 0)
            }
            parent.addView(spinner)
            val help = label(MatchingLabels.executionHelp(ordered), 12f, muted)
            parent.addView(help)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    help.text = MatchingLabels.executionHelp(position == 1)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }
    fun value() = spinner.selectedItemPosition == 1
}
