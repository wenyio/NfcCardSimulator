package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.app.Activity
import android.view.View
import android.widget.PopupMenu
import com.iscolt.cardsimulator.ui.JsonViewer
import com.iscolt.cardsimulator.core.ScriptCodec
import com.iscolt.cardsimulator.core.CardScript
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R

/** 列表与详情共用卡片操作，编辑期间暂停模拟，取消或保存后恢复详情会话。 */
class CardActions(private val ui: UiKit, private val store: SimulatorStore,
    private val openEditor: (CardScript?, Boolean) -> Unit,
    private val deleted: () -> Unit, private val export: (CardScript) -> Unit,
    private val message: (String) -> Unit) {
    /** 转交新建、编辑或复制请求，是否暂停模拟由首页进入编辑页的统一入口处理。 */
    fun edit(card: CardScript? = null, copy: Boolean = false) = openEditor(card, copy)
    /** 显示卡片菜单；内置卡只允许复制编辑，用户卡删除经确认和后台保存成功后才刷新。 */
    fun menu(anchor: View, card: CardScript) {
        PopupMenu(ui.context, anchor).apply {
            menu.add(if (card.builtIn) R.string.card_copy_edit else R.string.card_edit).setOnMenuItemClickListener { edit(card, card.builtIn); true }
            menu.add(R.string.card_copy).setOnMenuItemClickListener { edit(card, true); true }
            menu.add(R.string.action_view_json).setOnMenuItemClickListener { JsonViewer(ui).show(ui.context.getString(R.string.card_script_json), ScriptCodec.encode(listOf(card))); true }
            menu.add(R.string.card_export).setOnMenuItemClickListener { export(card); true }
            if (!card.builtIn) menu.add(R.string.card_delete).setOnMenuItemClickListener {
                AlertDialog.Builder(ui.context).setTitle(R.string.card_delete_confirm).setMessage(card.name)
                    .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_delete) { _, _ ->
                        PackWork.run(ui.context as Activity, { store.delete(card.key) }) { result ->
                            result.onSuccess { deleted() }.onFailure { message(it.message ?: ui.context.getString(R.string.delete_failed)) }
                        }
                    }.show(); true
            }
            show()
        }
    }
}
