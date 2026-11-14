package com.iscolt.cardsimulator.ui.cards
import android.widget.LinearLayout
import com.iscolt.cardsimulator.core.TestScenario
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R
/** 编辑场景说明；来源和旧 DOL 数据保留原值，不与用户说明混写。 */
class ScenarioEditor(private val ui: UiKit, parent: LinearLayout, private val original: TestScenario) {
    private val fields = mutableListOf<android.widget.EditText>()
    init {
        with(ui) {
            val body = expandableSection(ui, parent, context.getString(R.string.scenario_optional))
            listOf(context.getString(R.string.scenario_category) to original.category, context.getString(R.string.scenario_purpose) to original.purpose,
                context.getString(R.string.scenario_result) to original.expectedResult, context.getString(R.string.scenario_conditions) to original.conditions,
                context.getString(R.string.scenario_amount) to original.amount).forEach { (title, value) ->
                body.addView(label(title, 13f, muted))
                fields.add(android.widget.EditText(context).apply { hint = title; setText(value); body.addView(this) })
            }
        }
    }
    fun value() = original.copy(category = fields[0].text.toString(), purpose = fields[1].text.toString(),
        expectedResult = fields[2].text.toString(), conditions = fields[3].text.toString(), amount = fields[4].text.toString())
}
