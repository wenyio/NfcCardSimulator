package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ScriptCodecTest {
    private val sample = """{"id":"x","name":"测试","aids":["F001020304"],"rules":[{"command":"00CA000000","response":"9000"}]}"""
    @Test fun roundTripPreservesRulesAndMetadata() {
        val original = ScriptCodec.parse(sample)
        assertEquals(original, ScriptCodec.parse(ScriptCodec.encode(original)))
    }
    @Test fun rejectsInvalidAidResponseMaskAndDuplicateIds() {
        val invalid = listOf(sample.replace("F001020304", "F001"), sample.replace("9000", "90"),
            sample.replace("\"response\":\"9000\"", "\"response\":\"9000\",\"match\":\"MASK\",\"mask\":\"FF\""),
            "[$sample,$sample]")
        invalid.forEach { input ->
            try { ScriptCodec.parse(input); fail("Invalid script accepted") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun rejectsLegacyAliasesAndSerializedApdu() {
        val old = """{"cardID":"old","cardImg":"Old","AID":"F001020304","APDU":"T:00CA000000;C:9000"}"""
        assertTrue(runCatching { ScriptCodec.parse(old) }.isFailure)
        assertTrue(runCatching { ScriptCodec.parse(sample.replace("rules", "APDU")) }.isFailure)
    }
    @Test fun standardRulesKeepDuplicateCommandsInOrder() {
        val card = ScriptCodec.parse(sample).single()
        val pair = card.copy(ordered = true, rules = listOf(ApduRule("00CA000000", "019000"), ApduRule("00CA000000", "029000")))
        val engine = ScriptEngine(ScriptCodec.parse(ScriptCodec.encode(listOf(pair))).single())
        assertEquals("019000", engine.exchange("00CA000000").response)
        assertEquals("029000", engine.exchange("00CA000000").response)
    }
    @Test fun emptyPersistedCollectionIsReadableButEmptyImportIsRejected() {
        val json = ScriptCodec.encode(emptyList())
        assertTrue(ScriptCodec.parse(json, allowEmpty = true).isEmpty())
        try { ScriptCodec.parse(json); fail() } catch (_: IllegalArgumentException) {}
    }
    @Test fun builtinExamplesHaveValidTlvAndSelectRoutes() {
        val cards = ScriptCodec.parse(File("src/main/assets/demo-cards.json").readText(), true)
        assertEquals(6, cards.size)
        cards.forEach { card ->
            val engine = ScriptEngine(card)
            val ppse = engine.exchange("00A404000E325041592E5359532E444446303100")
            assertTrue(ppse.matched); assertTrue(ppse.response.endsWith("9000"))
            val bytes = Hex.bytes(ppse.response)
            assertEquals(bytes.size - 4, bytes[1].toInt() and 255)
            val aid = card.aids.single()
            val select = "00A40400" + "%02X".format(aid.length / 2) + aid + "00"
            assertTrue(engine.exchange(select).matched)
            assertEquals("6985", engine.exchange("80A8000002830000").response)
        }
    }
}
