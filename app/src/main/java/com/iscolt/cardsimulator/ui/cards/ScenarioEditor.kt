package com.iscolt.cardsimulator.ui.cards
import android.widget.LinearLayout
import com.iscolt.cardsimulator.core.TestScenario
import com.iscolt.cardsimulator.ui.UiKit
/** 编辑场景说明；来源和旧 DOL 数据保留原值，不与用户说明混写。 */
class ScenarioEditor(private val ui: UiKit, parent: LinearLayout, private val original: TestScenario) {
    private val fields = mutableListOf<android.widget.EditText>()
    init {
        with(ui) {
            val body = expandableSection(ui, parent, "测试场景（可选）")
            listOf("测试分类" to original.category, "测试目的" to original.purpose,
                "预期终端结果" to original.expectedResult, "运行条件（仅说明）" to original.conditions,
                "参考金额（注明单位）" to original.amount).forEach { (title, value) ->
                body.addView(label(title, 13f, muted))
                fields.add(android.widget.EditText(context).apply { hint = title; setText(value); body.addView(this) })
            }
        }
    }
    fun value() = original.copy(category = fields[0].text.toString(), purpose = fields[1].text.toString(),
        expectedResult = fields[2].text.toString(), conditions = fields[3].text.toString(), amount = fields[4].text.toString())
}
