package com.iscolt.cardsimulator.importer

import com.iscolt.cardsimulator.core.*
import java.util.UUID

data class CapturedPair(val command: String, val response: String, val line: Int)
data class CapturedTransaction(val pairs: List<CapturedPair>, val issues: List<String>) {
    // 仅从 SELECT 命令提取业务 AID，PPSE 不计入业务列表。
    fun aids(): List<String> = pairs.mapNotNull { pair ->
        val c = pair.command
        if (!c.startsWith("00A40400") || c.length < 10) null else {
            val size = c.substring(8, 10).toInt(16)
            if (size !in 5..16 || c.length < 10 + size * 2) null
            else c.substring(10, 10 + size * 2).takeUnless { it == PPSE }
        }
    }.distinct()
    fun toCard(name: String, scheme: String, aids: List<String>): CardScript {
        require(issues.isEmpty()) { "日志有错误，不能导入" }
        val card = CardScript("log-" + UUID.randomUUID(), name, scheme, aids,
            "日志固定回复；未计算动态密文或签名。",
            pairs.map { ApduRule(it.command, it.response, label = "日志行 ${it.line}") }, ordered = true)
        return ScriptCodec.parse(ScriptCodec.encode(listOf(card))).single()
    }
    companion object { const val PPSE = "325041592E5359532E4444463031" }
}

/** 严格解析 T:/C:，不猜测方向、不补造缺失回复。 */
object TransactionLogParser {
    fun parse(text: String): List<CapturedTransaction> {
        require(text.toByteArray().size <= ScriptCodec.MAX_BYTES) { "日志超过 2 MB" }
        val result = mutableListOf<CapturedTransaction>()
        var pairs = mutableListOf<CapturedPair>()
        var issues = mutableListOf<String>()
        var command: String? = null
        var commandLine = 0
        var direction: String? = null
        var buffer = StringBuilder()
        var startLine = 0
        fun flush() {
            val dir = direction ?: return
            val value = Hex.clean(buffer.toString())
            if (!Hex.valid(value) || value.length < (if (dir == "T") 8 else 4)) {
                issues.add("第 $startLine 行：$dir 数据不是完整十六进制 APDU")
                // 错误回复消耗当前命令，避免把下一条回复错误配对。
                if (command != null) issues.add("第 $commandLine 行：命令未获得有效回复")
                command = null
            } else if (dir == "T") {
                if (command != null) issues.add("第 $commandLine 行：命令缺少回复")
                // 只有明确 PPSE 选择才自动分组，其余重复命令保留执行顺序。
                if (value.startsWith("00A404000E" + CapturedTransaction.PPSE) && pairs.isNotEmpty()) {
                    result.add(CapturedTransaction(pairs.toList(), issues.toList()))
                    pairs = mutableListOf(); issues = mutableListOf()
                }
                command = value; commandLine = startLine
            } else {
                val pending = command
                if (pending == null) issues.add("第 $startLine 行：回复前没有命令")
                else { pairs.add(CapturedPair(pending, value, commandLine)); command = null }
            }
            direction = null; buffer = StringBuilder()
        }
        text.trimStart(0xFEFF.toChar()).lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            val marker = Regex("^([TC]):(.*)$", RegexOption.IGNORE_CASE).matchEntire(line)
            when {
                marker != null -> { flush(); direction = marker.groupValues[1].uppercase(); buffer.append(marker.groupValues[2]); startLine = index + 1 }
                line.isBlank() || line.startsWith("#") -> Unit
                line == "---" -> {
                    flush()
                    if (command != null) { issues.add("第 $commandLine 行：命令缺少回复"); command = null }
                    if (pairs.isNotEmpty() || issues.isNotEmpty()) result.add(CapturedTransaction(pairs.toList(), issues.toList()))
                    pairs = mutableListOf(); issues = mutableListOf()
                }
                direction != null && Hex.valid(Hex.clean(line)) -> buffer.append(line)
                else -> { flush(); issues.add("第 ${index + 1} 行：无法识别，仅支持 T:/C:、十六进制续行、# 注释、--- 分隔") }
            }
        }
        flush()
        if (command != null) issues.add("第 $commandLine 行：命令缺少回复")
        if (pairs.isNotEmpty() || issues.isNotEmpty()) result.add(CapturedTransaction(pairs, issues))
        require(result.isNotEmpty()) { "未找到命令回复" }
        require(result.size <= 200) { "交易数超过 200" }
        return result
    }
}
