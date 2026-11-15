package com.iscolt.cardsimulator.diagnostics

import com.iscolt.cardsimulator.core.ApduRule
import com.iscolt.cardsimulator.core.Hex
import com.iscolt.cardsimulator.core.MatchMode

/** DOL 中的一项：标签标识字段，length 是随后命令中该字段值的字节数。 */
internal data class DolField(val tag: String, val length: Int)

/**
 * 每个脚本会话独享定义与 GAC 次数；仅用已经成功匹配并回复的数据解释输入。
 * PDOL（Processing Options Data Object List，处理选项数据对象列表，标签 9F38）解释 GPO 输入。
 * CDOL（Card Risk Management Data Object List，卡片风险管理数据对象列表）解释 GENERATE AC 输入，
 * 其中 CDOL1（8C）用于第一次，CDOL2（8D）用于第二次。次数只统计本会话命中且回复 9000 的命令。
 * GPO（Get Processing Options，获取处理选项）；GAC 是此项目对 GENERATE AC（生成应用密文）的简称。
 * 定义只用于诊断；即使输入可解释，也不会重算 AC（Application Cryptogram，应用密文）。
 */
class DolDiagnostics {
    private val definitions = mutableMapOf<String, String>()
    private val conflicts = mutableSetOf<String>()
    private var gacCount = 0

    /** 清除已观察到的 DOL 定义、冲突标记和成功 GAC 次数，避免跨应用沿用字段布局。 */
    fun reset() {
        definitions.clear()
        conflicts.clear()
        gacCount = 0
    }

    /** 遇到 SELECT 命令头即清除诊断状态，即使后续未匹配；不重置脚本引擎的顺序游标。 */
    fun beforeCommand(command: String) {
        // 新 SELECT 无论是否成功，都不能沿用上一个应用的 DOL 定义。
        if (command.startsWith("00A4")) reset()
    }

    /**
     * 只从成功命中规则所返回的 9000 响应学习定义，统计成功 GAC，并拒绝冲突的字段布局。
     * SELECT 响应提取 PDOL，READ RECORD 响应提取 CDOL1/2；不借用场景元数据猜测定义。
     */
    fun observe(command: String, response: String) {
        // 9000 是成功状态字；错误响应、未命中的兜底响应均不用于学习定义。
        if (!response.endsWith("9000")) return
        if (command.startsWith("80AE")) {
            gacCount++; return
        }
        if (!command.startsWith("00A4") && !command.startsWith("00B2")) return
        // 状态字占 2 字节，即 4 个十六进制字符，它不是 TLV 内容的一部分。
        val values = runCatching { BerTlv.parse(response.dropLast(4)) }.getOrNull() ?: return
        val keys = if (command.startsWith("00A4")) setOf("9F38") else setOf("8C", "8D")
        values.filter { it.tag in keys }.groupBy { it.tag }.forEach { (tag, entries) ->
            val choices = entries.map { it.value }.distinct()
            val old = definitions[tag]
            // 同标签定义不唯一时停止使用，后续同应用即使再收到单一值也不能消除歧义。
            if (choices.size != 1 || old != null && old != choices.single()) {
                definitions.remove(tag); conflicts.add(tag)
            } else if (tag !in conflicts) definitions[tag] = choices.single()
        }
    }

    /**
     * 按本会话已知 PDOL/CDOL 拆解预期与实际命令的数据，生成差异说明。
     * 诊断不足或格式损坏时返回说明文字，不改规则、回复或顺序游标；matched 只控制提示语。
     */
    fun describe(rule: ApduRule, actual: String, matched: Boolean): String {
        val type = when {
            actual.startsWith("80A8") -> "9F38"
            actual.startsWith("80AE") && gacCount < 2 -> if (gacCount == 0) "8C" else "8D"
            else -> return ""
        }
        val title = when (type) {
            "9F38" -> "PDOL"; "8C" -> "CDOL1"; else -> "CDOL2"
        }
        if (rule.command == actual) return ""
        if (type in conflicts) return "$title 定义存在冲突，未按字段解释；请核对当前应用的回复。"
        val definition =
            definitions[type] ?: return "$title：当前会话尚未获得 $type 定义，仅保留原始命令差异。"
        return try {
            val fields = parseDol(definition)
            val input = commandData(actual, type == "9F38")
            val expected = try { commandData(rule.command, type == "9F38") }
            catch (error: IllegalArgumentException) {
                if (rule.mode == MatchMode.PREFIX && actual.startsWith(rule.command) && rule.command.length < actual.length) {
                    require(input.length == fields.sumOf { it.length } * 2) { "实际输入长度与 DOL 定义不一致" }
                    return "$title：规则仅提供命令前缀，未提供完整参考数据；实际输入长度符合 DOL，固定回复不会重算签名或密文。"
                }
                throw error
            }
            val size = fields.sumOf { it.length } * 2
            require(expected.length == size && input.length == size) {
                "DOL 要求 " + size / 2 + " 字节，期望输入 " + expected.length / 2 + "，实际输入 " + input.length / 2
            }
            // 此处对字符串切片，所以 DOL 的字节数必须乘以 2 才是字符偏移。
            var offset = 0
            val changes = fields.mapNotNull { field ->
                val end = offset + field.length * 2
                val expectedValue = expected.substring(offset, end)
                val actualValue = input.substring(offset, end)
                offset = end
                if (expectedValue == actualValue) null else "${field.tag} ${names[field.tag] ?: "未知字段"}：期望 $expectedValue / 实际 $actualValue"
            }
            if (changes.isEmpty()) "$title 数据一致；差异位于 APDU 头、长度或 Le。"
            else (if (matched) "规则已匹配，但固定回复的 $title 输入不同（不会重算签名或密文）：\n"
            else "$title 字段差异：\n") + changes.take(20).joinToString("\n") +
                    if (changes.size > 20) "\n其余差异省略" else ""
        } catch (e: IllegalArgumentException) {
            "$title 无法按字段解释：${e.message}"
        }
    }

