package com.iscolt.cardsimulator.ui.debug

import android.view.View
import android.widget.*
import com.iscolt.cardsimulator.core.ApduRule
import com.iscolt.cardsimulator.ui.UiKit

/** 选择器只填充输入框；发送动作及顺序游标始终由调试页和匹配引擎负责。 */
class DebugCommandSelector(ui: UiKit, parent: LinearLayout, rules: List<ApduRule>, input: EditText) {
    init {
        parent.addView(ui.label("选择命令", 13f, ui.muted))
        input.setText(rules.firstOrNull()?.command.orEmpty())
        val spinner = Spinner(ui.context, Spinner.MODE_DROPDOWN).apply {
            contentDescription = "选择 APDU 命令"
            isEnabled = rules.isNotEmpty()
            val labels = rules.mapIndexed(DebugCommandLabel::format).ifEmpty { listOf("当前卡片暂无 APDU 规则") }
            adapter = ArrayAdapter(ui.context, android.R.layout.simple_spinner_item, labels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        parent.addView(spinner, LinearLayout.LayoutParams(-1, ui.dp(48)))
        // 首项已同步填充；忽略首次布局回调，避免覆盖用户随后输入的内容。
        var selected = 0
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != selected) {
                    selected = position
                    input.setText(rules.getOrNull(position)?.command.orEmpty())
                    input.setSelection(input.length())
                }
            }
        }
    }
}
