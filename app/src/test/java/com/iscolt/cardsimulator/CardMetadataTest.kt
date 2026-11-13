package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.importer.*
import org.junit.Assert.*
import org.junit.Test

class CardMetadataTest {
    private val legacy = """{"cardID":"sample","cardImg":"示例","cardType":"Discover","category":"L2","AID":"F001020304","config":"CONF01","amount":"0.01","PDOL":"112233","CDOL":"ABCD","APDU":"T:00CA000000;C:9000"}"""
    @Test fun legacyConditionsAndWarningsSurviveRoundTripWithoutInventingOutcome() {
        val card = ScriptCodec.parse(legacy).single()
        assertEquals("CONF01", card.metadata.source.configId)
        assertEquals("sample", card.metadata.source.originalCardId)
        assertEquals("112233", card.metadata.scenario.legacyPdol)
        assertEquals("L2", card.metadata.scenario.category)
        assertEquals("0.01", card.metadata.scenario.amount)
        assertEquals("", card.metadata.scenario.expectedResult)
        assertEquals(MatchMode.EXACT, card.rules.single().mode)
        assertEquals(2, card.metadata.warnings.size)
        assertEquals(card, ScriptCodec.parse(ScriptCodec.encode(listOf(card))).single())
    }
    @Test fun fileEvidenceUsesExactBytesAndDoesNotReplaceOriginalEvidence() {
        val bytes = byteArrayOf(97, 98, 99)
        val evidence = ImportEvidence.from(bytes, "config.json")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", evidence.sha256)
        val card = CardFileImporter.parse(legacy, evidence).single()
        assertEquals("config.json", card.metadata.source.fileName)
        val second = CardFileImporter.parse(ScriptCodec.encode(listOf(card)), ImportEvidence.from(byteArrayOf(0), "export.json")).single()
        assertEquals(card.metadata, second.metadata)
        assertEquals(card.metadata, card.copy(id = "copy").metadata)
    }
    @Test fun rejectsCorruptedMetadataInsteadOfSilentlyDroppingIt() {
        val base = ScriptCodec.encode(ScriptCodec.parse(legacy))
        val invalid = listOf(base.replace("0.01", "x".repeat(121)),
            base.replace("旧版 APDU JSON", "x".repeat(121)),
            base.replace("\"sha256\": \"\"", "\"sha256\": \"wrong\""))
        invalid.forEach { value ->
            try { ScriptCodec.parse(value); fail("Invalid metadata accepted") } catch (_: IllegalArgumentException) {}
        }
    }
}