    /**
     * 把 DOL 定义拆成标签与长度列表；每项没有值，长度是随后命令应提供的字节数。
     * DOL（Data Object List，数据对象列表）与 TLV 不同，不能把长度后的内容当成该字段值。
     */
    private fun parseDol(value: String): List<DolField> {
        require(Hex.valid(value) && value.length <= 8192) { "DOL 定义格式错误" }
        val bytes = Hex.bytes(value)
        var offset = 0
        val fields = mutableListOf<DolField>()
        while (offset < bytes.size) {
            val start = offset
            val first = bytes[offset++].toInt() and 255
            // 标签延续规则与 BER-TLV 一致，但标签之后的长度规则不同。
            if (first and 31 == 31) {
                var next: Int
                do {
                    require(offset < bytes.size && offset - start < 4) { "DOL Tag 截断" }
                    next = bytes[offset++].toInt() and 255
                } while (next and 128 != 0)
            }
            val tag = Hex.text(bytes.copyOfRange(start, offset))
            require(offset < bytes.size) { "DOL 缺少字段长度" }
            // DOL 的长度是单字节数值，不使用 BER 长度编码。
            // 例如 9F02 06 9F37 04 表示后续数据先给 6 字节金额，再给 4 字节不可预知数。
            fields.add(DolField(tag, bytes[offset++].toInt() and 255))
        }
        return fields
    }

    /**
     * 校验含数据的短/扩展命令 APDU 长度并提取数据域，允许带或不带 Le。
     * gpo 为 true 时还要求唯一 83 模板，返回其值；此函数只供 DOL 诊断，不是通用 APDU 解析器。
     */
    private fun commandData(command: String, gpo: Boolean): String {
        require(Hex.valid(command) && command.length >= 10) { "APDU 缺少 Lc" }
        // 命令头 CLA/INS/P1/P2 共 4 字节，即前 8 字符；其后读取 Lc（Length of Command Data，命令数据长度）。
        val shortLength = command.substring(8, 10).toInt(16)
        val start: Int
        val length: Int
        val le: Int
        if (shortLength != 0) {
            start = 10
            length = shortLength * 2
            le = 2
        } else {
            require(command.length >= 14) { "扩展 APDU 缺少 Lc" }
            // 00 为扩展长度标志，接下来的两字节才是 Lc；此函数前提是含数据的 GPO/GAC。
            start = 14
            length = command.substring(10, 14).toInt(16) * 2
            le = 4
        }
        // Le（Expected Response Data Length，期望响应数据长度）可选，短格式占 1 字节，扩展格式占 2 字节。
        // 这里只核对编码占用空间，不解码 Le=0 对应的最大期望长度。
        require(command.length == start + length || command.length == start + length + le) { "Lc 与命令长度不一致" }
        val data = command.substring(start, start + length)
        if (!gpo) return data
        // GPO 的数据先包在标签 83 中；83 的值才是依 PDOL 排列的字段，不是嵌套 TLV。
        val values = BerTlv.parse(data)
        require(values.size == 1 && values.single().tag == "83") { "GPO 缺少唯一 83 数据模板" }
        return values.single().value
    }

    companion object {
        // 字段名称仅用于诊断显示。完整英文释义和长度示例见 docs/NFC-GLOSSARY.md。
        // TTQ（Terminal Transaction Qualifiers，终端交易属性）、TVR（Terminal Verification Results，终端验证结果）。
        // ICC（Integrated Circuit Card，集成电路卡）；9F4C 是 ICC Dynamic Number（ICC 动态数）。
        private val names = mapOf(
            "9F02" to "授权金额",
            "9F03" to "其他金额",
            "9F1A" to "终端国家代码",
            "5F2A" to "交易货币代码",
            "9A" to "交易日期",
            "9C" to "交易类型",
            "9F37" to "不可预知数（终端随机数）",
            "9F66" to "终端交易属性 TTQ",
            "9F35" to "终端类型",
            "9F33" to "终端能力",
            "95" to "终端验证结果 TVR",
            "9F34" to "持卡人验证结果",
            "9F21" to "交易时间",
            "9F45" to "数据认证码",
            "9F4C" to "ICC 动态数"
        )
    }
}
