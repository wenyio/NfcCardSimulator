package com.iscolt.cardsimulator.core

import org.json.JSONObject

/** 只转换字段；草稿允许未完成输入，导入校验仍由 ScriptCodec 负责。 */
object RuleCodec {
    /** 编码一条规则；正式导出只为 MASK 模式输出掩码，草稿则保留未完成的掩码输入。 */
    fun encode(rule: ApduRule, draft: Boolean = false): JSONObject =
        JSONObject().put("command", rule.command).put("response", rule.response)
            .put("match", rule.mode.name).put("label", rule.label).apply {
                if (draft || rule.mode == MatchMode.MASK) put("mask", rule.mask)
            }

    /**
     * 按当前字段名读取规则，不执行十六进制或长度校验，允许编辑草稿包含未完成输入。
     * 未显式传 mode 时读取必需的 match 枚举名；导入入口另行处理默认值并校验。
     */
    fun decode(json: JSONObject, mode: MatchMode = MatchMode.valueOf(json.getString("match"))) =
        ApduRule(
            json.getString("command"), json.getString("response"), mode,
            json.optString("mask"), json.optString("label")
        )
}
