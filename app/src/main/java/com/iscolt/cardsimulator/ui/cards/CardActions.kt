package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.view.View
import android.widget.PopupMenu
import com.iscolt.cardsimulator.ui.JsonViewer
import com.iscolt.cardsimulator.core.ScriptCodec
import com.iscolt.cardsimulator.core.CardScript
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.ui.UiKit

/** 列表与详情共用卡片操作，编辑期间暂停模拟，取消或保存后恢复详情会话。 */
class CardActions(private val ui: UiKit, private val store: SimulatorStore,
    private val openEditor: (CardScript?, Boolean) -> Unit,
    private val deleted: () -> Unit, private val export: (CardScript) -> Unit,
    private val message: (String) -> Unit) {
    fun edit(card: CardScript? = null, copy: Boolean = false) = openEditor(card, copy)
    fun menu(anchor: View, card: CardScript) {
        PopupMenu(ui.context, anchor).apply {
            menu.add(if (card.builtIn) "复制后编辑" else "编辑卡片").setOnMenuItemClickListener { edit(card, card.builtIn); true }
            menu.add("复制卡片").setOnMenuItemClickListener { edit(card, true); true }
            menu.add("查看 JSON").setOnMenuItemClickListener { JsonViewer(ui).show("脚本 JSON", ScriptCodec.encode(listOf(card))); true }
            menu.add("导出卡片").setOnMenuItemClickListener { export(card); true }
            if (!card.builtIn) menu.add("删除卡片").setOnMenuItemClickListener {
                AlertDialog.Builder(ui.context).setTitle("删除此卡片？").setMessage(card.name)
                    .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                        runCatching { store.delete(card.key); deleted() }.onFailure { message(it.message ?: "删除失败") }
                    }.show(); true
            }
            show()
        }
    }
}
