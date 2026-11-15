package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import org.junit.Assert.*
import org.junit.Test

class PrefixDiagnosticsTest {
    private val select = ApduRule("00A4040005F00102030400", "6F099F38069F02069F37049000")
    private val full = ApduRule("80A800000C830A00000000000111223344", "9000")
    private val prefix = full.copy(command = "80A800000C", mode = MatchMode.PREFIX)
    private val actual = "80A800000C830A000000000002AABBCCDD"
    private fun engine(vararg rules: ApduRule) = ScriptEngine(
        CardScript("prefix", "prefix", "", listOf("F001020304"), "", listOf(select) + rules))

    @Test fun prefixOnlyDoesNotMistakeReferenceForMalformedTerminalCommand() {
        val engine = engine(prefix)
        engine.exchange(select.command)
        val reply = engine.exchange(actual)
        assertTrue(reply.matched)
        assertTrue(reply.difference, reply.difference.contains("未提供完整参考数据"))
        assertFalse(reply.difference.contains("Lc 与命令长度不一致"))
        val malformed = engine.exchange(actual.dropLast(2))
        assertTrue(malformed.matched)
        assertTrue(malformed.difference.contains("Lc 与命令长度不一致"))
    }

    @Test fun uniqueFullReferenceExplainsDynamicFieldsWithoutChangingReply() {
        val engine = engine(full, prefix)
        engine.exchange(select.command)
        val reply = engine.exchange(actual)
        assertTrue(reply.matched)
        assertEquals(prefix.response, reply.response)
        assertTrue(reply.difference, reply.difference.contains("实际 AABBCCDD"))
    }

    @Test fun ambiguousReferenceIsNotGuessedAndDoesNotChangeMatching() {
        val engine = engine(full, full.copy(command = actual.replace("AABBCCDD", "01020304")), prefix)
        engine.exchange(select.command)
        val reply = engine.exchange(actual)
        assertTrue(reply.matched)
        assertTrue(reply.difference.contains("未提供完整参考数据"))
    }

    @Test fun shortPrefixIsValidatedAndUsedOnlyAfterSpecificRules() {
        val rules = listOf(
            ApduRule("80AE800004", "019000", MatchMode.PREFIX),
            ApduRule("80AE", "029000", MatchMode.PREFIX))
        val card = ScriptValidation.card(CardScript("fallback", "fallback", "", listOf("F001020304"), "", rules))
        val restored = ScriptCodec.parse(ScriptCodec.encode(listOf(card))).single()
        val engine = ScriptEngine(restored)
        assertEquals("019000", engine.exchange("80AE80000401020304").response)
        assertEquals("029000", engine.exchange("80AE4000020102").response)
        assertEquals("6700", engine.exchange("80AE").response)
        assertTrue(runCatching { ScriptValidation.rule(rules[1].copy(mode = MatchMode.EXACT)) }.isFailure)
        assertTrue(runCatching { ScriptValidation.rule(rules[1].copy(command = "80")) }.isFailure)
    }
}
