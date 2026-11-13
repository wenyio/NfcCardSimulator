package com.iscolt.cardsimulator

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.data.CardPackRepository
import com.iscolt.cardsimulator.ui.cards.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
class CardPackCreationTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private val app get() = context.applicationContext as SimulatorApp
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun windows() = android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { views(it) }
    private fun field(hint: String) = windows().filterIsInstance<EditText>().last { it.hint == hint }
    private fun button(title: String) = windows().filterIsInstance<Button>().last { it.text.toString() == title }.performClick()
    private fun click(activity: Activity, description: String) = views(activity.window.decorView).first { it.contentDescription == description }.performClick()
    private fun picker(activity: Activity) = views(activity.window.decorView).filterIsInstance<Spinner>().single { it.contentDescription == "所属卡包" }
    private fun sample(id: String = "one") = CardScript(id, "测试卡", "Discover", listOf("F001020304"), "", listOf(ApduRule("00A40400", "9000")))

    @Test fun createFromHomeValidatesReturnsToEmptyPackAndNewCardUsesIt() {
        app.store.switchPack(CardPacks.DEMO)
        val before = app.store.packs().map { it.id }.toSet()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { click(it, "管理卡包：连通性演示") }
                val openedManager = awaitResumedActivity<CardPackManagerActivity>()
                instrument.runOnMainSync {
                    val manager = openedManager
                    click(manager, "添加卡包")
                }
                instrument.waitForIdleSync()
                instrument.runOnMainSync {
                    val list = windows().filterIsInstance<ListView>().last()
                    val index = (0 until list.adapter.count).first { list.adapter.getItem(it).toString() == "新建卡包" }
                    list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
                }
                instrument.waitForIdleSync()
                instrument.runOnMainSync {
                    button("创建"); assertNotNull(field("卡包名称").error)
                    assertEquals(before, app.store.packs().map { it.id }.toSet())
                    field("卡包名称").setText("新建场景包")
                    field("说明（选填）").setText("本地场景说明")
                    button("创建")
                }
                instrument.waitForIdleSync()
                scenario.onActivity { activity ->
                    val pack = app.store.currentPack()
                    assertEquals("新建场景包", pack.name); assertEquals("本地场景说明", pack.description)
                    assertTrue(pack.cards.isEmpty()); assertFalse(app.store.enabled)
                    assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "此卡包暂无卡片" })
                    views(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "新建卡片" }.performClick()
                }
                val openedEditor = awaitResumedActivity<CardEditorActivity>()
                instrument.runOnMainSync {
                    val editor = openedEditor
                    assertEquals("新建场景包", picker(editor).selectedItem)
                    assertEquals(app.store.currentPack().id, CardPackRepository(context).packs().single { it.name == "新建场景包" }.id)
                }
            }
        } finally {
            instrument.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<CardEditorActivity>().forEach { it.finish() } }
            app.store.packs().filter { it.id !in before }.forEach { app.store.deletePack(it.id) }; app.store.switchPack(CardPacks.DEMO)
        }
    }

    @Test fun atomicMoveRejectsCollisionAndReadonlyTargetWithoutChangingSource() {
        val dir = java.io.File(context.cacheDir, "pack-move-" + System.nanoTime()).apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) { override fun getFilesDir() = dir }
        val repo = CardPackRepository(isolated)
        val source = repo.createPack("原包", "")
        val target = repo.createPack("目标包", "")
        val card = sample().copy(packId = source.id)
        repo.saveEditedCard(card, null)
        repo.saveEditedCard(sample().copy(packId = target.id), null)
        assertTrue(runCatching { repo.saveEditedCard(card.copy(packId = target.id), card.key) }.isFailure)
        assertEquals(card, repo.find(source.id)!!.cards.single())
        assertTrue(runCatching { repo.saveEditedCard(card.copy(packId = CardPacks.DEMO), card.key) }.isFailure)
        repo.deleteCard(target.id + ":one")
        repo.saveEditedCard(card.copy(packId = target.id), card.key)
        val restored = CardPackRepository(isolated)
        assertTrue(restored.find(source.id)!!.cards.isEmpty())
        assertEquals(card.copy(packId = target.id), restored.find(target.id)!!.cards.single())
        assertTrue(runCatching { restored.createPack(" ", "") }.isFailure)
    }

    @Test fun editorPackSelectionSurvivesRecreationAndMovesOnlyOnSave() {
        val before = app.store.packs().map { it.id }.toSet()
        val source = app.store.createPack("编辑原包", "")
        val target = app.store.createPack("编辑目标包", "")
        val card = sample().copy(packId = source.id)
        app.store.saveEditedCard(card, null)
        try {
            ActivityScenario.launch<CardEditorActivity>(Intent(context, CardEditorActivity::class.java).putExtra("cardId", card.key)).use { scenario ->
                scenario.onActivity { activity ->
                    val spinner = picker(activity)
                    assertEquals(source.name, spinner.selectedItem)
                    assertFalse((0 until spinner.count).any { spinner.getItemAtPosition(it).toString() == "连通性演示" })
                    spinner.setSelection((0 until spinner.count).first { spinner.getItemAtPosition(it).toString() == target.name })
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals(target.name, picker(activity).selectedItem)
                    assertNotNull(app.store.findCard(card.key))
                    views(activity.window.decorView).filterIsInstance<Button>().single { it.text.toString() == "保存卡片" }.performClick()
                }
            }
            assertNull(app.store.findCard(card.key)); assertNotNull(app.store.findCard(target.id + ":" + card.id))
            app.store.switchPack(CardPacks.DEMO)
            val builtin = app.store.currentCards().first()
            ActivityScenario.launch<CardEditorActivity>(Intent(context, CardEditorActivity::class.java).putExtra("cardId", builtin.key).putExtra("copy", true)).use { scenario ->
                scenario.onActivity { activity -> assertEquals(app.store.packs().first { it.id == CardPacks.PERSONAL }.name, picker(activity).selectedItem) }
            }
            app.store.switchPack(target.id)
            ActivityScenario.launch<CardEditorActivity>(Intent(context, CardEditorActivity::class.java).putExtra("cardId", target.id + ":" + card.id).putExtra("copy", true)).use { scenario ->
                scenario.onActivity { activity -> assertEquals(target.name, picker(activity).selectedItem) }
            }
        } finally { app.store.packs().filter { it.id !in before }.forEach { app.store.deletePack(it.id) }; app.store.switchPack(CardPacks.DEMO) }
    }
}
