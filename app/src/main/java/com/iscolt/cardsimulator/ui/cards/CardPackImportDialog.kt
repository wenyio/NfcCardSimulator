package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.app.Activity
import android.widget.*
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R
import java.util.UUID

/** 文件导入总是先预览；同编号只提示更新，不根据 ID 静默覆盖。 */
class CardPackImportDialog(
    private val ui: UiKit, private val store: SimulatorStore,
    private val imported: () -> Unit, private val message: (String) -> Unit
) {
    /** 列出新建或可更新的用户包，按相同 ID 预选建议目标，下一步仍需预览确认。 */
    fun show(pack: CardPack) {
        val existing = store.packs().filter { !it.builtIn && it.id != CardPacks.PERSONAL }
        val choices = arrayOf(ui.context.getString(R.string.pack_new)) + existing.map {
            ui.context.getString(
                R.string.pack_update_choice,
                it.name
            )
        }.toTypedArray()
        val matched = existing.indexOfFirst { it.id == pack.id }
        var target = if (matched >= 0) matched + 1 else 0
        val dialog = AlertDialog.Builder(ui.context)
            .setTitle(ui.context.getString(R.string.pack_import_title, pack.cards.size))
            .setSingleChoiceItems(choices, target) { _, index -> target = index }
            .setPositiveButton(R.string.action_next) { _, _ ->
                preview(
                    pack,
                    existing.getOrNull(target - 1)
                )
            }
            .setNegativeButton(R.string.action_cancel, null).create()
        dialog.show()
    }

    /**
     * 显示整包替换的新增、同 ID 和移除数量；确认后后台提交。
     * 新建时遇到已有包 ID 会生成新 ID，更新时明确使用选定目标，失败不关闭表单。
     */
    private fun preview(incoming: CardPack, old: CardPack?) = with(ui) {
        val body = column().apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val name = EditText(context).apply {
            setText(incoming.name); hint = context.getString(R.string.pack_name); isSingleLine =
            true
        }; body.addView(name)
        val oldIds = old?.cards?.map { it.id }?.toSet().orEmpty()
        val newIds = incoming.cards.map { it.id }.toSet()
        val change = if (old == null) context.getString(R.string.pack_new_explanation) else
            context.getString(
                R.string.pack_replace_summary,
                (newIds - oldIds).size,
                (newIds intersect oldIds).size,
                (oldIds - newIds).size
            )
        body.addView(label(change, 14f))
        body.addView(label(incoming.cards.joinToString("\n") { it.name }.take(1600), 13f))
        val warnings = incoming.cards.flatMap { it.metadata.warnings }.distinct()
        if (warnings.isNotEmpty()) body.addView(
            label(
                warnings.joinToString("\n").take(2000),
                12f,
                muted
            )
        )
        val dialog = AlertDialog.Builder(context).setTitle(
            if (old == null) context.getString(R.string.pack_new) else context.getString(
                R.string.pack_update_choice,
                old.name
            )
        )
            .setView(ScrollView(context).apply { addView(body) })
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm_import, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = name.text.toString()
                PackWork.run(context as Activity, {
                    val id = if (old == null && store.packs()
                            .any { it.id == incoming.id }
                    ) "pack-" + UUID.randomUUID() else incoming.id
                    val pack = incoming.copy(id = id, name = CardPacks.validateName(value))
                    store.importPack(pack, old?.id)
                }) { result ->
                    result.onSuccess { dialog.dismiss(); imported() }
                        .onFailure {
                            name.error = it.message; message(
                            it.message ?: context.getString(R.string.import_failed)
                        )
                        }
                }
            }
        }; dialog.show()
    }
}
