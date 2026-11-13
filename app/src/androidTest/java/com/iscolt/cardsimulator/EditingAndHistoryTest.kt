package com.iscolt.cardsimulator

import android.app.AlertDialog
import android.widget.*
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.data.LogRepository
import com.iscolt.cardsimulator.data.LogEntry
import com.iscolt.cardsimulator.importer.TransactionLogParser
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.ui.cards.CardEditorPage
import com.iscolt.cardsimulator.ui.cards.CardEditorDraft
import com.iscolt.cardsimulator.data.CardSchemeStore
import com.iscolt.cardsimulator.ui.cards.TransactionImportDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EditingAndHistoryTest {
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun button(dialog: AlertDialog, title: String) = views(dialog.window!!.decorView).filterIsInstance<Button>().first { it.text.toString() == title }
    private fun field(dialog: AlertDialog, hint: String) = views(dialog.window!!.decorView).filterIsInstance<EditText>().first { it.hint?.toString() == hint }
    private val source = CardScript("qa-source", "原卡", "Discover", listOf("A0000001523010"), "说明",
        listOf(ApduRule("00CA000000", "019000"), ApduRule("00CA010000", "029000")), true)
    @Test fun copyReorderSaveAndValidationUseRealControls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var saved: CardScript? = null
                val draft = CardEditorDraft.create(source, true)
                val parent = LinearLayout(activity)
                val page = CardEditorPage(UiKit(activity), CardSchemeStore(activity) { listOf(source) })
                page.show(parent, draft.card, listOf(CardPack(CardPacks.PERSONAL, "自建卡片")), {}, {})
                val name = views(parent).filterIsInstance<EditText>().first { it.hint == "卡片名称" }
                name.setText(""); draft.card = page.snapshot()
                assertTrue(runCatching { draft.validated() }.isFailure)
                name.setText("复制并重排"); draft.card = page.snapshot().copy(rules = source.rules.reversed())
                val result = draft.validated()
                assertNotEquals(source.id, result.id); assertEquals("00CA010000", result.rules.first().command)
                assertEquals("00CA000000", source.rules.first().command)
                val store = (activity.application as SimulatorApp).store
                try {
                    store.importCards(listOf(result))
                    assertEquals(result, com.iscolt.cardsimulator.data.SimulatorStore(activity).cards().first { it.id == result.id })
                } finally { if (store.cards().any { it.id == result.id }) store.delete(result.id) }
            }
        }
    }
    @Test fun importPreviewRejectsIncompleteAndAcceptsValidTransaction() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var saved: CardScript? = null
                val ui = UiKit(activity)
                val broken = TransactionImportDialog(ui).show(TransactionLogParser.parse("T:00CA000000")) { saved = it }
                assertFalse(broken.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                broken.dismiss(); assertNull(saved)
                val valid = TransactionImportDialog(ui).show(TransactionLogParser.parse("T:00A4040007A000000152301000\nC:9000")) { saved = it }
                assertTrue(valid.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                valid.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertEquals(listOf("A0000001523010"), saved!!.aids); assertTrue(saved!!.ordered)
            }
        }
    }
    @Test fun historySurvivesReloadRetainsBoundAndClears() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "qa-history.json")
        val history = LogRepository(file)
        repeat(605) { history.append(LogEntry(it.toLong(), "T", "$it", "qa")) }
        history.close()
        val reloaded = LogRepository(file)
        assertEquals(600, reloaded.snapshot().size); assertEquals("5", reloaded.snapshot().first().message)
        assertEquals("qa", reloaded.snapshot().last().session)
        reloaded.clear(); reloaded.close()
        LogRepository(file).use { assertTrue(it.snapshot().isEmpty()) }
    }
    @Test fun clearingOneCardPreservesOtherCardsAndLegacyRecordsOnDisk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "qa-scoped-clear.json")
        LogRepository(file).use { history ->
            history.clear()
            history.append(LogEntry(1, "T", "A", "session-a", "a"))
            history.append(LogEntry(2, "T", "B", "session-b", "b"))
            history.append(LogEntry(3, "T", "旧记录"))
            history.clear("a")
            assertEquals(listOf("B", "旧记录"), history.snapshot().map { it.message })
            // 清除后发生的新收发仍应保留，不影响当前会话继续记录。
            history.append(LogEntry(4, "T", "A-new", "session-a", "a"))
        }
        LogRepository(file).use { history ->
            assertEquals(listOf("B", "旧记录", "A-new"), history.snapshot().map { it.message })
            history.clear()
        }
        LogRepository(file).use { assertTrue(it.snapshot().isEmpty()) }
    }
}
