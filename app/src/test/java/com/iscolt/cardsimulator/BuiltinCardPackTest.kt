package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.data.BuiltinCardCatalog
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BuiltinCardPackTest {
    private fun packs() = BuiltinCardCatalog.loadPacks { File("src/main/assets/$it").readText() }
    @Test fun catalogContainsOnlySixSelfAuthoredConnectivityDemos() {
        val packs = packs()
        assertEquals(listOf(CardPacks.DEMO), packs.map { it.id })
        val cards = packs.single().cards
        assertEquals(6, cards.size); assertEquals(6, cards.map { it.key }.distinct().size)
        assertTrue(cards.all { it.builtIn && it.id.startsWith("demo-") })
        assertEquals(setOf("Discover", "Visa", "Mastercard", "JCB", "Amex", "UnionPay"), cards.map { it.scheme }.toSet())
        assertEquals(listOf("demo-cards.json"), BuiltinCardCatalog.assetNames)
        assertFalse(File("src/main/assets/bctc-cards.json").exists())
    }
    @Test fun demosReplayAndRemainConnectivityChecksRatherThanTransactionCards() {
        packs().single().cards.forEach { card ->
            val engine = ScriptEngine(card)
            card.rules.forEach { rule ->
                val result = engine.exchange(rule.command)
                assertTrue(result.matched); assertEquals(rule.response, result.response)
            }
            assertEquals("6985", engine.exchange("80A8000002830000").response)
            assertEquals("6D00", engine.exchange("00FF000000").response)
            assertEquals(card.copy(builtIn = false, packId = ""), ScriptCodec.parse(ScriptCodec.encode(listOf(card))).single())
        }
    }
    @Test fun catalogRejectsDuplicateCardIdsInsidePack() {
        val text = File("src/main/assets/demo-cards.json").readText()
        val json = org.json.JSONObject(text)
        val cards = json.getJSONArray("cards")
        cards.put(cards.getJSONObject(0))
        assertTrue(runCatching { BuiltinCardCatalog.load { json.toString() } }.isFailure)
    }
}
