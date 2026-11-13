package com.iscolt.cardsimulator

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.cards.CardPackManagerActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
class CardPackManagerTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SimulatorApp
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun windows() = android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { views(it) }
    private fun button(title: String) = windows().filterIsInstance<Button>().last { it.text.toString() == title }.performClick()
    private fun click(activity: Activity, description: String) = views(activity.window.decorView)
        .first { it.contentDescription == description }.performClick()
    private fun popupOptions(): List<String> {
        val list = windows().filterIsInstance<ListView>().last()
        return (0 until list.adapter.count).map { list.adapter.getItem(it).toString() }
    }
    private fun menu(title: String) {
        val list = windows().filterIsInstance<ListView>().last()
        val index = (0 until list.adapter.count).first { list.adapter.getItem(it).toString() == title }
        list.performItemClick(list.getChildAt(index - list.firstVisiblePosition), index, list.adapter.getItemId(index))
    }
    private fun addPack(id: String) = app.store.importPack(CardPack(id, "管理测试", "场景说明", cards = listOf(
        CardScript("one", "测试卡", "Discover", listOf("F001020304"), "", listOf(ApduRule("00A40400", "9000")))
    )))

    @Test fun managerRenamesValidatesAndDeletesCurrentPackAfterRecreation() {
        val id = "qa-manager-" + System.nanoTime()
        addPack(id)
        try {
            ActivityScenario.launch(CardPackManagerActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val all = views(activity.window.decorView)
                    assertTrue(all.any { it.contentDescription == "添加卡包" })
                    assertTrue(all.any { it.contentDescription == "当前使用卡包：管理测试" })
                    click(activity, "卡包操作：管理测试")
                }
                scenario.onActivity { menu("重命名") }
                scenario.onActivity {
                    val input = windows().filterIsInstance<EditText>().last()
                    input.setText(""); button("保存")
                    assertNotNull(input.error)
                    assertEquals("管理测试", app.store.currentPack().name)
                    input.setText("已重命名"); button("保存")
                    assertEquals("已重命名", app.store.currentPack().name)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertTrue(views(activity.window.decorView).any { it.contentDescription == "当前使用卡包：已重命名" })
                    click(activity, "卡包操作：已重命名")
                }
                scenario.onActivity { menu("删除卡包") }
                scenario.onActivity { button("取消"); assertTrue(app.store.packs().any { it.id == id }) }
                scenario.onActivity { activity -> click(activity, "卡包操作：已重命名") }
                scenario.onActivity { menu("删除卡包") }
                scenario.onActivity { button("删除") }
                scenario.onActivity { activity ->
                    assertFalse(app.store.packs().any { it.id == id })
                    assertEquals(CardPacks.DEMO, app.store.activePackId)
                    assertFalse(app.store.enabled)
                    assertFalse(views(activity.window.decorView).any { it.tag == "card-pack:" + id })
                }
            }
        } finally { if (app.store.packs().any { it.id == id }) app.store.deletePack(id); app.store.switchPack(CardPacks.DEMO) }
    }

    @Test fun builtinAndPersonalMenusExposeOnlyAllowedOperations() {
        app.store.switchPack(CardPacks.DEMO)
        ActivityScenario.launch(CardPackManagerActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> click(activity, "卡包操作：连通性演示") }
            scenario.onActivity { assertEquals(listOf("查看 JSON", "导出卡包"), popupOptions()) }
        }
        ActivityScenario.launch(CardPackManagerActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> click(activity, "卡包操作：" + app.store.packs().first { it.id == CardPacks.PERSONAL }.name) }
            scenario.onActivity { assertEquals(listOf("查看 JSON", "导出卡包", "重命名"), popupOptions()) }
        }
    }

    @Test fun toolbarOpensManagerAndUsingPackRefreshesHomeWithoutSimulation() {
        app.store.switchPack(CardPacks.PERSONAL)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> click(activity, "管理卡包：" + app.store.currentPack().name) }
                // Activity 转场可能晚于主线程空闲，等待实际 RESUMED，避免将时序波动误判为导航失败。
                var opened: CardPackManagerActivity? = null
                val deadline = android.os.SystemClock.uptimeMillis() + 10000
                while (opened == null && android.os.SystemClock.uptimeMillis() < deadline) {
                    instrumentation.runOnMainSync {
                        opened = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<CardPackManagerActivity>().singleOrNull()
                    }
                    if (opened == null) android.os.SystemClock.sleep(50)
                }
                instrumentation.runOnMainSync {
                    val manager = requireNotNull(opened) { "管理页未进入前台" }
                    assertFalse(views(manager.window.decorView).filterIsInstance<Button>().any { it.text.toString() == "使用" })
                    assertTrue(views(manager.window.decorView).first { it.contentDescription == "使用卡包：连通性演示" } is LinearLayout)
                    val decor = manager.window.decorView
                    val bitmap = android.graphics.Bitmap.createBitmap(decor.width, decor.height, android.graphics.Bitmap.Config.ARGB_8888)
                    decor.draw(android.graphics.Canvas(bitmap))
                    java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "pack-manager.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }; bitmap.recycle()
                    click(manager, "使用卡包：连通性演示")
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals(CardPacks.DEMO, app.store.activePackId)
                    assertFalse(app.store.enabled); assertNull(app.store.selectedId)
                    assertTrue(views(activity.window.decorView).any { it.contentDescription == "管理卡包：连通性演示" })
                }
            }
        } finally { app.store.switchPack(CardPacks.DEMO) }
    }
}
