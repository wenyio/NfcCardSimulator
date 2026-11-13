package com.iscolt.cardsimulator.ui.cards

import android.view.View
import android.widget.*
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.UiKit

/** 规则使用独立页面，长 APDU 可滚动编辑，只有保存后才写回卡片草稿。 */
class RuleEditorPage(private val ui: UiKit) {
    private lateinit var readFields: () -> ApduRule
    private var error: TextView? = null
    fun snapshot() = readFields()
    fun error(message: String) {
        error?.text = message; error?.visibility = android.view.View.VISIBLE
        (error?.parent?.parent as? ScrollView)?.smoothScrollTo(0, 0)
        Toast.makeText(ui.context, message, Toast.LENGTH_LONG).show()
    }
    fun show(parent: LinearLayout, rule: ApduRule) = with(ui) {
        val body = column().apply { setPadding(0, dp(12), 0, dp(20)) }
        parent.addView(ScrollView(context).apply { addView(body) }, LinearLayout.LayoutParams(-1, -1))
        error = label("", 13f, android.graphics.Color.rgb(185, 28, 28)); body.addView(error); error?.visibility = android.view.View.GONE
        val fields = panel(body)
        val title = editorField(fields, "规则说明", rule.label)
        val command = editorField(fields, "命令 C-APDU（十六进制）", rule.command, true)
        val response = editorField(fields, "回复 R-APDU（包含 SW1/SW2）", rule.response, true)
        fields.addView(label("匹配方式", 13f, muted))
        val mode = Spinner(context).apply {
            contentDescription = "匹配方式"
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, MatchMode.entries.map(MatchingLabels::mode))
            setSelection(rule.mode.ordinal); fields.addView(this)
        }
        val help = label(MatchingLabels.modeHelp(rule.mode), 13f, muted)
        fields.addView(help)
        // 标签和输入框整体显隐，切换模式时仍保留未保存的掩码，便于切回继续编辑。
        val maskGroup = column()
        fields.addView(maskGroup)
        val mask = editorField(maskGroup, "掩码（与命令等长）", rule.mask, true)
        val warning = label(MatchingLabels.fixedReply, 12f, muted)
        fields.addView(warning)
        fun updateMode(position: Int) {
            val selected = MatchMode.entries[position]
            help.text = MatchingLabels.modeHelp(selected)
            maskGroup.visibility = if (selected == MatchMode.MASK) View.VISIBLE else View.GONE
            warning.visibility = if (selected == MatchMode.EXACT) View.GONE else View.VISIBLE
        }
        updateMode(rule.mode.ordinal)
        mode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateMode(position)
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        readFields = { ApduRule(command.text.toString(), response.text.toString(), MatchMode.entries[mode.selectedItemPosition], mask.text.toString(), title.text.toString()) }
    }
    fun validated(): ApduRule {
        val wrapper = CardScript("validate", "校验", "其他", listOf("F001020304"), "", listOf(snapshot()))
        return ScriptCodec.parse(ScriptCodec.encode(listOf(wrapper))).single().rules.single()
    }
}
