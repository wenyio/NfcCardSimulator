package com.iscolt.cardsimulator.ui.debug

import android.graphics.Typeface
import android.widget.*
import com.iscolt.cardsimulator.R
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.diagnostics.TlvDecoder
import com.iscolt.cardsimulator.ui.UiKit

/** 手动引擎与射频引擎相互独立；切卡或修改脚本会使旧会话失效。 */
class DebugPage(private val ui: UiKit, private val store: SimulatorStore, private val message: (String) -> Unit) {
    private var engine: ScriptEngine? = null
    private var revision = -1L
    private var session = "手动"
    /**
     * 构造手动命令输入、发送、重置和 TLV 结果视图。
     * 发送时按卡片身份及修订号复用或重建独立 ScriptEngine；它与真实 NFC 引擎共享算法，不共享游标。
     */
    fun show(content: LinearLayout) = with(ui) {
        val body = column(); content.addView(ScrollView(context).apply { addView(body) })
        panel(body).apply {
            addView(row().apply {
                addView(label(context.getString(R.string.debug_title), 20f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(icon(R.drawable.ic_info, context.getString(R.string.debug_help_title)) { DebugHelp.show(context) })
            })
            val input = EditText(context).apply {
                hint = context.getString(R.string.debug_input_hint); contentDescription = context.getString(R.string.debug_input_description)
                typeface = Typeface.MONOSPACE; textSize = 14f; minLines = 2
            }
            DebugCommandSelector(ui, this, store.selected()?.rules.orEmpty(), input)
            addView(input)
            val result = label(context.getString(R.string.debug_waiting), 14f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
            val tlv = label("", 12f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
            val actions = row()
            actions.action(context.getString(R.string.debug_send)) {
                val card = store.selected()
                if (card == null) message(context.getString(R.string.debug_select_card)) else {
                    // 与 HCE 相同的脚本失效判断，但这里创建自己的实例，因此手动发送不推进射频步骤。
                    if (engine?.script !== card || revision != store.sessionRevision) {
                        engine = ScriptEngine(card); revision = store.sessionRevision
                        session = store.beginSession("手动", card.name)
                    }
                    // 这里只调用内存解释器，不向真实终端发送射频报文。
                    val exchange = engine!!.exchange(input.text.toString())
                    result.text = "R-APDU  ${exchange.response}\n${exchange.rule}\n${exchange.difference}"
                    tlv.text = TlvDecoder.decodeResponse(exchange.response)
                    store.log("手动 T", Hex.clean(input.text.toString()), session)
                    store.log("手动 C", "${exchange.response} · ${exchange.rule}\n${exchange.difference}", session)
                }
            }
            actions.action(context.getString(R.string.debug_reset)) { engine = null; result.text = context.getString(R.string.debug_reset_done); tlv.text = "" }
            addView(actions); addView(result); addView(label(context.getString(R.string.debug_reply_tlv), 15f, ink, true)); addView(tlv)
        }
    }
}
