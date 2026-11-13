package com.iscolt.cardsimulator.ui.cards

import android.widget.*
import com.iscolt.cardsimulator.core.CardScript
import com.iscolt.cardsimulator.core.CardPack
import com.iscolt.cardsimulator.data.CardSchemeStore
import com.iscolt.cardsimulator.ui.UiKit

/** 主表单不保存业务数据，通过 snapshot 返回工作副本，规则编辑与字段分开维护。 */
class CardEditorPage(private val ui: UiKit, private val schemes: CardSchemeStore) {
    private lateinit var snapshotFields: () -> CardScript
    private var error: TextView? = null
    fun snapshot() = snapshotFields()
    fun error(message: String) {
        error?.text = message; error?.visibility = android.view.View.VISIBLE
        (error?.parent?.parent as? ScrollView)?.smoothScrollTo(0, 0)
        Toast.makeText(ui.context, message, Toast.LENGTH_LONG).show()
    }
    fun show(parent: LinearLayout, card: CardScript, packs: List<CardPack>, editRule: (Int) -> Unit, changedRules: (CardScript) -> Unit) = with(ui) {
        val body = column().apply { setPadding(0, dp(12), 0, dp(20)) }
        parent.addView(ScrollView(context).apply { isFillViewport = true; addView(body) }, LinearLayout.LayoutParams(-1, -1))
        error = label("", 13f, android.graphics.Color.rgb(185, 28, 28)); body.addView(error); error?.visibility = android.view.View.GONE
        val basic = panel(body); basic.addView(label("基本信息", 16f, ink, true))
        val name = editorField(basic, "卡片名称", card.name)
        val pack = CardPackSelector(ui, basic, packs, card.packId)
        val scheme = SchemeSelector(ui, basic, schemes, card.scheme)
        val aids = editorField(basic, "AID（每行一个，5–16 字节）", card.aids.joinToString("\n"), true)
        val description = editorField(basic, "说明", card.description, true)
        val execution = panel(body); execution.addView(label("执行设置", 16f, ink, true))
        val ordered = ExecutionSelector(ui, execution, card.ordered)
        val fallback = editorField(execution, "未匹配回复（含 SW1/SW2）", card.fallback)
        execution.addView(label(MatchingLabels.fixedReply, 12f, muted))
        val rules = panel(body); rules.addView(label("APDU 规则 · "+card.rules.size, 16f, ink, true))
        card.rules.forEachIndexed { index, rule ->
            val row = row().apply { minimumHeight = dp(56) }
            row.addView(label("${index + 1}. ${rule.label.ifBlank { "未命名规则" }}\n${MatchingLabels.mode(rule.mode)}", 14f).apply {
                contentDescription = "编辑规则 "+(index+1); isFocusable = true
                setOnClickListener { editRule(index) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(icon(com.iscolt.cardsimulator.R.drawable.ic_more, "规则操作 "+(index+1)) { anchor ->
                PopupMenu(context, anchor).apply {
                    menu.add("编辑规则").setOnMenuItemClickListener { editRule(index); true }
                    menu.add("上移").apply { isEnabled = index > 0; setOnMenuItemClickListener {
                        val current = snapshot(); val updated = current.rules.toMutableList()
                        java.util.Collections.swap(updated, index, index-1); changedRules(current.copy(rules = updated)); true
                    } }
                    menu.add("下移").apply { isEnabled = index < card.rules.lastIndex; setOnMenuItemClickListener {
                        val current = snapshot(); val updated = current.rules.toMutableList()
                        java.util.Collections.swap(updated, index, index+1); changedRules(current.copy(rules = updated)); true
                    } }
                    menu.add("移除").setOnMenuItemClickListener {
                        android.app.AlertDialog.Builder(context).setTitle("移除此规则？")
                            .setMessage(rule.label.ifBlank { "规则 "+(index+1) }).setNegativeButton("取消", null)
                            .setPositiveButton("移除") { _, _ -> val current = snapshot()
                                changedRules(current.copy(rules = current.rules.filterIndexed { i, _ -> i != index })) }.show(); true
                    }; show()
                }
            }); rules.addView(row)
        }
        rules.addView(row().apply { action("添加规则") { editRule(-1) } })
        val scenario = ScenarioEditor(ui, panel(body), card.metadata.scenario)

        snapshotFields = { card.copy(name = name.text.toString(), scheme = scheme.value(), packId = pack.value(),
            aids = aids.text.toString().lines().filter { it.isNotBlank() }, description = description.text.toString(),
            ordered = ordered.value(), fallback = fallback.text.toString(),
            metadata = card.metadata.copy(scenario = scenario.value())) }
    }
}
