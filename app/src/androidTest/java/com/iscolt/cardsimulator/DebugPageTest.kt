package com.iscolt.cardsimulator

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.ui.debug.DebugCommandSelector
import com.iscolt.cardsimulator.ui.debug.DebugPage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugPageTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun button(root: View, title: String) = views(root).filterIsInstance<Button>().first { it.text.toString() == title }
    private fun has(root: View, text: String) = views(root).filterIsInstance<TextView>().any { it.text.toString().contains(text) }

    @Test fun selectionOnlyFillsAndEngineStillChecksOrderEditsResetTlvAndLogs() {
        val store = (instrument.targetContext.applicationContext as SimulatorApp).store
        val first = ApduRule("00A4040005F00102030400", "5A0212349000", label = "选择应用")
        val second = ApduRule("00B2011400", "9000", label = "读记录")
        val card = CardScript("qa-debug-picker", "QA 调试选择", "其他", listOf("F001020304"), "", listOf(first, second), ordered = true)
        store.importCards(listOf(card))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var root: LinearLayout
                lateinit var picker: Spinner
                lateinit var input: EditText
                var initialLogs = 0
                scenario.onActivity { activity ->
                    store.select(card.id)
                    root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    DebugPage(UiKit(activity), store) { fail(it) }.show(root)
                    activity.setContentView(root)
                    picker = views(root).filterIsInstance<Spinner>().single()
                    input = views(root).filterIsInstance<EditText>().single()
                    assertEquals(first.command, input.text.toString())
                    assertEquals(0, picker.selectedItemPosition)
                    assertEquals("2 · READ RECORD · 00B20114…", picker.adapter.getItem(1))
                    initialLogs = store.logs().size
                    picker.setSelection(1)
                }
                instrument.waitForIdleSync()
                scenario.onActivity {
                    assertEquals(second.command, input.text.toString())
                    assertEquals(initialLogs, store.logs().size)
                    assertTrue(has(root, "等待输入命令"))
                    button(root, "发送到脚本").performClick()
                    assertTrue(has(root, "步骤 1 未匹配"))
                    picker.setSelection(0)
                }
                instrument.waitForIdleSync()
                scenario.onActivity {
                    button(root, "发送到脚本").performClick()
                    assertTrue(has(root, "5A0212349000")); assertTrue(has(root, "5A"))
                    picker.setSelection(1)
                }
                instrument.waitForIdleSync()
                scenario.onActivity {
                    input.setText("00B2021400"); button(root, "发送到脚本").performClick()
                    assertTrue(has(root, "步骤 2 未匹配"))
                    input.setText(second.command); button(root, "发送到脚本").performClick()
                    assertTrue(has(root, "R-APDU  9000")); assertTrue(has(root, "读记录"))
                    assertEquals(4, store.logs().count { it.cardId == store.selectedId && it.kind == "手动 T" })
                    assertTrue(store.logs().any { it.kind == "手动 T" && it.message == "00B2021400" })
                    assertTrue(store.logs().any { it.kind == "手动 C" && it.message.contains("步骤 2 未匹配") })
                    // 模拟 NFC 端另建引擎，手动发送不能推进其步骤。
                    val nfc = ScriptEngine(store.selected()!!)
                    assertFalse(nfc.exchange(second.command).matched)
                    assertTrue(nfc.exchange(first.command).matched)
                    button(root, "重置会话").performClick()
                    assertTrue(has(root, "手动会话已重置"))
                    input.setText(first.command); button(root, "发送到脚本").performClick()
                    assertTrue(has(root, "5A0212349000"))
                }
                instrument.waitForIdleSync()
                scenario.onActivity { activity ->
                    val image = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
                    root.draw(android.graphics.Canvas(image))
                    java.io.File(activity.getExternalFilesDir(null), "debug-command-picker.png").outputStream().use {
                        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    image.recycle()
                    assertFalse(has(root, "使用说明"))
                    val info = views(root).first { it.contentDescription?.toString() == "调试使用说明" }
                    assertEquals(UiKit(activity).dp(48), info.layoutParams.width)
                    info.performClick()
                }
                scenario.onActivity {
                    val dialog = WindowInspector.getGlobalWindowViews().first { has(it, "调试使用说明") }
                    assertTrue(has(dialog, "只填充")); assertTrue(has(dialog, "未匹配不推进")); assertTrue(has(dialog, "密文或签名"))
                    button(dialog, "知道了").performClick()
                }
                scenario.onActivity { assertFalse(WindowInspector.getGlobalWindowViews().any { has(it, "知道了") }) }
            }
        } finally { store.delete(card.id) }
    }

    @Test fun emptyRuleListDoesNotInjectPpseAndStillAllowsManualInput() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = LinearLayout(activity)
                val input = EditText(activity)
                DebugCommandSelector(UiKit(activity), root, emptyList(), input)
                val picker = views(root).filterIsInstance<Spinner>().single()
                assertFalse(picker.isEnabled); assertEquals("", input.text.toString())
                input.setText("0084000008"); assertEquals("0084000008", input.text.toString())
            }
        }
    }
}
