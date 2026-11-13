package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.importer.*
import org.junit.Assert.*
import org.junit.Test

class CardMetadataTest {
    private val sample = """{"id":"sample","name":"示例","scheme":"Discover","aids":["F001020304"],"rules":[{"command":"00CA000000","response":"9000"}],"metadata":{"source":{"format":"卡片 JSON","originalCardId":"sample","configId":"CONF01"},"scenario":{"category":"L2","amount":"0.01","legacyPdol":"112233","legacyCdol":"ABCD"},"warnings":["固定回复不计算密文","参考数据不限制交易"]}}"""
    @Test fun explicitConditionsAndWarningsSurviveRoundTripWithoutInventingOutcome() {
        val card = ScriptCodec.parse(sample).single()
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
        val card = CardFileImporter.parse(sample, evidence).single()
        assertEquals("config.json", card.metadata.source.fileName)
        val second = CardFileImporter.parse(ScriptCodec.encode(listOf(card)), ImportEvidence.from(byteArrayOf(0), "export.json")).single()
        assertEquals(card.metadata, second.metadata)
        assertEquals(card.metadata, card.copy(id = "copy").metadata)
    }
    @Test fun rejectsCorruptedMetadataInsteadOfSilentlyDroppingIt() {
        val base = ScriptCodec.encode(ScriptCodec.parse(sample))
        val invalid = listOf(base.replace("0.01", "x".repeat(121)),
            base.replace("卡片 JSON", "x".repeat(121)),
            base.replace("\"sha256\": \"\"", "\"sha256\": \"wrong\""))
        invalid.forEach { value ->
            try { ScriptCodec.parse(value); fail("Invalid metadata accepted") } catch (_: IllegalArgumentException) {}
        }
    }
}
