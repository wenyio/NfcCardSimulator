package com.iscolt.cardsimulator.ui.cards

import android.widget.LinearLayout
import com.iscolt.cardsimulator.core.CardScript
import com.iscolt.cardsimulator.ui.UiKit

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
            "运行条件（仅说明）" to scenario.conditions,
            "参考金额（原配置单位）" to scenario.amount,
            "原 PDOL 数据（非 DOL 定义）" to scenario.legacyPdol,
            "原 CDOL 数据（非 DOL 定义）" to scenario.legacyCdol,
            "原卡片编号" to if (card.builtIn) "" else metadata.source.originalCardId,
            "参考配置" to metadata.source.configId
        ).filter { it.second.isNotBlank() }
        val notes = metadata.warnings.filter { it.isNotBlank() }
        if (summary.isBlank() && scenario.expectedResult.isBlank() && conditions.isEmpty() && notes.isEmpty()) return@with
        panel(parent).apply {
            addView(label("测试场景", 18f, ink, true))
            if (summary.isNotBlank()) CardDescription(ui).show(this, summary)
            if (scenario.expectedResult.isNotBlank()) CardDescription(ui).show(this, "预期结果：" + scenario.expectedResult)
            if (conditions.isNotEmpty()) {
                val details = expandableSection(ui, this, "运行条件")
                conditions.forEach { (title, value) ->
                    details.addView(label(title + "\n" + value, 13f).apply { setTextIsSelectable(true) })
                }
            }
            // 只在有内容时提供入口，避免空面板；转换差异不挤占常用参数。
            if (notes.isNotEmpty()) {
                val details = expandableSection(ui, this, "脚本说明")
                details.addView(label(notes.joinToString("\n"), 13f).apply { setTextIsSelectable(true) })
            }
        }
    }
}
