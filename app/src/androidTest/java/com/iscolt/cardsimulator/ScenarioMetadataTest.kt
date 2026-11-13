package com.iscolt.cardsimulator

import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager.widget.ViewPager
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.ui.cards.CardEditorDraft
import com.iscolt.cardsimulator.ui.cards.CardEditorPage
import com.iscolt.cardsimulator.data.CardSchemeStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScenarioMetadataTest {
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun screenshot(name: String) {
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.waitForIdleSync()
        val image = instrument.uiAutomation.takeScreenshot()
        val file = java.io.File(instrument.targetContext.getExternalFilesDir(null), name)
        file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    @Test fun detailEditorAndDebugRetainMetadataAndShowSemanticDifferences() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SimulatorApp
        val select = ApduRule("00A4040005F00102030400", "6F099F38069F02069F37049000")
        val gpo = ApduRule("80A800000C830A00000000000111223344", "9000")
        val metadata = CardMetadata(CardSource("自编测试", "scenario.json", "a".repeat(64), "SOURCE01", "CFG01"),
            TestScenario("L2", "检查金额与随机数差异", "仅验证诊断展示", "先选择应用，再发送 GPO", "0.01（示例）"), listOf("自编验证数据"))
        val card = CardScript("qa-scenario", "QA 场景卡", "Discover", listOf("F001020304"), "", listOf(select, gpo), true, metadata = metadata)
        app.store.importCards(listOf(card))
        app.store.switchPack(com.iscolt.cardsimulator.core.CardPacks.PERSONAL)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    views(root).first { it.contentDescription?.toString() == "打开卡片：QA 场景卡" }.performClick()
                }
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    val texts = views(root).filterIsInstance<TextView>()
                    assertTrue(texts.any { it.isShown && it.text.toString().contains("检查金额与随机数差异") })
                    val origin = texts.firstOrNull { it.text.toString() == "原卡片编号\nSOURCE01" } ?: error(texts.joinToString { it.text.toString() })
                    assertFalse(origin.isShown)
                    views(root).first { it.contentDescription == "运行条件，展开" }.performClick()
                    assertTrue(origin.isShown)
                    assertFalse(texts.any { listOf("来源文件", "来源格式", "来源 SHA-256").any { title -> it.text.toString().startsWith(title) } })
                }
                screenshot("scenario-details-039.png")
                scenario.onActivity { activity ->
                    val draft = CardEditorDraft.create(card, true)
                    val page = CardEditorPage(UiKit(activity), CardSchemeStore(activity) { listOf(card) })
                    page.show(LinearLayout(activity), draft.card, app.store.packs().filterNot { it.builtIn }, {}, {})
                    draft.card = page.snapshot()
                    val saved = draft.validated()
                    assertNotEquals(card.id, saved.id); assertEquals(metadata, saved.metadata)
                    val pager = views(activity.window.decorView).filterIsInstance<ViewPager>().single()
                    pager.setCurrentItem(1, false)
                }
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    val input = views(root).filterIsInstance<EditText>().single()
                    val send = views(root).filterIsInstance<Button>().first { it.text.toString() == "发送到脚本" }
                    input.setText(select.command); send.performClick()
                    input.setText("80A800000C830A000000000002AABBCCDD"); send.performClick()
                    assertTrue(views(root).filterIsInstance<TextView>().any { it.isShown && it.text.toString().contains("9F02 授权金额") })
                    assertTrue(app.store.logs().any { it.cardId == "personal:" + card.id && it.message.contains("9F37 不可预知数") })
                }
                screenshot("scenario-debug-039.png")
                scenario.recreate()
                scenario.onActivity { assertEquals(metadata, app.store.selected()!!.metadata) }
            }
        } finally { app.store.delete(card.id) }
    }
}
