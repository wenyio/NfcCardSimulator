package com.iscolt.cardsimulator

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.iscolt.cardsimulator.core.CardScript
import com.iscolt.cardsimulator.io.DocumentController
import com.iscolt.cardsimulator.nfc.*
import com.iscolt.cardsimulator.ui.StartupPage
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.ui.swipePages
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.iscolt.cardsimulator.ui.cards.*
import com.iscolt.cardsimulator.ui.debug.DebugPage
import com.iscolt.cardsimulator.ui.logs.LogsPage
import com.iscolt.cardsimulator.ui.settings.DeviceSettingsMenu

/** Activity 只组合导航与生命周期，详情会话由独立控制器负责。 */
class MainActivity : AppCompatActivity() {
    private enum class Screen { CARDS, DETAIL, LOGS }
    private val store get() = (application as SimulatorApp).store
    private lateinit var ui: UiKit
    private lateinit var nfc: NfcController
    private lateinit var session: DetailSimulationSession
    private lateinit var documents: DocumentController
    private lateinit var packs: CardPackTitle
    private lateinit var cards: CardsPage
    private lateinit var actions: CardActions
    private lateinit var detail: CardDetailPage
    private lateinit var debug: DebugPage
    private lateinit var logs: LogsPage
    private lateinit var content: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var simulation: SimulationControl
    private var screen = Screen.CARDS
    private var detailTab = 0
    private var editorPending = false
    private var initialized = false
    private var resumed = false
    private var cancelStartup: (() -> Unit)? = null
    private var pendingState: Bundle? = null
    private var pendingEditorResult: ActivityResult? = null
    private var pendingManagerResult = false
    private val packManagerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // 管理页可重命名、删除或切包，返回时统一刷新标题和列表，不恢复模拟。
        if (initialized) showList() else pendingManagerResult = true
    }
    private val editorLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (initialized) handleEditorResult(result) else pendingEditorResult = result
    }
    private fun handleEditorResult(result: ActivityResult) {
        val requested = session.isRequested
        val savedId = result.data?.getStringExtra("savedCardId")
        if (result.resultCode == RESULT_OK && savedId != null) {
            store.findCard(savedId)?.let { store.switchPack(it.packId) }
        }
        if (result.resultCode == RESULT_OK && savedId != null && screen == Screen.DETAIL) {
            session.open(savedId); session.setRequested(requested); detailTab = 0
        }
        editorPending = false; session.editing(false); render()
    }
    private val logRefreshHandler = Handler(Looper.getMainLooper())
    private var logRefreshPending = false
    private val refreshLogs = Runnable {
        logRefreshPending = false
        if (resumed && (screen == Screen.LOGS || (screen == Screen.DETAIL && detailTab == 2))) logs.update()
    }
    private val listener: () -> Unit = {
        updateStatus()
        if (screen == Screen.LOGS || (screen == Screen.DETAIL && detailTab == 2)) {
            // 合并连续 APDU 通知；日志本身仍逐条保存。
            if (!logRefreshPending) {
                logRefreshPending = true
                logRefreshHandler.postDelayed(refreshLogs, 150)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingState = savedInstanceState
        pendingEditorResult = savedInstanceState?.getParcelable("pendingEditorResult")
        pendingManagerResult = savedInstanceState?.getBoolean("pendingManagerResult", false) ?: false
        ui = UiKit(this)
        // SAF 启动器必须在 STARTED 前注册，数据仓库则延迟到实际使用时取得。
        documents = DocumentController(this, { store }, { showList() }, ::message)
        ui.systemBars(this)
        setContentView(StartupPage(ui).create())
        cancelStartup = (application as SimulatorApp).storeLoader.whenReady { result ->
            if (!isDestroyed && !isFinishing) result.fold(
                onSuccess = { initialize(pendingState) },
                onFailure = { setContentView(StartupPage(ui).create(it)) }
            )
        }
    }
    private fun initialize(savedInstanceState: Bundle?) {
        nfc = NfcController(this, store)
        session = DetailSimulationSession(store::select, nfc::start, nfc::stop)
        packs = CardPackTitle(ui, store) {
            packManagerLauncher.launch(Intent(this, CardPackManagerActivity::class.java))
        }
        actions = CardActions(ui, store, ::editCard, ::showList, documents::exportCard, ::message)
        cards = CardsPage(ui, store, ::openCard, { anchor, card -> actions.menu(anchor, card) }, { actions.edit() })
        detail = CardDetailPage(ui); simulation = SimulationControl(ui); debug = DebugPage(ui, store, ::message)
        logs = LogsPage(ui, store) { documents.exportLogs(if (screen == Screen.DETAIL) session.cardId else null) }
        val restoredId = savedInstanceState?.getString("detailCard")
        if (restoredId != null && store.findCard(restoredId) != null) {
            session.open(restoredId); detailTab = savedInstanceState.getInt("detailTab")
            screen = Screen.DETAIL
        } else nfc.stop()
        session.setRequested(savedInstanceState?.getBoolean("requested", true) ?: true)
        editorPending = savedInstanceState?.getBoolean("editorPending", false) ?: false
        session.editing(editorPending)
        initialized = true; pendingState = null
        buildShell(); render()
        // 数据就绪后再处理系统补发的编辑结果，避免进程重建时丢失刚保存的卡片。
        pendingEditorResult?.let { pendingEditorResult = null; handleEditorResult(it) }
        if (pendingManagerResult) { pendingManagerResult = false; showList() }
        window.decorView.post { if (!isDestroyed) reportFullyDrawn() }
        if (resumed) resumeSession()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    screen != Screen.CARDS -> showList()
                    else -> { session.leave(); finish() }
                }
            }
        })
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (!initialized) {
            pendingState?.let(outState::putAll)
            outState.putParcelable("pendingEditorResult", pendingEditorResult)
            outState.putBoolean("pendingManagerResult", pendingManagerResult)
            super.onSaveInstanceState(outState); return
        }
        outState.putString("detailCard", session.cardId); outState.putInt("detailTab", detailTab)
        outState.putBoolean("requested", session.isRequested); outState.putBoolean("editorPending", editorPending)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume(); resumed = true
        if (initialized) resumeSession()
    }
    private fun resumeSession() {
        store.addListener(listener)
        session.foreground(screen == Screen.DETAIL); updateStatus()
        if (screen == Screen.LOGS || (screen == Screen.DETAIL && detailTab == 2)) logs.update()
    }
    override fun onPause() {
        resumed = false
        logRefreshHandler.removeCallbacks(refreshLogs)
        logRefreshPending = false
        if (initialized) store.removeListener(listener)
        // 退出可见详情即停止，包括切到后台；onResume 根据当前页面决定是否恢复。
        if (initialized) session.foreground(false)
        super.onPause()
    }
    override fun onDestroy() { cancelStartup?.invoke(); documents.close(); super.onDestroy() }
    private fun buildShell() = with(ui) {
        content = column().apply { setPadding(dp(16), dp(4), dp(16), 0) }
        toolbar = shell(this@MainActivity, content)
    }
    private fun editCard(card: CardScript?, copy: Boolean) {
        editorPending = true; session.editing(true); session.foreground(false); updateStatus()
        editorLauncher.launch(Intent(this, CardEditorActivity::class.java).apply {
            putExtra("cardId", card?.key); putExtra("copy", copy)
        })
    }
    private fun openCard(card: CardScript) {
        session.open(card.key); screen = Screen.DETAIL; detailTab = 0
        session.foreground(true); render()
    }
    private fun showList() {
        // 顶部返回、系统返回、删除和导入完成均经过同一出口，先停止再显示列表。
        session.leave(); screen = Screen.CARDS; render()
    }
    private fun settings(anchor: View) {
        DeviceSettingsMenu(this, nfc, ::message).show(anchor)
    }
    private fun render(): Unit = with(ui) {
        if (!::content.isInitialized) return@with
        content.removeAllViews(); logs.detach(); simulation.detach(); toolbar.removeAllViews()
        if (screen != Screen.CARDS) toolbar.addView(icon(R.drawable.ic_back, getString(R.string.action_back)) { onBackPressedDispatcher.onBackPressed() })
        val card = session.cardId?.let(store::findCard)
        val title = when (screen) { Screen.CARDS -> getString(R.string.app_name); Screen.DETAIL -> card?.name ?: getString(R.string.page_card_details); Screen.LOGS -> getString(R.string.page_all_logs) }
        if (screen == Screen.CARDS) {
            toolbar.addView(packs.title(packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()), LinearLayout.LayoutParams(0, -2, 1f))
        } else toolbar.addView(label(title, 20f, Color.WHITE, true).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (screen == Screen.CARDS) {
            toolbar.addView(icon(R.drawable.ic_add, getString(R.string.action_add_card)) { anchor ->
                PopupMenu(this@MainActivity, anchor).apply {
                    menu.add(R.string.action_new_card).setOnMenuItemClickListener { actions.edit(); true }
                    menu.add(R.string.action_import_file).setOnMenuItemClickListener { documents.import(); true }
                    show()
                }
            })
            toolbar.addView(icon(R.drawable.ic_logs, getString(R.string.page_all_logs)) { screen = Screen.LOGS; render() })
            toolbar.addView(icon(R.drawable.ic_settings, getString(R.string.action_settings)) { settings(it) })
        } else if (screen == Screen.DETAIL && card != null) {
            toolbar.addView(icon(R.drawable.ic_settings, getString(R.string.action_settings)) { settings(it) })
            toolbar.addView(icon(R.drawable.ic_more, getString(R.string.action_card_menu)) { actions.menu(it, card) })
        }
        for (i in 0 until toolbar.childCount) (toolbar.getChildAt(i) as? ImageButton)?.imageTintList =
            android.content.res.ColorStateList.valueOf(Color.WHITE)
        when (screen) {
            Screen.CARDS -> cards.show(content)
            Screen.LOGS -> logs.show(content)
            Screen.DETAIL -> if (card == null) showList() else {
                simulation.show(content) { enabled -> session.setRequested(enabled); updateStatus() }
                swipePages(content, listOf(getString(R.string.tab_details), getString(R.string.tab_debug), getString(R.string.tab_logs)), detailTab,
                    onSelected = { detailTab = it; updateStatus(); logs.update() }) { index, page ->
                    when (index) {
                        0 -> detail.show(page, card) { actions.edit(card, card.builtIn) }
                        1 -> debug.show(page)
                        else -> logs.show(page, card.key)
                    }
                }
            }
        }
        updateStatus()
    }
    private fun updateStatus() {
        if (::simulation.isInitialized) simulation.update(store.enabled, session.error)
    }
    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
}
