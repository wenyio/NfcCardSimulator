package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.view.View
import android.widget.*
import com.iscolt.cardsimulator.core.MatchMode
import com.iscolt.cardsimulator.ui.UiKit
import android.content.Context
import com.iscolt.cardsimulator.R

/** 仅统一界面文案；JSON 字段与引擎枚举保持不变。 */
internal object MatchingLabels {
    /** 读取固定回复限制的资源文案，不改变匹配配置。 */
    fun fixedReply(context: Context) = context.getString(R.string.match_fixed_reply)
    /** 按 ordered 读取顺序或内容模式名称；显示文字不作为持久化值。 */
    fun execution(context: Context, ordered: Boolean) = context.getString(if (ordered) R.string.execution_ordered else R.string.execution_content)
    /** 读取执行模式解释，帮助区分当前步骤匹配和首条内容匹配。 */
    fun executionHelp(context: Context, ordered: Boolean) = context.getString(if (ordered) R.string.execution_ordered_help else R.string.execution_content_help)
    /** 将稳定 MatchMode 枚举映射为 strings.xml 中的可显示名称。 */
    fun mode(context: Context, mode: MatchMode) = context.getString(when (mode) {
        MatchMode.EXACT -> R.string.match_exact
        MatchMode.PREFIX -> R.string.match_prefix
        MatchMode.MASK -> R.string.match_mask
    })
    /** 按匹配模式读取解释文案，枚举仍用于实际匹配和保存。 */
    fun modeHelp(context: Context, mode: MatchMode) = context.getString(when (mode) {
        MatchMode.EXACT -> R.string.match_exact_help
        MatchMode.PREFIX -> R.string.match_prefix_help
        MatchMode.MASK -> R.string.match_mask_help
    })
    /** 展示两种执行方式及边界的说明弹窗；查看说明不重置会话。 */
    fun showExecutionHelp(ui: UiKit) {
        val context = ui.context
        AlertDialog.Builder(context).setTitle(R.string.execution_title)
            .setMessage(execution(context, false) + "\n" + executionHelp(context, false) + "\n\n" + execution(context, true) + "\n" + executionHelp(context, true) + "\n\n" + context.getString(R.string.execution_help_detail))
            .setPositiveButton(R.string.sim_ok, null).show()
    }
}

/** 执行方式显式二选一；选择只进入草稿，保存卡片时才持久化。 */
internal class ExecutionSelector(ui: UiKit, parent: LinearLayout, ordered: Boolean) {
    private val spinner: Spinner
    init {
        with(ui) {
            parent.addView(label(context.getString(R.string.execution_title), 13f, muted))
            spinner = Spinner(context).apply {
                contentDescription = context.getString(R.string.execution_title)
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                    listOf(MatchingLabels.execution(context, false), MatchingLabels.execution(context, true)))
                setSelection(if (ordered) 1 else 0)
            }
            parent.addView(spinner)
            val help = label(MatchingLabels.executionHelp(context, ordered), 12f, muted)
            parent.addView(help)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                /** 按下拉位置更新执行方式帮助；这里只改变界面，保存时才读取选择。 */
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    help.text = MatchingLabels.executionHelp(context, position == 1)
                }
                /** 没有选中事件时无需修改草稿，保持已有选择。 */
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }
    /** 把下拉位置转换为 ordered 布尔值，第二项表示顺序执行。 */
    fun value() = spinner.selectedItemPosition == 1
}
