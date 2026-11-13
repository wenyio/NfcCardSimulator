package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import org.junit.Assert.*
import org.junit.Test

class ScriptEngineTest {
    private fun card(rules: List<ApduRule>, ordered: Boolean = false) =
        CardScript("test", "test", "custom", listOf("F001020304"), "", rules, ordered)
    @Test fun repeatedCommandsFollowSessionOrderAndReset() {
        val engine = ScriptEngine(card(listOf(ApduRule("80AE000000", "019000"),
            ApduRule("80AE000000", "029000")), true))
        assertEquals("019000", engine.exchange("80AE000000").response)
        assertEquals("6D00", engine.exchange("00B2010C00").response)
        assertEquals("029000", engine.exchange("80AE000000").response)
        assertFalse(engine.exchange("80AE000000").matched)
        engine.reset()
        assertEquals("019000", engine.exchange("80AE000000").response)
    }
    @Test fun maskIgnoresOnlySelectedBitsAndRequiresSameLength() {
        val engine = ScriptEngine(card(listOf(ApduRule("80CA0000021234", "9000", MatchMode.MASK, "FFFFFFFFFF00F0"))))
        assertTrue(engine.exchange("80CA000002AB3F").matched)
        assertFalse(engine.exchange("80CA000002AB4F").matched)
        assertFalse(engine.exchange("80CA000002AB3F00").matched)
    }
    @Test fun matchingIsExactUnlessExplicitlyConfigured() {
        val engine = ScriptEngine(card(listOf(ApduRule("00A4040005F001020304", "9000"))))
        assertTrue(engine.exchange("00 a4 04 00 05 f0 01 02 03 04").matched)
        assertFalse(engine.exchange("00A4040005F00102030400").matched)
    }
    @Test fun malformedApdusDoNotAdvanceSession() {
        val engine = ScriptEngine(card(listOf(ApduRule("00CA000000", "9000")), true))
        for (input in listOf("", "ABC", "00CAZZ0000", "0000")) assertEquals("6700", engine.exchange(input).response)
        assertTrue(engine.exchange("00CA000000").matched)
    }
    @Test fun hexConversionPreservesUnsignedBytes() {
        assertEquals("0080FF", Hex.text(Hex.bytes("00 80 ff")))
    }
}
