package com.iscolt.cardsimulator.diagnostics

import com.iscolt.cardsimulator.core.*

/** 只解释指定候选规则的差异；诊断结果绝不参与选规则或修改回复。 */
object RuleDifference {
    /**
     * 逐字节比较候选规则与实际命令，MASK 模式只比较掩码选中的位；最多显示 16 处差异。
     * 输入应已规范化且规则有效，界面字节编号从 1 开始，字符串偏移则从 0 开始。
     */
    fun describe(rule: ApduRule, actual: String): String {
        val expected = rule.command
        val differences = (0 until minOf(expected.length, actual.length) step 2).filter { i ->
            val mask = if (rule.mode == MatchMode.MASK) rule.mask.substring(i, i + 2).toInt(16) else 255
            (expected.substring(i, i + 2).toInt(16) and mask) != (actual.substring(i, i + 2).toInt(16) and mask)
        }
        val detail = differences.take(16).joinToString("；") { i ->
            "字节 ${i / 2 + 1}: 期望 ${expected.substring(i, i + 2)} / 实际 ${actual.substring(i, i + 2)}"
        }
        val length = if (expected.length != actual.length) "长度：期望 ${expected.length / 2}，实际 ${actual.length / 2} 字节。" else ""
        return "${rule.mode} · $length$detail" + if (differences.size > 16) "（其余差异省略）" else ""
    }
}
