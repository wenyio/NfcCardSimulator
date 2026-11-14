package com.iscolt.cardsimulator.ui.cards

import android.view.View
import android.widget.*
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R

/** 规则使用独立页面，长 APDU 可滚动编辑，只有保存后才写回卡片草稿。 */
class RuleEditorPage(private val ui: UiKit) {
    private lateinit var readFields: () -> ApduRule
    private lateinit var error: TextView
    /** 读取规则表单原始值，允许未完成输入；调用前必须先 show。 */
    fun snapshot() = readFields()
    /** 显示规则校验错误并滚动提示，保留命令、回复和掩码输入。 */
    fun error(message: String) = error.showEditorError(message)
    /** 构建独立规则表单及模式说明，绑定原始值快照读取；切换模式不丢弃未保存掩码。 */
    fun show(parent: LinearLayout, rule: ApduRule) = with(ui) {
        val body = column().apply { setPadding(0, dp(12), 0, dp(20)) }
        parent.addView(ScrollView(context).apply { addView(body) }, LinearLayout.LayoutParams(-1, -1))
        error = label("", 13f, android.graphics.Color.rgb(185, 28, 28)); body.addView(error); error.visibility = android.view.View.GONE
        val fields = panel(body)
        val title = editorField(fields, context.getString(R.string.rule_label), rule.label)
        val command = editorField(fields, context.getString(R.string.rule_command), rule.command, true)
        val response = editorField(fields, context.getString(R.string.rule_response), rule.response, true)
        fields.addView(label(context.getString(R.string.rule_match_mode), 13f, muted))
        val mode = Spinner(context).apply {
            contentDescription = context.getString(R.string.rule_match_mode)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, MatchMode.entries.map { MatchingLabels.mode(context, it) })
            setSelection(rule.mode.ordinal); fields.addView(this)
        }
        val help = label(MatchingLabels.modeHelp(context, rule.mode), 13f, muted)
        fields.addView(help)
        // 标签和输入框整体显隐，切换模式时仍保留未保存的掩码，便于切回继续编辑。
        val maskGroup = column()
        fields.addView(maskGroup)
        val mask = editorField(maskGroup, context.getString(R.string.rule_mask), rule.mask, true)
        val warning = label(MatchingLabels.fixedReply(context), 12f, muted)
        fields.addView(warning)
        /** 按选择更新帮助及掩码区域可见性；仅隐藏控件，不清理其中输入。 */
        fun updateMode(position: Int) {
            val selected = MatchMode.entries[position]
            help.text = MatchingLabels.modeHelp(context, selected)
            maskGroup.visibility = if (selected == MatchMode.MASK) View.VISIBLE else View.GONE
            warning.visibility = if (selected == MatchMode.EXACT) View.GONE else View.VISIBLE
        }
        updateMode(rule.mode.ordinal)
        mode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            /** 下拉模式改变时同步说明和输入区域，不立即提交规则。 */
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateMode(position)
            /** 无选中事件时不更改当前模式或输入。 */
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        readFields = { ApduRule(command.text.toString(), response.text.toString(), MatchMode.entries[mode.selectedItemPosition], mask.text.toString(), title.text.toString()) }
    }
    /** 用临时单卡容器检查导出大小，再直接校验规则；返回规范化结果，不保存卡片。 */
    fun validated(): ApduRule {
        val wrapper = CardScript("validate", "校验", "其他", listOf("F001020304"), "", listOf(snapshot()))
        ScriptCodec.checkSize(ScriptCodec.encode(listOf(wrapper)))
        return ScriptValidation.rule(wrapper.rules.single())
    }
}
