package com.iscolt.cardsimulator.core

import org.json.JSONObject

/** 只转换字段；草稿允许未完成输入，导入校验仍由 ScriptCodec 负责。 */
object RuleCodec {
    fun encode(rule: ApduRule, draft: Boolean = false): JSONObject =
        JSONObject().put("command", rule.command).put("response", rule.response)
            .put("match", rule.mode.name).put("label", rule.label).apply {
                if (draft || rule.mode == MatchMode.MASK) put("mask", rule.mask)
            }
    fun decode(json: JSONObject, mode: MatchMode = MatchMode.valueOf(json.getString("match"))) =
        ApduRule(json.getString("command"), json.getString("response"), mode,
            json.optString("mask"), json.optString("label"))
}
