package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RuleCodecTest {
    @Test fun unfinishedRuleAndHiddenMaskSurviveDraftRoundTrip() {
        for (mode in MatchMode.entries) {
            val unfinished = ApduRule("F0Z", "9", mode, "FF?", "未完成")
            assertEquals(unfinished, RuleCodec.decode(RuleCodec.encode(unfinished, draft = true)))
        }
    }
    @Test fun exportedRulesKeepSchemaAndStrictImportValidation() {
        val base = CardScript("rule-test", "规则", "其他", listOf("F001020304"), "", emptyList())
        for (mode in MatchMode.entries) {
            val rule = ApduRule("00ca000000", "9000", mode, "ffffffffff", "说明")
            val json = RuleCodec.encode(rule)
            assertEquals(mode == MatchMode.MASK, json.has("mask"))
            assertFalse(json.has("mode"))
            val parsed = ScriptCodec.parse(ScriptCodec.encode(listOf(base.copy(rules = listOf(rule))))).single().rules.single()
            assertEquals(rule.copy(command = rule.command.uppercase(),
                mask = if (mode == MatchMode.MASK) rule.mask.uppercase() else ""), parsed)
        }
        val invalid = base.copy(rules = listOf(ApduRule("Z", "9", MatchMode.MASK, "?")))
        assertTrue(runCatching { ScriptCodec.parse(ScriptCodec.encode(listOf(invalid))) }.isFailure)
        val alias = RuleCodec.encode(ApduRule("00CA000000", "9000")).apply { remove("match"); put("mode", "MASK") }
        val card = JSONObject(ScriptCodec.encode(listOf(base.copy(rules = listOf(ApduRule("00CA000000", "9000"))))))
        card.getJSONArray("cards").getJSONObject(0).getJSONArray("rules").put(0, alias)
        assertEquals(MatchMode.EXACT, ScriptCodec.parse(card.toString()).single().rules.single().mode)
    }
}
