package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.diagnostics.TlvDecoder
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {
    @Test fun nestedTlvAndStatusAreDecoded() {
        val result = TlvDecoder.decodeResponse("6F098407A00000015230109000")
        assertTrue(result.contains("SW=9000")); assertTrue(result.contains("84 [7] A0000001523010"))
        assertEquals("SW=6985", TlvDecoder.decodeResponse("6985"))
    }
    @Test fun truncatedLengthsAndExcessNestingFailWithoutCrashing() {
        for (input in listOf("900", "6F81909000", "9F9000", "6F8000009000"))
            assertTrue(input, TlvDecoder.decodeResponse(input).startsWith("非完整"))
        var nested = "8400"
        repeat(14) { nested = "6F" + "%02X".format(nested.length / 2) + nested }
        assertTrue(TlvDecoder.decodeResponse(nested + "9000").contains("嵌套"))
    }
    @Test fun differenceRespectsMaskAndDoesNotAdvance() {
        val rule = ApduRule("80CA0000021234", "9000", MatchMode.MASK, "FFFFFFFFFF00F0")
        val engine = ScriptEngine(CardScript("x", "x", "", listOf("F001020304"), "", listOf(rule), true))
        val miss = engine.exchange("80CA000002AB4F")
        assertTrue(miss.difference.contains("字节 7")); assertFalse(miss.difference.contains("字节 6"))
        assertTrue(engine.exchange("80CA000002AB3F").matched)
        assertTrue(engine.exchange(rule.command).difference.contains("所有步骤"))
    }
    @Test fun exactLengthMismatchShowsExpectedAndActual() {
        val rule = ApduRule("00CA000000", "9000")
        val engine = ScriptEngine(CardScript("x", "x", "", listOf("F001020304"), "", listOf(rule), true))
        assertTrue(engine.exchange("00CA00000000").difference.contains("期望 5，实际 6"))
    }
}
