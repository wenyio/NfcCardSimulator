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
    fun show(content: LinearLayout) = with(ui) {
        val body = column(); content.addView(ScrollView(context).apply { addView(body) })
        panel(body).apply {
            addView(row().apply {
                addView(label("APDU 手动调试", 20f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(icon(R.drawable.ic_info, "调试使用说明") { DebugHelp.show(context) })
            })
            val input = EditText(context).apply {
                hint = "输入 C-APDU 十六进制"; contentDescription = "C-APDU 输入"
                typeface = Typeface.MONOSPACE; textSize = 14f; minLines = 2
            }
            DebugCommandSelector(ui, this, store.selected()?.rules.orEmpty(), input)
            addView(input)
            val result = label("等待输入命令", 14f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
            val tlv = label("", 12f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
            val actions = row()
            actions.action("发送到脚本") {
                val card = store.selected()
                if (card == null) message("请先选择卡片") else {
                    if (engine?.script !== card || revision != store.sessionRevision) {
                        engine = ScriptEngine(card); revision = store.sessionRevision
                        session = store.beginSession("手动", card.name)
                    }
                    val exchange = engine!!.exchange(input.text.toString())
                    result.text = "R-APDU  ${exchange.response}\n${exchange.rule}\n${exchange.difference}"
                    tlv.text = TlvDecoder.decodeResponse(exchange.response)
                    store.log("手动 T", Hex.clean(input.text.toString()), session)
                    store.log("手动 C", "${exchange.response} · ${exchange.rule}\n${exchange.difference}", session)
                }
            }
            actions.action("重置会话") { engine = null; result.text = "手动会话已重置"; tlv.text = "" }
            addView(actions); addView(result); addView(label("回复 TLV", 15f, ink, true)); addView(tlv)
        }
    }
}
