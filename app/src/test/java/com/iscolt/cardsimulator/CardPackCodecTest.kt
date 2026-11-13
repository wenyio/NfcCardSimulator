package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.cards.CardEditorDraft
import org.junit.Assert.*
import org.junit.Test

class CardPackCodecTest {
    private val card = CardScript("same", "测试", "Discover", listOf("F001020304"), "", listOf(ApduRule("00A40400", "9000")))
    @Test fun packRoundTripAndSameCardIdHaveIndependentKeys() {
        val first = CardPack("one", "场景一", "条件一", listOf(card)).bound()
        val second = first.copy(id = "two").bound()
        assertEquals(first, CardPackCodec.parse(CardPackCodec.encode(first)))
        assertNotEquals(first.cards.single().key, second.cards.single().key)
        assertEquals(first.cards.single().id, second.cards.single().id)
        assertEquals(card.rules, second.cards.single().rules)
    }
    @Test fun importedFileCannotDeclareItselfBuiltin() {
        val pack = CardPack("sample-pack", "副本", cards = listOf(card), builtIn = true).bound()
        assertFalse(CardPackCodec.parse(CardPackCodec.encode(pack)).builtIn)
        assertFalse(CardPackCodec.parse(CardPackCodec.encode(pack)).cards.single().builtIn)
    }
    @Test fun rejectsInvalidEnvelopeAndDuplicateLocalIds() {
        val valid = CardPackCodec.encode(CardPack("one", "测试", cards = listOf(card)))
        for (text in listOf(valid.replace("one", "bad:id"), valid.replace("测试", ""),
            org.json.JSONObject(valid).put("schemaVersion", 9).toString(),
            CardPackCodec.encode(CardPack("one", "测试", cards = listOf(card, card))))) {
            assertTrue(text, runCatching { CardPackCodec.parse(text) }.isFailure)
        }
    }
    @Test fun cardFileGetsIndependentPackAndDraftRetainsOwnership() {
        val file = ScriptCodec.encode(listOf(card))
        val pack = CardPackCodec.parse(file, "从文件命名")
        assertEquals("从文件命名", pack.name)
        assertNotEquals(pack.id, CardPackCodec.parse(file).id)
        val edit = CardEditorDraft.create(pack.cards.single(), false)
        assertEquals(pack.id, edit.validated().packId)
        assertEquals(pack.id, CardEditorDraft.restore(edit.state()).card.packId)
        assertEquals(CardPacks.PERSONAL, CardEditorDraft.create(pack.cards.single(), true).card.packId)
    }
}
