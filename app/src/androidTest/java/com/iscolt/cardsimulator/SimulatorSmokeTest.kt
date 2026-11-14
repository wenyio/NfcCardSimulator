package com.iscolt.cardsimulator

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import com.iscolt.cardsimulator.core.ScriptCodec
import com.iscolt.cardsimulator.data.SimulatorStore
import android.widget.Button
import android.widget.TextView
import android.view.View
import android.view.ViewGroup

@RunWith(AndroidJUnit4::class)
class SimulatorSmokeTest {
    @Test fun manualApduAndLogsWorkWithoutNfcHardware() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SimulatorApp
        app.store.select("demo-discover")
        app.store.clearLogs()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                views(root).first { it.contentDescription?.toString() == "打开卡片：Discover · 连通性演示" }.performClick()
                findButton(root, "调试").performClick()
                findButton(root, "发送到脚本").performClick()
                assertTrue(texts(root).any { it.startsWith("R-APDU") && it.contains("9000") })
                findButton(root, "日志").performClick()
                assertTrue(texts(root).any { it.contains("SELECT PPSE") })
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "smoke-logs.png")
            file.outputStream().use { output -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output) }
            bitmap.recycle()
        }
    }
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun findButton(root: View, text: String): Button =
        views(root).filterIsInstance<Button>().first { it.text.toString() == text }
    private fun texts(root: View): List<String> = views(root).filterIsInstance<TextView>().map { it.text.toString() }
    @Test fun customCardsSurviveReloadAndDeletionOfLastCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as SimulatorApp
        val text = """{"id":"qa-persist","name":"QA","aids":["F001020304"],"rules":[{"command":"00CA000000","response":"9000"}]}"""
        try {
            app.store.saveEditedCard(ScriptCodec.parse(text).single().copy(packId = com.iscolt.cardsimulator.core.CardPacks.PERSONAL), null)
            val reloaded = SimulatorStore(context)
            assertTrue(reloaded.cards().any { it.id == "qa-persist" })
            app.store.delete("qa-persist")
            val afterDeletion = SimulatorStore(context)
            assertFalse(afterDeletion.cards().any { it.id == "qa-persist" })
            assertEquals(6, afterDeletion.cards().count { it.builtIn })
        } finally {
            if (app.store.cards().any { it.id == "qa-persist" }) app.store.delete("qa-persist")
        }
    }
}
