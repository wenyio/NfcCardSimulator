package com.iscolt.cardsimulator.core

import com.iscolt.cardsimulator.diagnostics.RuleDifference
import com.iscolt.cardsimulator.diagnostics.DolDiagnostics

/** 每个射频/手动会话独享一个引擎；核心匹配不访问 Android 或文件。 */
class ScriptEngine(val script: CardScript) {
    private var cursor = 0
    private val dol = DolDiagnostics()
    fun reset() { cursor = 0; dol.reset() }
    fun exchange(raw: String): Exchange {
        val command = Hex.clean(raw)
        if (!Hex.valid(command) || command.length < 8 || command.length > 131080)
            return Exchange("6700", "APDU 长度或格式错误", false)
        dol.beforeCommand(command)
        val rule = if (script.ordered) script.rules.getOrNull(cursor)?.takeIf { it.matches(command) }
                   else script.rules.firstOrNull { it.matches(command) }
        if (rule == null) {
            // 顺序脚本只解释当前步骤；内容模式仅在 GPO/GAC 头唯一时提供诊断候选。
            // 候选绝不参与匹配、推进游标或修改回复，避免诊断改变模拟行为。
            val expected = if (script.ordered) script.rules.getOrNull(cursor) else {
                if (command.startsWith("80A8") || command.startsWith("80AE"))
                    script.rules.filter { it.command.take(8) == command.take(8) }.singleOrNull() else null
            }
            val detail = expected?.let {
                listOf(RuleDifference.describe(it, command), dol.describe(it, command, false))
                    .filter { text -> text.isNotBlank() }.joinToString("\n")
            } ?: if (script.ordered) "所有步骤已执行，请重置会话"
                else "共 ${script.rules.size} 条规则，无唯一诊断候选；可切换顺序模式定位差异"
            return Exchange(script.fallback, if (script.ordered) "步骤 ${cursor + 1} 未匹配" else "无匹配规则", false, detail)
        }
        val index = if (script.ordered) cursor++ else script.rules.indexOf(rule)
        // 宽松匹配成功也提示输入变化：掩码只影响选规则，不能令固定密文或签名适配新输入。
        val detail = dol.describe(rule, command, true)
        dol.observe(command, rule.response)
        return Exchange(rule.response, rule.label.ifBlank { "规则 ${index + 1}" }, true, detail)
    }
}
