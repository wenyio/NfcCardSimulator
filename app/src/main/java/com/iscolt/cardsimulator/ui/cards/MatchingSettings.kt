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
    fun fixedReply(context: Context) = context.getString(R.string.match_fixed_reply)
    fun execution(context: Context, ordered: Boolean) = context.getString(if (ordered) R.string.execution_ordered else R.string.execution_content)
    fun executionHelp(context: Context, ordered: Boolean) = context.getString(if (ordered) R.string.execution_ordered_help else R.string.execution_content_help)
    fun mode(context: Context, mode: MatchMode) = context.getString(when (mode) {
        MatchMode.EXACT -> R.string.match_exact
        MatchMode.PREFIX -> R.string.match_prefix
        MatchMode.MASK -> R.string.match_mask
    })
    fun modeHelp(context: Context, mode: MatchMode) = context.getString(when (mode) {
        MatchMode.EXACT -> R.string.match_exact_help
        MatchMode.PREFIX -> R.string.match_prefix_help
        MatchMode.MASK -> R.string.match_mask_help
    })
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
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    help.text = MatchingLabels.executionHelp(context, position == 1)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }
    fun value() = spinner.selectedItemPosition == 1
}
