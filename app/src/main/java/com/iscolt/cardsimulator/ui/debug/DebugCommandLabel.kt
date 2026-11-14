package com.iscolt.cardsimulator.ui.debug

import com.iscolt.cardsimulator.core.ApduRule
import com.iscolt.cardsimulator.core.Hex

/** 仅用于展示命令含义，不参与规则匹配，也不推断私有 CLA 的指令。 */
object DebugCommandLabel {
    /** 根据已知命令头生成下拉摘要，未知命令使用规则标签；编号从 1 展示，不参与匹配。 */
    fun format(index: Int, rule: ApduRule): String {
        val command = Hex.clean(rule.command)
        val name = if (command.length >= 8 && Hex.valid(command)) when (command.take(4)) {
            "00A4" -> "SELECT"
            "80A8" -> "GPO"
            "00B2" -> "READ RECORD"
            "80AE" -> "GENERATE AC"
            "0084" -> "GET CHALLENGE"
            "0088" -> "INTERNAL AUTHENTICATE"
            "0082" -> "EXTERNAL AUTHENTICATE"
            "80CA" -> "GET DATA"
            else -> null
        } else null
        val title = name ?: rule.label.ifBlank { "规则 ${index + 1}" }
        val header = command.take(8) + if (command.length > 8) "…" else ""
        return "${index + 1} · $title · $header"
    }
}
