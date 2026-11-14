package com.iscolt.cardsimulator.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.iscolt.cardsimulator.SimulatorApp
import com.iscolt.cardsimulator.core.Hex
import com.iscolt.cardsimulator.core.ScriptEngine

/**
 * NFC（Near Field Communication，近场通信）的真实命令入口，由 Android 绑定和调用。
 * HCE（Host Card Emulation，主机卡模拟）让应用在手机主机处理卡片命令，射频传输由系统负责。
 * 服务声明和静态路由见 AndroidManifest.xml、res/xml/apdu_service.xml；动态路由由 NfcController 注册。
 * 不负责读取实体卡或模拟卡片 UID（Unique Identifier，唯一标识符）；这里只处理被路由来的 APDU。
 */
class CardEmulationService : HostApduService() {
    private val store get() = (application as SimulatorApp).store
    private var engine: ScriptEngine? = null

    // 记录引擎创建时的配置修订号；与仓库不同时，旧会话不能继续使用。
    private var revision: Long = -1
    private var session = "NFC"

    /**
     * Android 将路由到本服务的 C-APDU 交给此回调，本函数同步返回完整 R-APDU 字节数组。
     * APDU（Application Protocol Data Unit，应用协议数据单元）；C 为 Command（命令），R 为 Response（响应）。
     * 回调默认运行在应用主线程；这里执行规则匹配和内存日志追加，日志文件另由写线程保存。
     */
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        // 6700：长度错误。本分支防御空参数，正常系统回调应提供命令。
        if (commandApdu == null) return Hex.bytes("6700")
        val input = Hex.text(commandApdu)
        val card = store.selected()
        if (!store.enabled || card == null) {
            engine = null
            // 6985：使用条件不满足。路由仍可能存在，但应用当前没有允许脚本模拟。
            store.log("C → T", "6985 · 模拟未启动")
            return Hex.bytes("6985")
        }
        // 射频会话固定脚本版本；重复选择 AID 不重置步骤，断场或配置变化才重建引擎。
        if (engine == null || engine?.script !== card || revision != store.sessionRevision) {
            // !== 比较对象身份；脚本快照或修订号变化时重新从步骤 0 开始。
            engine = ScriptEngine(card)
            revision = store.sessionRevision
            session = store.beginSession("NFC", card.name)
        }
        // T（Terminal，终端）发命令给 C（Card，卡片）；回复方向相反。
        store.log("T → C", input, session)
        return try {
            val result = engine!!.exchange(input)
            store.log("C → T", "${result.response} · ${result.rule}\n${result.difference}", session)
            Hex.bytes(result.response)
        } catch (e: Exception) {
            store.log("错误", "响应失败：${e.message}", session)
            // 6F00：无精确诊断的失败；此处作为匹配/响应转换异常时的服务兜底。
            Hex.bytes("6F00")
        }
    }

    /**
     * Android 通知本 HCE 选择失活时丢弃引擎；下条命令重新建立脚本会话。
     * 可能是射频连接丢失，也可能是终端切换到其他服务，不能把所有失活都解释为断场。
     */
    override fun onDeactivated(reason: Int) {
        engine = null
        store.log(
            "会话",
            if (reason == DEACTIVATION_LINK_LOSS) "射频连接断开" else "切换到其他 NFC 服务",
            session
        )
    }
}
