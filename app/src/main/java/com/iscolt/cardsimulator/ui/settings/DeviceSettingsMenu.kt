package com.iscolt.cardsimulator.ui.settings

import android.content.Context
import android.view.View
import android.widget.PopupMenu
import com.iscolt.cardsimulator.nfc.NfcController
import com.iscolt.cardsimulator.R

/** 仅实际跳转系统设置才触发生命周期暂停；展开菜单不重置当前模拟会话。 */
class DeviceSettingsMenu(private val context: Context, private val nfc: NfcController,
    private val message: (String) -> Unit) {
    /** 显示 NFC 和支付设置入口；只展开菜单不触发页面离开或重置会话。 */
    fun show(anchor: View) {
        PopupMenu(context, anchor).apply {
            menu.add(context.getString(R.string.settings_nfc)).setOnMenuItemClickListener { launch { nfc.nfcSettings() }; true }
            menu.add(context.getString(R.string.settings_payment)).setOnMenuItemClickListener { launch { nfc.paymentSettings() }; true }
            show()
        }
    }
    /** 执行系统跳转并捕获不可用等异常，以页面提示呈现失败原因。 */
    private fun launch(action: () -> Unit) {
        runCatching(action).onFailure { message(it.message ?: context.getString(R.string.settings_failed)) }
    }
}
