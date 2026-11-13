package com.iscolt.cardsimulator.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.provider.Settings
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.hce.CardEmulationService

/** 封装手机系统 NFC 路由；页面和脚本引擎不直接访问系统服务。 */
class NfcController(private val activity: Activity, private val store: SimulatorStore) {
    private val service = ComponentName(activity, CardEmulationService::class.java)
    private val adapter get() = NfcAdapter.getDefaultAdapter(activity)
    private fun supportsHce() = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
    private fun isDefault() = adapter?.let { supportsHce() && CardEmulation.getInstance(it)
        .isDefaultServiceForCategory(service, CardEmulation.CATEGORY_PAYMENT) } ?: false
    fun status(): String = runCatching {
        when {
            !supportsHce() || adapter == null -> "设备不支持 NFC HCE · 可调试脚本"
            adapter?.isEnabled != true -> "NFC 未开启"
            !isDefault() -> "请设为默认支付应用"
            else -> "NFC HCE 就绪 · 保持屏幕解锁"
        }
    }.getOrElse { "NFC 系统服务不可用：${it.message}" }
    fun start() {
        if (store.enabled) return
        val card = store.selected() ?: error("请先选择卡片")
        val nfc = adapter ?: error("当前设备没有 NFC")
        check(supportsHce()) { "设备不支持 HCE" }
        check(nfc.isEnabled) { "请先打开 NFC" }
        check(isDefault()) { "请到设备设置中将本应用设为默认支付应用" }
        val aids = (listOf("325041592E5359532E4444463031") + card.aids).distinct()
        // 注册成功后才启动模拟；失败不能伪装为已启动。
        val accepted = CardEmulation.getInstance(nfc).registerAidsForService(service, CardEmulation.CATEGORY_PAYMENT, aids)
        if (!accepted) {
            store.log("路由错误", "service=$service; category=payment; aids=$aids; result=false")
            error("系统未接受 AID 路由注册，模拟未启动。请确认使用支持 HCE 的原生 Android 环境，详见日志。")
        }
        store.setEnabled(true)
    }
    fun stop() { if (store.enabled) store.setEnabled(false) }
    fun nfcSettings() = activity.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    fun paymentSettings() {
        check(adapter != null && supportsHce()) { "设备不支持 HCE" }
        activity.startActivity(if (isDefault()) Intent(Settings.ACTION_NFC_PAYMENT_SETTINGS) else
            Intent(CardEmulation.ACTION_CHANGE_DEFAULT).putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, service)
                .putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT))
    }
}
