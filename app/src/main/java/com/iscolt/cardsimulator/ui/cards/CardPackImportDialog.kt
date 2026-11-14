package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.app.Activity
import android.widget.*
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.ui.UiKit
import java.util.UUID

/** 文件导入总是先预览；同编号只提示更新，不根据 ID 静默覆盖。 */
class CardPackImportDialog(private val ui: UiKit, private val store: SimulatorStore,
    private val imported: () -> Unit, private val message: (String) -> Unit) {
    fun show(pack: CardPack) {
        val existing = store.packs().filter { !it.builtIn && it.id != CardPacks.PERSONAL }
        val choices = arrayOf("新建卡包") + existing.map { "更新：" + it.name }.toTypedArray()
        val matched = existing.indexOfFirst { it.id == pack.id }
        var target = if (matched >= 0) matched + 1 else 0
        val dialog = AlertDialog.Builder(ui.context).setTitle("导入卡包 · " + pack.cards.size + " 张")
            .setSingleChoiceItems(choices, target) { _, index -> target = index }
            .setPositiveButton("下一步") { _, _ -> preview(pack, existing.getOrNull(target - 1)) }
            .setNegativeButton("取消", null).create()
        dialog.show()
    }
    private fun preview(incoming: CardPack, old: CardPack?) = with(ui) {
        val body = column().apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val name = EditText(context).apply { setText(incoming.name); hint = "卡包名称"; isSingleLine = true }; body.addView(name)
        val oldIds = old?.cards?.map { it.id }?.toSet().orEmpty()
        val newIds = incoming.cards.map { it.id }.toSet()
        val change = if (old == null) "建立独立卡包，不影响已有卡片。" else
            "整包替换：新增 " + (newIds - oldIds).size + "，更新 " + (newIds intersect oldIds).size +
                "，移除 " + (oldIds - newIds).size + " 张。"
        body.addView(label(change, 14f))
        body.addView(label(incoming.cards.joinToString("\n") { it.name }.take(1600), 13f))
        val warnings = incoming.cards.flatMap { it.metadata.warnings }.distinct()
        if (warnings.isNotEmpty()) body.addView(label(warnings.joinToString("\n").take(2000), 12f, muted))
        val dialog = AlertDialog.Builder(context).setTitle(if (old == null) "新建卡包" else "更新：" + old.name)
            .setView(ScrollView(context).apply { addView(body) }).setNegativeButton("取消", null)
            .setPositiveButton("确认导入", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = name.text.toString()
            PackWork.run(context as Activity, {
                val id = if (old == null && store.packs().any { it.id == incoming.id }) "pack-" + UUID.randomUUID() else incoming.id
                val pack = incoming.copy(id = id, name = CardPacks.validateName(value))
                store.importPack(pack, old?.id)
            }) { result ->
                result.onSuccess { dialog.dismiss(); imported() }
                    .onFailure { name.error = it.message; message(it.message ?: "导入失败") }
            }
        } }; dialog.show()
    }
}
