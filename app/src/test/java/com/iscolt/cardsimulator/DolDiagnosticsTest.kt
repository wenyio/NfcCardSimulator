package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import org.junit.Assert.*
import org.junit.Test

class DolDiagnosticsTest {
    private val select = ApduRule("00A4040005F00102030400", "6F099F38069F02069F37049000")
    private val gpo = ApduRule("80A800000C830A00000000000111223344", "9000")
    private val changed = "80A800000C830A000000000002AABBCCDD"
    private fun engine(rules: List<ApduRule>, ordered: Boolean = true) = ScriptEngine(
        CardScript("x", "x", "", listOf("F001020304"), "", rules, ordered))

    @Test fun gpoExplainsAmountAndRandomAndFailureDoesNotAdvance() {
        val e = engine(listOf(select, gpo)); e.exchange(select.command)
        val miss = e.exchange(changed)
        assertFalse(miss.matched)
        assertTrue(miss.difference, miss.difference.contains("9F02 授权金额"))
        assertTrue(miss.difference.contains("9F37 不可预知数"))
        assertTrue(miss.difference.contains("实际 AABBCCDD"))
        assertEquals("6D00", miss.response)
        assertTrue(e.exchange(gpo.command).matched)
    }
    @Test fun maskedSuccessStillWarnsAndKeepsFixedResponse() {
        val e = engine(listOf(select, gpo.copy(mode = MatchMode.MASK, mask = "FF".repeat(7) + "00".repeat(10))))
        e.exchange(select.command)
        val hit = e.exchange(changed)
        assertTrue(hit.matched); assertEquals("9000", hit.response)
        assertTrue(hit.difference.contains("规则已匹配"))
        assertTrue(hit.difference.contains("不会重算"))
    }
    @Test fun usesCdolOneThenTwoAndResetClearsDefinitions() {
        val record = ApduRule("00B2011400", "700A8C039F02068D039F37049000")
        val first = ApduRule("80AE800006000000000001", "9000")
        val second = ApduRule("80AE40000411223344", "9000")
        val e = engine(listOf(record, first, second))
        e.exchange(record.command)
        val miss = e.exchange("80AE800006000000000002")
        assertTrue(miss.difference.contains("CDOL1")); assertTrue(miss.difference.contains("授权金额"))
        assertTrue(e.exchange(first.command).matched)
        val miss2 = e.exchange("80AE400004AABBCCDD")
        assertTrue(miss2.difference.contains("CDOL2")); assertTrue(miss2.difference.contains("9F37"))
        e.reset(); e.exchange(record.command)
        assertTrue(e.exchange("80AE800006000000000002").difference.contains("CDOL1"))
    }
    @Test fun missingOrInvalidDefinitionAndLcFailClosedWithoutChangingReply() {
        val e = engine(listOf(select.copy(response = "6F0A9F38079F02069F37049000"), gpo))
        e.exchange(select.command)
        assertTrue(e.exchange(changed).difference.contains("尚未获得"))
        val valid = engine(listOf(select, gpo)); valid.exchange(select.command)
        val miss = valid.exchange(changed.dropLast(2))
        assertEquals("6D00", miss.response); assertTrue(miss.difference.contains("Lc 与命令长度不一致"))
        val mismatch = engine(listOf(select.copy(response = "9F38039F02059000"), gpo))
        mismatch.exchange(select.command)
        assertTrue(mismatch.exchange(changed).difference.contains("DOL 要求 5 字节"))
    }
    @Test fun selectingAgainClearsStaleDefinitionsEvenWhenSelectFails() {
        val e = engine(listOf(select, gpo), false)
        e.exchange(select.command)
        assertTrue(e.exchange(changed).difference.contains("授权金额"))
        e.exchange("00A4040005F00908070600")
        assertTrue(e.exchange(changed).difference.contains("尚未获得"))
    }
    @Test fun contentModeDoesNotGuessAmongMultipleGpoCandidates() {
        val e = engine(listOf(select, gpo, gpo.copy(command = changed)), false)
        e.exchange(select.command)
        val miss = e.exchange("80A800000C830A000000000003FFFFFFFF")
        assertFalse(miss.matched); assertTrue(miss.difference.contains("无唯一诊断候选"))
        assertFalse(miss.difference.contains("授权金额"))
    }
    @Test fun conflictingDefinitionsAndMultiApplicationReselectAreNotMixed() {
        val conflicting = select.copy(response = "9F38039F02069F38039F37049000")
        val e = engine(listOf(conflicting, gpo))
        e.exchange(select.command)
        assertTrue(e.exchange(changed).difference.contains("定义存在冲突"))
    }
}
