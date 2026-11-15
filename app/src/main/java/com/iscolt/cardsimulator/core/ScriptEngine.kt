package com.iscolt.cardsimulator.core

import com.iscolt.cardsimulator.diagnostics.RuleDifference
import com.iscolt.cardsimulator.diagnostics.DolDiagnostics

/**
 * 每个射频/手动会话独享一个引擎；核心匹配不访问 Android 或文件。
 * 这里是固定回复解释器：读取命令、选择规则、推进步骤并返回既存回复，不计算支付密文。
 * 持有状态，因此不能让真实 NFC 与手动调试共用一个实例。入口见 CardEmulationService 与 DebugPage。
 */
class ScriptEngine(val script: CardScript) {
    // 当前待执行步骤的下标，从 0 开始；只有 ordered=true 时参与选规则。
    private var cursor = 0

    // DOL（Data Object List，数据对象列表）诊断记录本会话已经得到的字段布局。
    private val dol = DolDiagnostics()

    /** 把顺序游标归零并清除本会话学习到的 DOL 定义及命令次数；不修改脚本。 */
    fun reset() {
        cursor = 0
        dol.reset()
    }

    /**
     * 处理一条命令，返回规则的固定回复或兜底回复，并附加诊断文字。
     * 顺序模式只比较当前步骤，命中才推进；内容模式取首条命中规则。诊断不改变选规则和回复。
     * 输入和输出均为十六进制文本；response 已包含状态字，调用方不能再追加 9000。
     */
    fun exchange(raw: String): Exchange {
        val command = Hex.clean(raw)
        // 此处只检查十六进制及总长度，不解析所有 APDU case（数据域/期望长度的组合）。
        // 4 字节命令头对应 8 个字符；上限沿用当前脚本字段限制，不表示设备均支持该长度。
        if (!Hex.valid(command) || command.length < 8 || command.length > 131080)
            return Exchange("6700", "APDU 长度或格式错误", false)
        // SELECT 会清空诊断所学定义，但不会清空顺序脚本游标，两类状态不可混为一谈。
        dol.beforeCommand(command)
        val rule =
            if (script.ordered) script.rules.getOrNull(cursor)?.takeIf { it.matches(command) }
            else script.rules.firstOrNull { it.matches(command) }
        if (rule == null) {
            // GPO（Get Processing Options，获取处理选项）常以 80 A8 开头；
            // GAC 是本项目对 GENERATE AC（Generate Application Cryptogram，生成应用密文）的简称，常以 80 AE 开头。
            // 顺序脚本只解释当前步骤；内容模式仅在 GPO/GAC 头唯一时提供诊断候选。
            // 候选绝不参与匹配、推进游标或修改回复，避免诊断改变模拟行为。
            val expected = if (script.ordered) script.rules.getOrNull(cursor) else {
                if (command.startsWith("80A8") || command.startsWith("80AE"))
                    script.rules.filter { it.command.take(8) == command.take(8) }
                        .singleOrNull() else null
            }
            val detail = expected?.let {
                listOf(RuleDifference.describe(it, command), dol.describe(it, command, false))
                    .filter { text -> text.isNotBlank() }.joinToString("\n")
            } ?: if (script.ordered) "所有步骤已执行，请重置会话"
            else "共 ${script.rules.size} 条规则，无唯一诊断候选；可切换顺序模式定位差异"
            return Exchange(
                script.fallback,
                if (script.ordered) "步骤 ${cursor + 1} 未匹配" else "无匹配规则",
                false,
                detail
            )
        }
        // 后置 ++ 先取得本次索引再推进；只有走到命中分支才执行，失败回复不消耗步骤。
        val index = if (script.ordered) cursor++ else script.rules.indexOf(rule)
        // 宽松匹配成功也提示输入变化：掩码只影响选规则，不能令固定密文或签名适配新输入。
        // PREFIX 可配一条同回复的完整 EXACT 作为诊断参考；它不参与本次选规则。
        val reference = if (rule.mode == MatchMode.PREFIX) script.rules.filter {
            it.mode == MatchMode.EXACT && it.response == rule.response && it.command.startsWith(rule.command)
        }.singleOrNull() ?: rule else rule
        val detail = dol.describe(reference, command, true)
        // 先解释当前输入，再观察本次固定回复，避免用尚未返回的定义反向解释之前的输入。
        dol.observe(command, rule.response)
        return Exchange(rule.response, rule.label.ifBlank { "规则 ${index + 1}" }, true, detail)
    }
}
