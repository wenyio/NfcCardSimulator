package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.ApduRule
import com.iscolt.cardsimulator.ui.debug.DebugCommandLabel
import org.junit.Assert.*
import org.junit.Test

class DebugCommandLabelTest {
    @Test fun recognizesKnownInstructionsAndKeepsTheirPositionAndHeader() {
        val commands = mapOf(
            "00A4040005F00102030400" to "SELECT", "80A8000002830000" to "GPO",
            "00B2011400" to "READ RECORD", "80AE800000" to "GENERATE AC",
            "0084000008" to "GET CHALLENGE", "0088000004" to "INTERNAL AUTHENTICATE",
            "0082000000" to "EXTERNAL AUTHENTICATE", "80CA9F1700" to "GET DATA"
        )
        commands.entries.forEachIndexed { index, (command, name) ->
            assertEquals("${index + 1} · $name · ${command.take(8)}…",
                DebugCommandLabel.format(index, ApduRule(command, "9000", label = "原脚本规则")))
        }
    }
    @Test fun doesNotGuessPrivateOrIncompleteInstructionsAndPreservesDuplicateSteps() {
        val rule = ApduRule("FFA4040000", "9000", label = "私有指令")
        assertEquals("1 · 私有指令 · FFA40400…", DebugCommandLabel.format(0, rule))
        assertEquals("2 · 私有指令 · FFA40400…", DebugCommandLabel.format(1, rule))
        assertEquals("3 · 短前缀 · 80A8", DebugCommandLabel.format(2, ApduRule("80A8", "9000", label = "短前缀")))
        assertEquals("4 · 规则 4 · 00FF0000", DebugCommandLabel.format(3, ApduRule("00FF0000", "9000")))
    }
}
