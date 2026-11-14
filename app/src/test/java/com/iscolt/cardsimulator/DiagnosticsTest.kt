package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.diagnostics.TlvDecoder
import com.iscolt.cardsimulator.diagnostics.BerTlv
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {
    @Test fun sharedParserPreservesNestedDisplayAndLongLengths() {
        val response = "6f0e8407a0000001523010a5035001419000"
        assertEquals(listOf("SW=9000", "6F [14] 8407A0000001523010A503500141",
            "  84 [7] A0000001523010", "  A5 [3] 500141", "    50 [1] 41").joinToString(System.lineSeparator()),
            TlvDecoder.decodeResponse(response))
        val value = "AB".repeat(128)
        assertEquals("SW=9000" + System.lineSeparator() + "5A [128] $value",
            TlvDecoder.decodeResponse("5A8180" + value + "9000"))
        assertEquals(listOf(0, 1, 1, 2), BerTlv.parse(response.uppercase().dropLast(4)).map { it.depth })
    }
    @Test fun validPrefixDoesNotHideMalformedTrailingData() {
        for (payload in listOf("9F38039F02065A02FF", "9F38039F02069F", "9F38039F02065A80")) {
            assertTrue(payload, runCatching { BerTlv.parse(payload) }.isFailure)
            assertTrue(payload, TlvDecoder.decodeResponse(payload + "9000").startsWith("非完整 BER-TLV："))
        }
    }
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
