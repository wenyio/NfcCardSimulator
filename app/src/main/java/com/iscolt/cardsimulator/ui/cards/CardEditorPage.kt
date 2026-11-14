package com.iscolt.cardsimulator.ui.cards

import android.widget.*
import com.iscolt.cardsimulator.core.CardScript
import com.iscolt.cardsimulator.core.CardPack
import com.iscolt.cardsimulator.data.CardSchemeStore
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R

/** 主表单不保存业务数据，通过 snapshot 返回工作副本，规则编辑与字段分开维护。 */
class CardEditorPage(private val ui: UiKit, private val schemes: CardSchemeStore) {
    private lateinit var snapshotFields: () -> CardScript
    private lateinit var error: TextView

    /** 从已构造表单读取卡片工作副本，不校验、不写盘；调用前必须先 show。 */
    fun snapshot() = snapshotFields()

    /** 在表单顶部显示错误并滚动提示，保留当前所有输入。 */
    fun error(message: String) = error.showEditorError(message)

    /** 构建卡片字段、执行方式和规则列表，并保存读取字段的闭包；规则增删重排先读取最新表单。 */
    fun show(
        parent: LinearLayout,
        card: CardScript,
        packs: List<CardPack>,
        editRule: (Int) -> Unit,
        changedRules: (CardScript) -> Unit
    ) = with(ui) {
        val body = column().apply { setPadding(0, dp(12), 0, dp(20)) }
        parent.addView(
            ScrollView(context).apply { isFillViewport = true; addView(body) },
            LinearLayout.LayoutParams(-1, -1)
        )
        error = label(
            "",
            13f,
            android.graphics.Color.rgb(185, 28, 28)
        ); body.addView(error); error.visibility = android.view.View.GONE
        val basic = panel(body); basic.addView(
        label(
            context.getString(R.string.editor_basic),
            16f,
            ink,
            true
        )
    )
        val name = editorField(basic, context.getString(R.string.editor_name), card.name)
        val pack = CardPackSelector(ui, basic, packs, card.packId)
        val scheme = SchemeSelector(ui, basic, schemes, card.scheme)
        val aids = editorField(
            basic,
            context.getString(R.string.editor_aids),
            card.aids.joinToString("\n"),
            true
        )
        val description = editorField(
            basic,
            context.getString(R.string.editor_description),
            card.description,
            true
        )
        val execution = panel(body); execution.addView(
        label(
            context.getString(R.string.editor_execution),
            16f,
            ink,
            true
        )
    )
        val ordered = ExecutionSelector(ui, execution, card.ordered)
        val fallback =
            editorField(execution, context.getString(R.string.editor_fallback), card.fallback)
        execution.addView(label(MatchingLabels.fixedReply(context), 12f, muted))
        val rules = panel(body); rules.addView(
        label(
            context.getString(
                R.string.editor_rules,
                card.rules.size
            ), 16f, ink, true
        )
    )
        card.rules.forEachIndexed { index, rule ->
            val row = row().apply { minimumHeight = dp(56) }
            row.addView(
                label(
                    "${index + 1}. ${rule.label.ifBlank { context.getString(R.string.rule_unnamed) }}\n${
                        MatchingLabels.mode(
                            context,
                            rule.mode
                        )
                    }", 14f
                ).apply {
                    contentDescription =
                        context.getString(R.string.rule_edit_number, index + 1); isFocusable = true
                    setOnClickListener { editRule(index) }
                }, LinearLayout.LayoutParams(0, -2, 1f)
            )
            row.addView(
                icon(
                    R.drawable.ic_more,
                    context.getString(R.string.rule_actions_number, index + 1)
                ) { anchor ->
                    PopupMenu(context, anchor).apply {
                        menu.add(R.string.rule_edit)
                            .setOnMenuItemClickListener { editRule(index); true }
                        menu.add(R.string.action_move_up).apply {
                            isEnabled = index > 0; setOnMenuItemClickListener {
                            val current = snapshot();
                            val updated = current.rules.toMutableList()
                            java.util.Collections.swap(
                                updated,
                                index,
                                index - 1
                            ); changedRules(current.copy(rules = updated)); true
                        }
                        }
                        menu.add(R.string.action_move_down).apply {
                            isEnabled = index < card.rules.lastIndex; setOnMenuItemClickListener {
                            val current = snapshot();
                            val updated = current.rules.toMutableList()
                            java.util.Collections.swap(
                                updated,
                                index,
                                index + 1
                            ); changedRules(current.copy(rules = updated)); true
                        }
                        }
                        menu.add(R.string.action_remove).setOnMenuItemClickListener {
                            android.app.AlertDialog.Builder(context)
                                .setTitle(R.string.rule_remove_confirm)
                                .setMessage(rule.label.ifBlank {
                                    context.getString(
                                        R.string.rule_number,
                                        index + 1
                                    )
                                }).setNegativeButton(R.string.action_cancel, null)
                                .setPositiveButton(R.string.action_remove) { _, _ ->
                                    val current = snapshot()
                                    changedRules(current.copy(rules = current.rules.filterIndexed { i, _ -> i != index }))
                                }.show(); true
                        }; show()
                    }
                }); rules.addView(row)
        }
        rules.addView(row().apply { action(context.getString(R.string.rule_add)) { editRule(-1) } })
        val scenario = ScenarioEditor(ui, panel(body), card.metadata.scenario)

        snapshotFields = {
            card.copy(
                name = name.text.toString(),
                scheme = scheme.value(),
                packId = pack.value(),
                aids = aids.text.toString().lines().filter { it.isNotBlank() },
                description = description.text.toString(),
                ordered = ordered.value(),
                fallback = fallback.text.toString(),
                metadata = card.metadata.copy(scenario = scenario.value())
            )
        }
    }
}
