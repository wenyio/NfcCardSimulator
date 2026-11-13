package com.iscolt.cardsimulator.ui.settings

import android.content.Context
import android.view.View
import android.widget.PopupMenu
import com.iscolt.cardsimulator.nfc.NfcController

/** 仅实际跳转系统设置才触发生命周期暂停；展开菜单不重置当前模拟会话。 */
class DeviceSettingsMenu(private val context: Context, private val nfc: NfcController,
    private val message: (String) -> Unit) {
    fun show(anchor: View) {
        PopupMenu(context, anchor).apply {
            menu.add("NFC 设置").setOnMenuItemClickListener { launch { nfc.nfcSettings() }; true }
            menu.add("默认支付设置").setOnMenuItemClickListener { launch { nfc.paymentSettings() }; true }
            show()
        }
    }
    private fun launch(action: () -> Unit) {
        runCatching(action).onFailure { message(it.message ?: "无法打开系统设置") }
    }
}
