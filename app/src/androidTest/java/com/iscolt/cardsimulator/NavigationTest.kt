package com.iscolt.cardsimulator

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.tabs.TabLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @org.junit.Before fun chooseDemoPack() {
        (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SimulatorApp).store.switchPack(com.iscolt.cardsimulator.core.CardPacks.DEMO)
    }
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun visible(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.tag?.toString()?.startsWith("tab-page:") == true) {
                val pager = current.parent as androidx.viewpager.widget.ViewPager
                return current.tag == "tab-page:${pager.currentItem}"
            }
            current = current.parent as? View
        }
        return view.isShown
    }
    private fun open(root: View) = views(root).first { visible(it) && it.contentDescription?.toString() == "打开卡片：Discover · 连通性演示" }.performClick()
    private fun tabs(root: View) = views(root).filterIsInstance<TabLayout>().single()
    private fun select(root: View, index: Int) = views(root).filterIsInstance<androidx.viewpager.widget.ViewPager>().single().setCurrentItem(index, false)
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name)
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun screenshotsAndDetailNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            screenshot("list-03.png")
            scenario.onActivity { open(it.window.decorView) }
            screenshot("detail-03.png")
            scenario.onActivity { activity ->
                assertTrue(tabs(activity.window.decorView).getTabAt(0)?.text == "详情")
            }
        }
    }
    @Test fun detailExitAndBackgroundStopSimulationAndListHasNoStartButton() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SimulatorApp
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertFalse(app.store.enabled)
                assertFalse(views(root).filterIsInstance<android.widget.TextView>().any { it.isShown && it.text.toString().contains("模拟已停止") })
                assertFalse(views(root).filterIsInstance<Button>().any { it.text == "开始模拟" })
                assertFalse(views(root).filterIsInstance<android.widget.TextView>().any {
                    it.text.toString().contains("按步骤") || it.text.toString().contains("按内容")
                })
                open(root); assertEquals("connectivity-demo:demo-discover", app.store.selectedId)
                assertTrue(tabs(root).getTabAt(0)?.text == "详情")
                assertTrue(views(root).filterIsInstance<android.widget.TextView>().any {
                    it.text.toString() == "内容匹配 · 未匹配时返回 6D00"
                })
                assertTrue(views(root).any { it.contentDescription == "执行方式说明" })
                // 模拟器没有 HCE，用已启用的 store 状态验证导航出口确实关闭服务开关。
                app.store.setEnabled(true)
                activity.onBackPressedDispatcher.onBackPressed()
                assertFalse(app.store.enabled)
            }
            scenario.onActivity { activity -> open(activity.window.decorView); app.store.setEnabled(true) }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            assertFalse(app.store.enabled)
        }
    }
    @Test fun detailToolsDoNotResetSessionAndRecreationRetainsDetail() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SimulatorApp
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView; open(root)
                val revision = app.store.sessionRevision
                select(root, 1)
                select(root, 2)
                select(root, 0)
                assertEquals(revision, app.store.sessionRevision)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(tabs(activity.window.decorView).getTabAt(0)?.text == "详情")
                activity.onBackPressedDispatcher.onBackPressed(); assertFalse(app.store.enabled)
            }
        }
    }
    @Test fun currentCardLogClearButtonPreservesOtherCardLogs() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SimulatorApp
        app.store.select("demo-visa"); app.store.log("QA", "other-card-record")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView; open(root)
                app.store.log("QA", "current-card-record")
                select(root, 2)
                views(root).filterIsInstance<Button>().first { it.text == "清空本卡日志" }.performClick()
                assertFalse(app.store.logs().any { it.cardId == "connectivity-demo:demo-discover" })
                assertTrue(app.store.logs().any { it.message == "other-card-record" })
            }
        }
    }
    @Test fun categoryTabsFilterAndSwitchReflectsActualSimulationState() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SimulatorApp
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                var tabs = views(root).filterIsInstance<com.google.android.material.tabs.TabLayout>().single()
                assertEquals(com.google.android.material.tabs.TabLayout.MODE_SCROLLABLE, tabs.tabMode)
                select(root, (0 until tabs.tabCount).first { tabs.getTabAt(it)?.text == "Visa" })
                assertTrue(views(root).any { visible(it) && it.contentDescription?.toString() == "打开卡片：Visa · 连通性演示" })
                assertFalse(views(root).any { visible(it) && it.contentDescription?.toString() == "打开卡片：Discover · 连通性演示" })
                tabs = views(root).filterIsInstance<com.google.android.material.tabs.TabLayout>().single()
                select(root, 0); open(root)
                app.store.setEnabled(true)
                // 切回详情重绘，将受控启用状态同步到开关。
                select(root, 1); select(root, 0)
                val current = views(root).filterIsInstance<androidx.appcompat.widget.SwitchCompat>().single()
                assertTrue(current.isChecked)
                current.performClick(); assertFalse(app.store.enabled); assertFalse(current.isChecked)
                assertTrue(views(root).any { it.contentDescription?.toString() == "设备设置" })
            }
        }
    }
}
