package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.cards.CardEditorDraft
import org.junit.Assert.*
import org.junit.Test

class CardEditorDraftTest {
    private val source = CardScript("source", "原卡", "Discover", listOf("A0000001523010"), "说明",
        listOf(ApduRule("00CA000000", "019000"), ApduRule("00CA010000", "029000")), true,
        builtIn = true, metadata = CardMetadata(scenario = TestScenario(purpose = "测试目的", legacyPdol = "9F0206")))
    @Test fun copyingUsesNewIdAndPreservesMetadataWithoutMutatingOriginal() {
        val draft = CardEditorDraft.create(source, true)
        assertNotEquals(source.id, draft.card.id); assertFalse(draft.card.builtIn); assertFalse(draft.dirty)
        draft.card = draft.card.copy(rules = draft.card.rules.reversed())
        assertTrue(draft.dirty); assertEquals("00CA000000", source.rules.first().command)
        assertEquals(source.metadata, draft.validated().metadata)
    }
    @Test fun incompleteInputSurvivesDraftRoundTripAndRemainsUnsaved() {
        val draft = CardEditorDraft.create(null, false)
        draft.card = draft.card.copy(name = "未完成", aids = listOf("F00"), fallback = "Z", scheme = "本地测试")
        val restored = CardEditorDraft.restore(draft.state())
        assertEquals(draft.card, restored.card); assertTrue(restored.dirty)
        assertTrue(runCatching { restored.validated() }.isFailure)
        assertEquals(draft.card.id, restored.card.id)
    }
    @Test fun packOnlyChangeIsDirtyAndRestoresOriginalIdentity() {
        val original = source.copy(builtIn = false, packId = "first")
        val draft = CardEditorDraft.create(original, false, "other")
        assertEquals("first", draft.card.packId); assertFalse(draft.dirty)
        draft.card = draft.card.copy(packId = "second")
        val restored = CardEditorDraft.restore(draft.state())
        assertTrue(restored.dirty); assertEquals(original.key, restored.originalKey)
        assertEquals("second", restored.validated().packId)
        restored.card = restored.card.copy(packId = "first"); assertFalse(restored.dirty)
        val copy = CardEditorDraft.create(original, true, "second")
        assertNull(copy.originalKey); assertEquals("second", copy.card.packId)
    }
}
