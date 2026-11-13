package com.iscolt.cardsimulator

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.iscolt.cardsimulator.core.CardPacks
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BuiltinCardPackDeviceTest {
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    @Test fun bundledDemosLoadFilterOpenAndRemainReadonly() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SimulatorApp
        app.store.switchPack(CardPacks.DEMO)
        val builtin = app.store.cards().filter { it.builtIn }
        assertEquals(6, builtin.size)
        val source = builtin.first { it.id == "demo-discover" }
        assertTrue(runCatching { app.store.importCards(listOf(source)) }.isFailure)
        assertTrue(runCatching { app.store.delete(source.key) }.isFailure)
        val copy = source.copy(id = "qa-demo-copy", builtIn = false, packId = CardPacks.PERSONAL)
        try {
            app.store.importCards(listOf(copy))
            assertEquals(source.metadata, app.store.findCard(copy.key)!!.metadata)
        } finally { app.store.delete(copy.key) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val tabs = views(root).filterIsInstance<TabLayout>().single()
                views(root).filterIsInstance<ViewPager>().single().setCurrentItem(
                    (0 until tabs.tabCount).first { tabs.getTabAt(it)?.text == "Amex" }, false)
            }
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val pager = views(root).filterIsInstance<ViewPager>().single()
                val page = views(root).first { it.tag == "tab-page:" + pager.currentItem }
                assertEquals(1, views(page).count { it.contentDescription?.toString()?.startsWith("打开卡片：") == true })
                views(page).first { it.contentDescription == "打开卡片：Amex · 连通性演示" }.performClick()
            }
            scenario.onActivity { activity ->
                val texts = views(activity.window.decorView).filterIsInstance<TextView>()
                assertEquals(CardPacks.DEMO + ":demo-amex", app.store.selectedId)
                assertTrue(texts.any { it.text.toString() == "复制后编辑" })
                activity.onBackPressedDispatcher.onBackPressed(); assertFalse(app.store.enabled)
            }
        }
    }
}
