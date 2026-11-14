package com.iscolt.cardsimulator.ui.debug

import android.view.View
import android.widget.*
import com.iscolt.cardsimulator.core.ApduRule
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R

/** 选择器只填充输入框；发送动作及顺序游标始终由调试页和匹配引擎负责。 */
class DebugCommandSelector(
    ui: UiKit,
    parent: LinearLayout,
    rules: List<ApduRule>,
    input: EditText
) {
    init {
        parent.addView(ui.label(ui.context.getString(R.string.debug_choose_command), 13f, ui.muted))
        input.setText(rules.firstOrNull()?.command.orEmpty())
        val spinner = Spinner(ui.context, Spinner.MODE_DROPDOWN).apply {
            contentDescription = ui.context.getString(R.string.debug_choose_apdu)
            isEnabled = rules.isNotEmpty()
            val labels = rules.mapIndexed(DebugCommandLabel::format)
                .ifEmpty { listOf(ui.context.getString(R.string.debug_no_rules)) }
            adapter = ArrayAdapter(ui.context, android.R.layout.simple_spinner_item, labels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        parent.addView(spinner, LinearLayout.LayoutParams(-1, ui.dp(48)))
        // 首项已同步填充；忽略首次布局回调，避免覆盖用户随后输入的内容。
        var selected = 0
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            /** 无选中事件时保留命令输入。 */
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit

            /** 只在选择位置变化时填入规则命令并移动光标，不发送命令、不改变执行步骤。 */
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position != selected) {
                    selected = position
                    input.setText(rules.getOrNull(position)?.command.orEmpty())
                    input.setSelection(input.length())
                }
            }
        }
    }
}
