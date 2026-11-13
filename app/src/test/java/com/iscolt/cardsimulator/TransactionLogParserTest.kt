package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.importer.TransactionLogParser
import org.junit.Assert.*
import org.junit.Test

class TransactionLogParserTest {
    @Test fun wrappedHexAndRepeatedCommandsKeepOrder() {
        val result = TransactionLogParser.parse("# 测试\nt: 00 CA 00\n00 00\nc: 01 90 00\nT:00CA000000\nC:029000").single()
        assertTrue(result.issues.isEmpty())
        val card = result.toCard("测试", "自定义", listOf("F001020304"))
        assertTrue(card.ordered)
        val engine = ScriptEngine(card)
        assertEquals("019000", engine.exchange("00CA000000").response)
        assertEquals("029000", engine.exchange("00CA000000").response)
    }
    @Test fun ppseAndExplicitMarkersSplitTransactions() {
        val pair = "T:00A404000E325041592E5359532E444446303100\nC:9000\n"
        assertEquals(2, TransactionLogParser.parse(pair + pair).size)
        assertEquals(2, TransactionLogParser.parse("T:00CA000000\nC:9000\n---\nT:00CA000000\nC:9000").size)
    }
    @Test fun extractBusinessAidsExcludesPpse() {
        val result = TransactionLogParser.parse("T:00A404000E325041592E5359532E444446303100\nC:9000\nT:00A4040007A000000152301000\nC:9000").single()
        assertEquals(listOf("A0000001523010"), result.aids())
    }
    @Test fun incompleteOrUnknownInputCannotBecomeCard() {
        for (text in listOf("T:00CA000000", "C:9000", "T:ZZ\nC:9000", "T:00CA000000\nC:90", "unexpected\nT:00CA000000\nC:9000")) {
            val result = TransactionLogParser.parse(text).single()
            assertTrue(text, result.issues.isNotEmpty())
            try { result.toCard("错误", "自定义", listOf("F001020304")); fail(text) }
            catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun missingResponseBeforeNextCommandReportsOriginalLine() {
        val result = TransactionLogParser.parse("T:00CA000000\nT:00CA010000\nC:9000").single()
        assertTrue(result.issues.any { it.contains("第 1 行") })
        assertEquals("00CA010000", result.pairs.single().command)
    }
    @Test fun brokenResponseDoesNotPairWithLaterResponse() {
        val result = TransactionLogParser.parse("T:00CA000000\nC:9Z\nC:9000").single()
        assertTrue(result.pairs.isEmpty()); assertTrue(result.issues.size >= 2)
    }
    @Test fun emptyAndOversizeLogsAreRejected() {
        for (text in listOf("# 空日志", " ".repeat(ScriptCodec.MAX_BYTES + 1))) {
            try { TransactionLogParser.parse(text); fail() } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun deliveredDiscoverExamplesParseAndKeepExpectedErrorPaths() {
        val transaction = TransactionLogParser.parse(java.io.File("../examples/discover-session.txt").readText()).single()
        assertTrue(transaction.issues.isEmpty())
        assertEquals("6985", transaction.pairs.last().response)
        val cards = ScriptCodec.parse(java.io.File("../examples/discover-error-scenarios.json").readText())
        assertEquals("6A82", ScriptEngine(cards[0]).exchange("00A4040007A000000152301000").response)
        assertEquals("6A80", ScriptEngine(cards[1]).exchange("80A8000002830000").response)
    }
}
