package com.iscolt.cardsimulator

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.data.*
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardPackDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun card(name: String) = CardScript("same", name, "Discover", listOf("F001020304"), "", listOf(ApduRule("00A40400", "9000")))
    private fun dialogViews() = android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { views(it) }
    private fun clickDialogButton(title: String) = dialogViews().filterIsInstance<Button>().last { it.text.toString() == title }.performClick()
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun importPreviewRequiresConfirmationAndManagerSwitches() {
        val app = context.applicationContext as SimulatorApp
        val id = "qa-dialog-" + System.nanoTime()
        val pack = CardPack(id, "导入预览测试", cards = listOf(card("预览卡")))
        try {
            app.store.switchPack(CardPacks.DEMO)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    com.iscolt.cardsimulator.ui.cards.CardPackImportDialog(com.iscolt.cardsimulator.ui.UiKit(activity), app.store, {}, { error(it) }).show(pack)
                    assertNull(app.store.packs().firstOrNull { it.id == id })
                    clickDialogButton("下一步")
                }
                scenario.onActivity {
                    assertNull(app.store.packs().firstOrNull { it.id == id })
                    clickDialogButton("确认导入")
                    assertEquals(id, app.store.activePackId)
                    assertEquals("预览卡", app.store.currentCards().single().name)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    views(activity.window.decorView).first { it.contentDescription == "管理卡包：导入预览测试" }.performClick()
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val manager = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                        .filterIsInstance<com.iscolt.cardsimulator.ui.cards.CardPackManagerActivity>().single()
                    views(manager.window.decorView).first { it.contentDescription == "使用卡包：连通性演示" }.performClick()
                    assertEquals(CardPacks.DEMO, app.store.activePackId)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    java.io.File(context.getExternalFilesDir(null), "card-packs-home.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    com.iscolt.cardsimulator.ui.cards.CardPackImportDialog(com.iscolt.cardsimulator.ui.UiKit(activity), app.store, {}, { error(it) })
                        .show(pack.copy(cards = listOf(card("更新预览卡"))))
                    clickDialogButton("下一步")
                }
                scenario.onActivity {
                    assertEquals("预览卡", app.store.packs().first { it.id == id }.cards.single().name)
                    clickDialogButton("确认导入")
                    assertEquals("更新预览卡", app.store.currentCards().single().name)
                }
            }
        } finally { if (app.store.packs().any { it.id == id }) app.store.deletePack(id); app.store.switchPack(CardPacks.DEMO) }
    }
    @Test fun isolatedIdsReplacementPersistenceAndBuiltinProtection() {
        // 专用上下文只隔离本测试的数据文件，不清除用户安装实例的数据。
        val dir = java.io.File(context.cacheDir, "pack-test-" + java.util.UUID.randomUUID()).apply { mkdirs() }
        val fixed = object : android.content.ContextWrapper(context) { override fun getFilesDir() = dir }
        val repo = CardPackRepository(fixed)
        val first = repo.importPack(CardPack("qa-one", "一", cards = listOf(card("甲"))))
        val second = repo.importPack(CardPack("qa-two", "二", cards = listOf(card("乙"))))
        assertNotEquals(first.cards.single().key, second.cards.single().key)
        assertTrue(runCatching { repo.importPack(first) }.isFailure)
        assertTrue(runCatching { repo.importPack(first, CardPacks.DEMO) }.isFailure)
        repo.importPack(first.copy(cards = listOf(card("更新"))), first.id)
        assertEquals("乙", repo.find(second.id)!!.cards.single().name)
        assertEquals("更新", CardPackRepository(fixed).find(first.id)!!.cards.single().name)
        repo.rename(first.id, "新名称"); assertEquals("新名称", repo.find(first.id)!!.name)
        repo.delete(first.id); assertNull(repo.find(first.id)); assertNotNull(repo.find(second.id))
        assertTrue(runCatching { repo.delete(CardPacks.PERSONAL) }.isFailure)
    }
    @Test fun migrateLegacyCardsAndKeepFileAsBackup() {
        val dir = java.io.File(context.cacheDir, "pack-migration-" + java.util.UUID.randomUUID()).apply { mkdirs() }
        java.io.File(dir, "cards.json").writeText(ScriptCodec.encode(listOf(card("旧卡"))))
        val ctx = object : android.content.ContextWrapper(context) { override fun getFilesDir() = dir }
        val repo = CardPackRepository(ctx)
        assertEquals("旧卡", repo.find(CardPacks.PERSONAL)!!.cards.single().name)
        assertTrue(java.io.File(dir, "cards.json").exists())
        assertEquals(1, CardPackRepository(ctx).find(CardPacks.PERSONAL)!!.cards.size)
    }
    @Test fun toolbarSwitchFiltersCardsAndRemembersPack() {
        val app = context.applicationContext as SimulatorApp
        app.store.switchPack(CardPacks.DEMO)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val all = views(activity.window.decorView)
                assertTrue(all.any { it.contentDescription == "管理卡包：连通性演示" })
                val page = all.first { it.tag == "tab-page:0" }
                assertEquals(6, views(page).count { it.contentDescription?.toString()?.startsWith("打开卡片：") == true })
                val tabs = all.filterIsInstance<TabLayout>().single()
                assertFalse((0 until tabs.tabCount).any { tabs.getTabAt(it)?.text == "自定义" })
                app.store.switchPack(CardPacks.DEMO)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val all = views(activity.window.decorView)
                assertTrue(all.any { it.contentDescription == "管理卡包：连通性演示" })
                val page = all.first { it.tag == "tab-page:0" }
                assertEquals(6, views(page).count { it.contentDescription?.startsWith("打开卡片：") == true })
                views(page).first { it.contentDescription?.startsWith("打开卡片：") == true }.performClick()
            }
            scenario.onActivity { activity ->
                assertTrue(app.store.selectedId!!.startsWith(CardPacks.DEMO + ":"))
                activity.onBackPressedDispatcher.onBackPressed(); assertFalse(app.store.enabled)
            }
        }
        app.store.switchPack(CardPacks.DEMO)
    }
}
