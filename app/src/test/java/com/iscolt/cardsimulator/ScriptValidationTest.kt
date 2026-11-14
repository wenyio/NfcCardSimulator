package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.cards.CardEditorDraft
import org.junit.Assert.*
import org.junit.Test

class ScriptValidationTest {
    private val card = CardScript("sample", " 卡片 ", " Discover ", listOf("f001020304", "F001020304"), "说明",
        listOf(ApduRule("00ca000000", "9000", MatchMode.MASK, "ffffffffff")), fallback = "6d00",
        metadata = CardMetadata(source = CardSource(sha256 = "A".repeat(64))), packId = "personal")
    @Test fun directValidationMatchesImportedNormalizationAndPreservesOwnership() {
        val expected = card.copy(name = "卡片", scheme = "Discover", aids = listOf("F001020304"), fallback = "6D00",
            rules = listOf(ApduRule("00CA000000", "9000", MatchMode.MASK, "FFFFFFFFFF")),
            metadata = card.metadata.copy(source = card.metadata.source.copy(sha256 = "a".repeat(64))))
        assertEquals(expected, ScriptValidation.card(card))
        assertEquals(expected.copy(packId = ""), ScriptCodec.parse(ScriptCodec.encode(listOf(card))).single())
        assertEquals(expected, CardEditorDraft.create(card, false).validated())
        assertEquals(" 卡片 ", card.name)
    }
    @Test fun directAndImportedValidationRejectInvalidFieldsAndLimits() {
        val invalid = listOf(card.copy(name = " "), card.copy(id = "bad:id"), card.copy(aids = listOf("F00")),
            card.copy(rules = emptyList()), card.copy(rules = List(1001) { card.rules.single() }),
            card.copy(rules = listOf(card.rules.single().copy(mask = "FF"))), card.copy(fallback = "90"),
            card.copy(metadata = CardMetadata(source = CardSource(sha256 = "bad"))),
            card.copy(metadata = CardMetadata(scenario = TestScenario(amount = "x".repeat(121)))),
            card.copy(metadata = CardMetadata(warnings = List(31) { "warning" })))
        for (value in invalid) {
            assertTrue(value.toString(), runCatching { ScriptValidation.card(value) }.isFailure)
            assertTrue(value.toString(), runCatching { ScriptCodec.parse(ScriptCodec.encode(listOf(value))) }.isFailure)
        }
        assertTrue(runCatching { ScriptValidation.cards(List(201) { card }) }.isFailure)
        assertTrue(runCatching { ScriptValidation.cards(listOf(card, card)) }.isFailure)
    }
    @Test fun draftsAndPacksKeepEncodedSizeLimitAndGeneratedIds() {
        val withoutId = card.copy(id = "")
        assertEquals(ScriptCodec.parse(ScriptCodec.encode(listOf(withoutId))).single().id, ScriptValidation.card(withoutId).id)
        val huge = card.copy(description = "x".repeat(ScriptCodec.MAX_BYTES))
        assertTrue(runCatching { CardEditorDraft.create(huge, false).validated() }.isFailure)
        assertTrue(runCatching { CardPackCodec.validate(CardPack("pack", "包", cards = listOf(huge))) }.isFailure)
        assertTrue(runCatching { CardPackCodec.validate(CardPack("pack", "包", description = "x".repeat(4001))) }.isFailure)
        CardPackCodec.validate(CardPack("empty", "空包"))
    }
    @Test fun exactRuleAndMetadataBoundariesRemainAcceptedAndExcessRejected() {
        val longest = card.copy(rules = listOf(ApduRule("AA".repeat(65540), "9000")),
            metadata = CardMetadata(CardSource("x".repeat(120), "x".repeat(512), "a".repeat(64),
                "x".repeat(120), "x".repeat(240)), TestScenario(purpose = "x".repeat(4000)), List(30) { "x".repeat(2000) }))
        ScriptValidation.card(longest)
        assertTrue(runCatching { ScriptValidation.card(longest.copy(rules = listOf(longest.rules.single().copy(command = longest.rules.single().command + "AA")))) }.isFailure)
        assertTrue(runCatching { ScriptValidation.card(longest.copy(metadata = longest.metadata.copy(warnings = listOf("x".repeat(2001))))) }.isFailure)
        assertTrue(runCatching { ScriptValidation.card(longest.copy(metadata = longest.metadata.copy(scenario = TestScenario(purpose = "x".repeat(4001))))) }.isFailure)
    }
}
