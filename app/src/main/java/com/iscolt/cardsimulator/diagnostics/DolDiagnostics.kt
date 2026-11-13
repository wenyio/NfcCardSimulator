package com.iscolt.cardsimulator.diagnostics

import com.iscolt.cardsimulator.core.ApduRule
import com.iscolt.cardsimulator.core.Hex

internal data class DolField(val tag: String, val length: Int)

/** 每个脚本会话独享定义与 GAC 次数；仅用已经成功匹配并回复的数据解释输入。 */
class DolDiagnostics {
    private val definitions = mutableMapOf<String, String>()
    private val conflicts = mutableSetOf<String>()
    private var gacCount = 0

    fun reset() { definitions.clear(); conflicts.clear(); gacCount = 0 }
    fun beforeCommand(command: String) {
        // 新 SELECT 无论是否成功，都不能沿用上一个应用的 DOL 定义。
        if (command.startsWith("00A4")) reset()
    }
    fun observe(command: String, response: String) {
        if (!response.endsWith("9000")) return
        if (command.startsWith("80AE")) { gacCount++; return }
        if (!command.startsWith("00A4") && !command.startsWith("00B2")) return
        val values = runCatching { BerTlv.parse(response.dropLast(4)) }.getOrNull() ?: return
        val keys = if (command.startsWith("00A4")) setOf("9F38") else setOf("8C", "8D")
        values.filter { it.tag in keys }.groupBy { it.tag }.forEach { (tag, entries) ->
            val choices = entries.map { it.value }.distinct()
            val old = definitions[tag]
            if (choices.size != 1 || old != null && old != choices.single()) {
                definitions.remove(tag); conflicts.add(tag)
            } else if (tag !in conflicts) definitions[tag] = choices.single()
        }
    }

    fun describe(rule: ApduRule, actual: String, matched: Boolean): String {
        val type = when {
            actual.startsWith("80A8") -> "9F38"
            actual.startsWith("80AE") && gacCount < 2 -> if (gacCount == 0) "8C" else "8D"
            else -> return ""
        }
        val title = when (type) { "9F38" -> "PDOL"; "8C" -> "CDOL1"; else -> "CDOL2" }
        if (rule.command == actual) return ""
        if (type in conflicts) return "$title 定义存在冲突，未按字段解释；请核对当前应用的回复。"
        val definition = definitions[type] ?: return "$title：当前会话尚未获得 $type 定义，仅保留原始命令差异。"
        return try {
            val fields = parseDol(definition)
            val expected = commandData(rule.command, type == "9F38")
            val input = commandData(actual, type == "9F38")
            val size = fields.sumOf { it.length } * 2
            require(expected.length == size && input.length == size) {
                "DOL 要求 "+size / 2+" 字节，期望输入 "+expected.length / 2+"，实际输入 "+input.length / 2
            }
            var offset = 0
            val changes = fields.mapNotNull { field ->
                val end = offset + field.length * 2
                val a = expected.substring(offset, end); val b = input.substring(offset, end)
                offset = end
                if (a == b) null else "${field.tag} ${names[field.tag] ?: "未知字段"}：期望 $a / 实际 $b"
            }
            if (changes.isEmpty()) "$title 数据一致；差异位于 APDU 头、长度或 Le。"
            else (if (matched) "规则已匹配，但固定回复的 $title 输入不同（不会重算签名或密文）：\n"
                else "$title 字段差异：\n") + changes.take(20).joinToString("\n") +
                if (changes.size > 20) "\n其余差异省略" else ""
        } catch (e: IllegalArgumentException) { "$title 无法按字段解释：${e.message}" }
    }

    private fun parseDol(value: String): List<DolField> {
        require(Hex.valid(value) && value.length <= 8192) { "DOL 定义格式错误" }
        val bytes = Hex.bytes(value); var p = 0
        val fields = mutableListOf<DolField>()
        while (p < bytes.size) {
            val start = p
            val first = bytes[p++].toInt() and 255
            if (first and 31 == 31) {
                var next: Int
                do {
                    require(p < bytes.size && p - start < 4) { "DOL Tag 截断" }
                    next = bytes[p++].toInt() and 255
                } while (next and 128 != 0)
            }
            val tag = Hex.text(bytes.copyOfRange(start, p))
            require(p < bytes.size) { "DOL 缺少字段长度" }
            // DOL 的长度是单字节数值，不使用 BER 长度编码。
            fields.add(DolField(tag, bytes[p++].toInt() and 255))
        }
        return fields
    }

    private fun commandData(command: String, gpo: Boolean): String {
        require(Hex.valid(command) && command.length >= 10) { "APDU 缺少 Lc" }
        val short = command.substring(8, 10).toInt(16)
        val start: Int; val length: Int; val le: Int
        if (short != 0) { start = 10; length = short * 2; le = 2 }
        else {
            require(command.length >= 14) { "扩展 APDU 缺少 Lc" }
            start = 14; length = command.substring(10, 14).toInt(16) * 2; le = 4
        }
        require(command.length == start + length || command.length == start + length + le) { "Lc 与命令长度不一致" }
        val data = command.substring(start, start + length)
        if (!gpo) return data
        val values = BerTlv.parse(data)
        require(values.size == 1 && values.single().tag == "83") { "GPO 缺少唯一 83 数据模板" }
        return values.single().value
    }

    companion object {
        private val names = mapOf("9F02" to "授权金额", "9F03" to "其他金额", "9F1A" to "终端国家代码",
            "5F2A" to "交易货币代码", "9A" to "交易日期", "9C" to "交易类型", "9F37" to "不可预知数（终端随机数）",
            "9F66" to "终端交易属性 TTQ", "9F35" to "终端类型", "9F33" to "终端能力", "95" to "终端验证结果 TVR",
            "9F34" to "持卡人验证结果", "9F21" to "交易时间", "9F45" to "数据认证码", "9F4C" to "ICC 动态数")
    }
}
