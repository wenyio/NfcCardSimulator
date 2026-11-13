package com.iscolt.cardsimulator.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.iscolt.cardsimulator.SimulatorApp
import com.iscolt.cardsimulator.core.Hex
import com.iscolt.cardsimulator.core.ScriptEngine

class CardEmulationService : HostApduService() {
    private val store get() = (application as SimulatorApp).store
    private var engine: ScriptEngine? = null
    private var revision: Long = -1
    private var session = "NFC"
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return Hex.bytes("6700")
        val input = Hex.text(commandApdu)
        val card = store.selected()
        if (!store.enabled || card == null) {
            engine = null
            store.log("C → T", "6985 · 模拟未启动")
            return Hex.bytes("6985")
        }
        // 射频会话固定脚本版本；重复选择 AID 不重置步骤，断场或配置变化才重建引擎。
        if (engine == null || engine?.script !== card || revision != store.sessionRevision) {
            engine = ScriptEngine(card)
            revision = store.sessionRevision
            session = store.beginSession("NFC", card.name)
        }
        store.log("T → C", input, session)
        return try {
            val result = engine!!.exchange(input)
            store.log("C → T", "${result.response} · ${result.rule}\n${result.difference}", session)
            Hex.bytes(result.response)
        } catch (e: Exception) {
            store.log("错误", "响应失败：${e.message}", session)
            Hex.bytes("6F00")
        }
    }
    override fun onDeactivated(reason: Int) {
        engine = null
        store.log("会话", if (reason == DEACTIVATION_LINK_LOSS) "射频连接断开" else "切换到其他 NFC 服务", session)
    }
}
