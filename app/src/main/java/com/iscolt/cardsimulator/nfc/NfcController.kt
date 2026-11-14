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
import com.iscolt.cardsimulator.R

/** 封装手机系统 NFC 路由；页面和脚本引擎不直接访问系统服务。 */
class NfcController(private val activity: Activity, private val store: SimulatorStore) {
    private val service = ComponentName(activity, CardEmulationService::class.java)
    private val adapter get() = NfcAdapter.getDefaultAdapter(activity)
    private fun supportsHce() = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
    private fun isDefault() = adapter?.let { supportsHce() && CardEmulation.getInstance(it)
        .isDefaultServiceForCategory(service, CardEmulation.CATEGORY_PAYMENT) } ?: false
    fun status(): String = runCatching {
        when {
            !supportsHce() || adapter == null -> activity.getString(R.string.nfc_no_hce_debug)
            adapter?.isEnabled != true -> activity.getString(R.string.nfc_off)
            !isDefault() -> activity.getString(R.string.nfc_default_required)
            else -> activity.getString(R.string.nfc_ready)
        }
    }.getOrElse { activity.getString(R.string.nfc_service_error, it.message) }
    fun start() {
        if (store.enabled) return
        val card = store.selected() ?: error(activity.getString(R.string.debug_select_card))
        val nfc = adapter ?: error(activity.getString(R.string.nfc_missing))
        check(supportsHce()) { activity.getString(R.string.nfc_no_hce) }
        check(nfc.isEnabled) { activity.getString(R.string.nfc_enable) }
        check(isDefault()) { activity.getString(R.string.nfc_default_settings) }
        val aids = (listOf("325041592E5359532E4444463031") + card.aids).distinct()
        // 注册成功后才启动模拟；失败不能伪装为已启动。
        val accepted = CardEmulation.getInstance(nfc).registerAidsForService(service, CardEmulation.CATEGORY_PAYMENT, aids)
        if (!accepted) {
            store.log("路由错误", "service=$service; category=payment; aids=$aids; result=false")
            error(activity.getString(R.string.nfc_registration_failed))
        }
        store.setEnabled(true)
    }
    fun stop() { if (store.enabled) store.setEnabled(false) }
    fun nfcSettings() = activity.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    fun paymentSettings() {
        check(adapter != null && supportsHce()) { activity.getString(R.string.nfc_no_hce) }
        activity.startActivity(if (isDefault()) Intent(Settings.ACTION_NFC_PAYMENT_SETTINGS) else
            Intent(CardEmulation.ACTION_CHANGE_DEFAULT).putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, service)
                .putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT))
    }
}
