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

    /** 查询设备是否声明 HCE（Host Card Emulation，主机卡模拟）能力；普通 NFC 硬件支持不等于支持 HCE。 */
    private fun supportsHce() =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)

    /** 查询本服务是否为系统 payment 类别的默认服务；本项目启动前要求满足此条件。 */
    private fun isDefault() = adapter?.let {
        supportsHce() && CardEmulation.getInstance(it)
            .isDefaultServiceForCategory(service, CardEmulation.CATEGORY_PAYMENT)
    } ?: false

    /** 按硬件能力、NFC 开关、默认支付服务顺序生成状态文字；系统调用异常转为可显示的错误。 */
    fun status(): String = runCatching {
        when {
            !supportsHce() || adapter == null -> activity.getString(R.string.nfc_no_hce_debug)
            adapter?.isEnabled != true -> activity.getString(R.string.nfc_off)
            !isDefault() -> activity.getString(R.string.nfc_default_required)
            else -> activity.getString(R.string.nfc_ready)
        }
    }.getOrElse { activity.getString(R.string.nfc_service_error, it.message) }

    /**
     * 幂等启动：已有实际启用状态就返回，否则检查设备条件并注册 PPSE 与当前卡的 AID。
     * NFC（Near Field Communication，近场通信）；AID（Application Identifier，应用标识符）用于系统选择路由。
     * 路由注册成功后才设置 enabled；注册路由不会替脚本生成 SELECT 回复。
     */
    fun start() {
        if (store.enabled) return
        val card = store.selected() ?: error(activity.getString(R.string.debug_select_card))
        val nfc = adapter ?: error(activity.getString(R.string.nfc_missing))
        check(supportsHce()) { activity.getString(R.string.nfc_no_hce) }
        check(nfc.isEnabled) { activity.getString(R.string.nfc_enable) }
        check(isDefault()) { activity.getString(R.string.nfc_default_settings) }
        // PPSE（Proximity Payment System Environment，近距离支付系统环境）是常见非接触支付发现入口。
        // 此十六进制值对应 ASCII 文本 2PAY.SYS.DDF01；业务应用 AID 另由卡片配置提供。
        // ASCII（American Standard Code for Information Interchange，美国信息交换标准代码）定义字符与字节的对应。
        val aids = (listOf("325041592E5359532E4444463031") + card.aids).distinct()
        // 注册成功后才启动模拟；失败不能伪装为已启动。
        val accepted = CardEmulation.getInstance(nfc)
            .registerAidsForService(service, CardEmulation.CATEGORY_PAYMENT, aids)
        if (!accepted) {
            store.log("路由错误", "service=$service; category=payment; aids=$aids; result=false")
            error(activity.getString(R.string.nfc_registration_failed))
        }
        store.setEnabled(true)
    }

    /** 仅在已启用时关闭应用模拟开关，让旧引擎修订号失效；不撤销系统的默认支付选择。 */
    fun stop() {
        if (store.enabled) store.setEnabled(false)
    }

    /** 打开 Android 的 NFC 开关设置页；离开应用前台会由页面生命周期暂停模拟。 */
    fun nfcSettings() = activity.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))

    /** 非默认服务时请求系统切换默认支付应用，否则打开支付设置；硬件不支持时抛出提示。 */
    fun paymentSettings() {
        check(adapter != null && supportsHce()) { activity.getString(R.string.nfc_no_hce) }
        activity.startActivity(
            if (isDefault()) Intent(Settings.ACTION_NFC_PAYMENT_SETTINGS) else
                Intent(CardEmulation.ACTION_CHANGE_DEFAULT).putExtra(
                    CardEmulation.EXTRA_SERVICE_COMPONENT,
                    service
                )
                    .putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT)
        )
    }
}
