package com.iscolt.cardsimulator

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.cards.CardEditorActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardEditorNavigationTest {
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun field(activity: CardEditorActivity, hint: String) = views(activity.window.decorView)
        .filterIsInstance<EditText>().first { it.hint?.toString() == hint }
    private fun button(activity: CardEditorActivity, title: String) = views(activity.window.decorView)
        .filterIsInstance<Button>().first { it.text.toString() == title }
    @Test fun copyRuleSubpageAndRecreationPreserveDraftAndSaveMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as SimulatorApp
        val source = CardScript("qa-editor-source", "页面测试原卡", "Local Network", listOf("F001020304"), "原说明",
            listOf(ApduRule("00CA000000", "019000", label = "第一条")),
            metadata = CardMetadata(scenario = TestScenario(purpose = "保留场景", legacyPdol = "9F0206")))
        app.store.saveEditedCard(source.copy(packId = CardPacks.PERSONAL), null)
        try {
            ActivityScenario.launch<CardEditorActivity>(Intent(context, CardEditorActivity::class.java)
                .putExtra("cardId", source.id).putExtra("copy", true)).use { scenario ->
                scenario.onActivity { activity ->
                    field(activity, "卡片名称").setText("页面测试副本")
                    val scheme = views(activity.window.decorView).filterIsInstance<Spinner>().single { it.contentDescription == "卡组织" }
                    assertEquals("Local Network", scheme.selectedItem)
                    field(activity, "说明").setText("未保存的说明")
                    views(activity.window.decorView).filterIsInstance<Spinner>().single { it.contentDescription == "执行方式" }.setSelection(1)
                    views(activity.window.decorView).first { it.contentDescription == "编辑规则 1" }.performClick()
                    field(activity, "规则说明").setText("修改后的规则")
                    field(activity, "命令 C-APDU（十六进制）").setText("00CA01Z")
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals("00CA01Z", field(activity, "命令 C-APDU（十六进制）").text.toString())
                    button(activity, "保存规则").performClick()
                    assertEquals("00CA01Z", field(activity, "命令 C-APDU（十六进制）").text.toString())
                    field(activity, "命令 C-APDU（十六进制）").setText("00CA010000")
                    button(activity, "保存规则").performClick()
                    assertEquals("页面测试副本", field(activity, "卡片名称").text.toString())
                    assertEquals("顺序匹配", views(activity.window.decorView).filterIsInstance<Spinner>().single { it.contentDescription == "执行方式" }.selectedItem)
                    assertEquals("未保存的说明", field(activity, "说明").text.toString())
                }
                scenario.recreate()
                scenario.onActivity { activity -> button(activity, "保存卡片").performClick() }
                awaitSaved { app.store.cards().any { it.name == "页面测试副本" } }
            }
            val copied = app.store.cards().single { it.name == "页面测试副本" }
            assertNotEquals(source.id, copied.id); assertEquals(source.metadata, copied.metadata)
            assertTrue(copied.ordered)
            assertEquals("00CA010000", copied.rules.single().command)
            assertEquals("Local Network", copied.scheme)
            assertEquals(source.copy(packId = CardPacks.PERSONAL), app.store.cards().single { it.id == source.id })
        } finally {
            app.store.cards().filter { it.id == source.id || it.name == "页面测试副本" }.forEach { app.store.delete(it.id) }
        }
    }
    @Test fun unfinishedNewCardSurvivesRecreationAndValidationDoesNotSaveIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as SimulatorApp
        val before = app.store.cards()
        ActivityScenario.launch<CardEditorActivity>(Intent(context, CardEditorActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                field(activity, "卡片名称").setText("尚未完成")
                field(activity, "AID（每行一个，5–16 字节）").setText("F00")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals("F00", field(activity, "AID（每行一个，5–16 字节）").text.toString())
                button(activity, "保存卡片").performClick()
                assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("AID：") })
            }
        }
        assertEquals(before, app.store.cards())
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun addingSchemePersistsAndRejectsDuplicateNames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("card-schemes", android.content.Context.MODE_PRIVATE)
        val original = prefs.getString("names", null)
        val name = "QA Network "+System.nanoTime()
        try {
            ActivityScenario.launch<CardEditorActivity>(Intent(context, CardEditorActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    val spinner = views(activity.window.decorView).filterIsInstance<Spinner>().single { it.contentDescription == "卡组织" }
                    spinner.setSelection(spinner.count - 1)
                }
                scenario.onActivity { activity ->
                    fun dialogViews() = android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { views(it) }
                    fun input() = dialogViews().filterIsInstance<EditText>().first { it.hint == "卡组织名称" }
                    fun click(title: String) = dialogViews().filterIsInstance<Button>().first { it.text.toString() == title }.performClick()
                    input().setText(name); click("添加")
                    val spinner = views(activity.window.decorView).filterIsInstance<Spinner>().single { it.contentDescription == "卡组织" }
                    assertEquals(name, spinner.selectedItem)
                    assertTrue(com.iscolt.cardsimulator.data.CardSchemeStore(activity) { emptyList() }.options().contains(name))
                    spinner.setSelection(spinner.count - 1)
                }
                scenario.onActivity {
                    val roots = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    val dialogViews = roots.flatMap { views(it) }
                    val input = dialogViews.filterIsInstance<EditText>().first { it.hint == "卡组织名称" }
                    input.setText("visa")
                    dialogViews.filterIsInstance<Button>().first { it.text.toString() == "添加" }.performClick()
                    assertEquals("该卡组织已存在，请从下拉列表选择", input.error.toString())
                    dialogViews.filterIsInstance<Button>().first { it.text.toString() == "取消" }.performClick()
                }
                scenario.recreate()
                scenario.onActivity { activity -> assertEquals(name, views(activity.window.decorView).filterIsInstance<Spinner>().single { it.contentDescription == "卡组织" }.selectedItem) }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                java.io.File(context.getExternalFilesDir(null), "editor-page.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }; bitmap.recycle()
            }
        } finally {
            if (original == null) prefs.edit().remove("names").commit() else prefs.edit().putString("names", original).commit()
        }
    }
}
