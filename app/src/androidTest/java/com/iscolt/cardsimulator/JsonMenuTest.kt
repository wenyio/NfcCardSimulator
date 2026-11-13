package com.iscolt.cardsimulator

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.cards.CardPackManagerActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class JsonMenuTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SimulatorApp
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun windows() = WindowInspector.getGlobalWindowViews().flatMap { views(it) }
    private fun click(activity: Activity, description: String) = views(activity.window.decorView)
        .first { it.contentDescription == description }.performClick()
    private fun menu(title: String) {
        val list = windows().filterIsInstance<ListView>().last()
        val index = (0 until list.adapter.count).first { list.adapter.getItem(it).toString() == title }
        list.performItemClick(list.getChildAt(index - list.firstVisiblePosition), index, list.adapter.getItemId(index))
    }
    private fun json() = windows().filterIsInstance<TextView>().single { it.contentDescription == "JSON 内容" }.also {
        assertTrue(it.isTextSelectable)
    }.text.toString()
    private fun close() = windows().filterIsInstance<Button>().last { it.text == "关闭" }.performClick()

    @Test fun packJsonContainsMetadataAndAllCardsIncludingEmptyPack() {
        val empty = app.store.createPack("JSON 空包测试", "空包说明")
        try {
            ActivityScenario.launch(CardPackManagerActivity::class.java).use { scenario ->
                for (pack in listOf(app.store.packs().first { it.id == CardPacks.DEMO }, empty)) {
                    scenario.onActivity { click(it, "卡包操作：" + pack.name) }
                    scenario.onActivity { menu("查看 JSON") }
                    scenario.onActivity {
                        val parsed = CardPackCodec.parse(json(), allowEmpty = true)
                        assertEquals(pack.id, parsed.id); assertEquals(pack.name, parsed.name)
                        assertEquals(pack.description, parsed.description)
                        assertEquals(pack.cards.map { it.id }, parsed.cards.map { it.id })
                        assertFalse(app.store.enabled)
                        close()
                    }
                }
            }
        } finally { app.store.deletePack(empty.id); app.store.switchPack(CardPacks.DEMO) }
    }

    @Test fun listAndDetailMenusShowIdenticalCardJsonWithoutStartingFromList() {
        app.store.switchPack(CardPacks.DEMO)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val card = app.store.currentCards().first { it.id == "demo-discover" }
                scenario.onActivity { click(it, "更多操作：" + card.name) }
                scenario.onActivity { menu("查看 JSON") }
                scenario.onActivity {
                    assertEquals(ScriptCodec.encode(listOf(card)), json())
                    assertFalse(app.store.enabled); assertNull(app.store.selectedId)
                    close()
                    click(it, "打开卡片：" + card.name)
                }
                scenario.onActivity { click(it, "卡片操作") }
                scenario.onActivity { menu("查看 JSON") }
                scenario.onActivity { assertEquals(ScriptCodec.encode(listOf(card)), json()); close() }
            }
        } finally { app.store.switchPack(CardPacks.DEMO) }
    }
}
