package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.app.Activity
import android.view.View
import android.widget.*
import com.iscolt.cardsimulator.ui.JsonViewer
import com.iscolt.cardsimulator.core.CardPackCodec
import com.iscolt.cardsimulator.core.CardPack
import com.iscolt.cardsimulator.core.CardPacks
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R

/** 管理操作直接从行尾菜单触发；写入成功才刷新，失败保留原页面和输入。 */
class CardPackActions(private val ui: UiKit, private val store: SimulatorStore,
    private val changed: () -> Unit, private val export: (CardPack) -> Unit, private val message: (String) -> Unit) {
    fun show(anchor: View, pack: CardPack) {
        PopupMenu(ui.context, anchor).apply {
            menu.add(R.string.action_view_json).setOnMenuItemClickListener { JsonViewer(ui).show(ui.context.getString(R.string.pack_json), CardPackCodec.encode(pack)); true }
            menu.add(R.string.pack_export).setOnMenuItemClickListener { export(pack); true }
            // 权限与存储层保持一致：内置只读，自建卡片可重命名但不能删除。
            if (!pack.builtIn) menu.add(R.string.pack_rename).setOnMenuItemClickListener { rename(pack); true }
            if (!pack.builtIn && pack.id != CardPacks.PERSONAL) {
                menu.add(R.string.pack_delete).setOnMenuItemClickListener { delete(pack); true }
            }
            show()
        }
    }

    private fun rename(pack: CardPack) = with(ui) {
        val input = EditText(context).apply {
            setText(pack.name); isSingleLine = true; selectAll(); contentDescription = context.getString(R.string.pack_name)
        }
        val form = column().apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input) }
        val dialog = AlertDialog.Builder(context).setTitle(R.string.pack_rename_title).setView(form)
            .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_save, null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = input.text.toString()
            if (runCatching { CardPacks.validateName(value) }.onFailure { input.error = it.message }.isFailure) return@setOnClickListener
            PackWork.run(context as Activity, { store.renamePack(pack.id, value) }) { result ->
                result.onSuccess { dialog.dismiss(); changed() }.onFailure { input.error = it.message }
            }
        } }
        dialog.show()
    }

    private fun delete(pack: CardPack) {
        AlertDialog.Builder(ui.context).setTitle(R.string.pack_delete_confirm)
            .setMessage(ui.context.getString(R.string.pack_delete_detail, pack.name, pack.cards.size))
            .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_delete) { _, _ ->
                PackWork.run(ui.context as Activity, { store.deletePack(pack.id) }) { result ->
                    result.onSuccess { changed() }.onFailure { message(it.message ?: ui.context.getString(R.string.delete_failed)) }
                }
            }.show()
    }
}
