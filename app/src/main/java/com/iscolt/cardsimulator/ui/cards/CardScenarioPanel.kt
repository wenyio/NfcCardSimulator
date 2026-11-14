package com.iscolt.cardsimulator.ui.cards

import android.widget.LinearLayout
import com.iscolt.cardsimulator.core.CardScript
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R

/** 场景正文只显示有效摘要，参考参数与脚本说明按需展开。 */
class CardScenarioPanel(private val ui: UiKit) {
    fun show(parent: LinearLayout, card: CardScript) = with(ui) {
        val metadata = card.metadata
        val scenario = metadata.scenario
        // 原场景名与卡名相同时不重复展示；独立填写的测试目的继续保留。
        val purpose = scenario.purpose.takeUnless {
            it.removePrefix("原场景：").trim() in listOf(card.name.trim(), card.name.substringAfter(" · ").trim())
        }.orEmpty()
        val summary = listOf(scenario.category, purpose).filter { it.isNotBlank() }.joinToString(" · ")
        val conditions = listOf(
            context.getString(R.string.scenario_conditions) to scenario.conditions,
            context.getString(R.string.scenario_original_amount) to scenario.amount,
            context.getString(R.string.scenario_legacy_pdol) to scenario.legacyPdol,
            context.getString(R.string.scenario_legacy_cdol) to scenario.legacyCdol,
            context.getString(R.string.scenario_original_id) to if (card.builtIn) "" else metadata.source.originalCardId,
            context.getString(R.string.scenario_reference) to metadata.source.configId
        ).filter { it.second.isNotBlank() }
        val notes = metadata.warnings.filter { it.isNotBlank() }
        if (summary.isBlank() && scenario.expectedResult.isBlank() && conditions.isEmpty() && notes.isEmpty()) return@with
        panel(parent).apply {
            addView(label(context.getString(R.string.scenario_title), 18f, ink, true))
            if (summary.isNotBlank()) CardDescription(ui).show(this, summary)
            if (scenario.expectedResult.isNotBlank()) CardDescription(ui).show(this, context.getString(R.string.scenario_expected, scenario.expectedResult))
            if (conditions.isNotEmpty()) {
                val details = expandableSection(ui, this, context.getString(R.string.scenario_run_conditions))
                conditions.forEach { (title, value) ->
                    details.addView(label(title + "\n" + value, 13f).apply { setTextIsSelectable(true) })
                }
            }
            // 只在有内容时提供入口，避免空面板；转换差异不挤占常用参数。
            if (notes.isNotEmpty()) {
                val details = expandableSection(ui, this, context.getString(R.string.scenario_script_notes))
                details.addView(label(notes.joinToString("\n"), 13f).apply { setTextIsSelectable(true) })
            }
        }
    }
}
