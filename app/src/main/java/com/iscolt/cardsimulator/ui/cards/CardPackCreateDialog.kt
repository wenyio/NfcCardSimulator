package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.app.Activity
import android.widget.ScrollView
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.core.CardPacks
import com.iscolt.cardsimulator.ui.UiKit

/** 创建空包与文件导入分开；校验失败保留输入，成功后使用新包返回首页。 */
class CardPackCreateDialog(private val ui: UiKit, private val store: SimulatorStore, private val created: () -> Unit) {
    fun show() = with(ui) {
        val form = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
        val name = editorField(form, "卡包名称", "")
        val description = editorField(form, "说明（选填）", "", true)
        val dialog = AlertDialog.Builder(context).setTitle("新建卡包")
            .setView(ScrollView(context).apply { addView(form) })
            .setNegativeButton("取消", null).setPositiveButton("创建", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val packName = name.text.toString(); val packDescription = description.text.toString()
            if (runCatching { CardPacks.validateName(packName) }.onFailure { name.error = it.message }.isFailure) return@setOnClickListener
            PackWork.run(context as Activity, { store.createPack(packName, packDescription) }) { result ->
                result.onSuccess { dialog.dismiss(); created() }.onFailure { name.error = it.message }
            }
        } }
        dialog.show()
    }
}
