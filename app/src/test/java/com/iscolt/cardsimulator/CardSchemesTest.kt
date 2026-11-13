package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.core.CardSchemes
import org.junit.Assert.*
import org.junit.Test

class CardSchemesTest {
    @Test fun fixedNamesDeduplicateAndImportedNamesRemainAvailable() {
        val options = CardSchemes.options(listOf(" visa ", "VISA", "本地测试", " 本地测试 ", "Legacy Network"))
        assertEquals(1, options.count { it.equals("visa", true) })
        assertTrue(options.containsAll(listOf("本地测试", "Legacy Network")))
        assertEquals("Visa", CardSchemes.canonical(" visa "))
        assertEquals("Discover", options[1])
    }
    @Test fun onlySchemesPresentInCurrentPackGetTabs() {
        assertEquals(listOf("全部", "Visa", "本地测试"),
            CardSchemes.categories(listOf("本地测试", "自定义", "全部", "VISA")))
        assertTrue(CardSchemes.categories(listOf("其他")).contains("其他"))
    }
    @Test fun rejectsDuplicateReservedAndInvalidNewNames() {
        for (name in listOf(" visa ", "全部", "自定义", "", "A\nB", "x".repeat(41))) {
            assertTrue(runCatching { CardSchemes.validateNew(name, emptyList()) }.isFailure)
        }
        assertEquals("本地测试", CardSchemes.validateNew(" 本地测试 ", emptyList()))
    }
}
