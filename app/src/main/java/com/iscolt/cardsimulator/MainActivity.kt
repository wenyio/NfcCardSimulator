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
    /** 首页导航位置；DETAIL 内部另用 detailTab 区分详情、调试和日志标签。 */
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
    // Activity Result（页面结果）可能在仓库初始化前补发，先暂存，initialize 后再处理。
    private val editorLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (initialized) handleEditorResult(result) else pendingEditorResult = result
    }
    /**
     * 处理编辑页返回结果：保存成功时定位保存后的卡片，并恢复用户此前的模拟意图。
     * 取消编辑也会解除 editing 状态；是否实际启动仍由详情会话和页面前台状态决定。
     */
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
        // 仓库通知在主线程投递；状态立即更新，连续报文的日志重排版合并到一次延迟任务。
        updateStatus()
        if (screen == Screen.LOGS || (screen == Screen.DETAIL && detailTab == 2)) {
            // 合并连续 APDU 通知；日志本身仍逐条保存。
            if (!logRefreshPending) {
                logRefreshPending = true
                logRefreshHandler.postDelayed(refreshLogs, 150)
            }
        }
    }
    /**
     * Android 创建或重建首页时调用：先注册文件选择回调并显示启动页，再订阅后台加载结果。
     * 此时不直接等待仓库；加载成功后 initialize 才构造业务页面。
     */
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
    /**
     * 仓库就绪后在主线程组装页面、NFC 控制器和详情会话。
     * 恢复当前格式的页面状态，处理系统补发的编辑结果，再按前台状态同步模拟。
     */
    private fun initialize(savedInstanceState: Bundle?) {
        nfc = NfcController(this, store)
        // :: 是函数引用，把状态控制所需动作交给纯控制器；它不需要持有 Activity 或 Android NFC 对象。
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
            /** 统一系统返回行为：详情或日志返回列表，列表返回则停止详情会话并退出。 */
            override fun handleOnBackPressed() {
                when {
                    screen != Screen.CARDS -> showList()
                    else -> { session.leave(); finish() }
                }
            }
        })
    }
    /**
     * 保存页面位置、模拟意图和待处理结果，供 Android 重建 Activity（页面组件）时恢复。
     * 保存的是页面状态；进程重启后不会直接恢复为 NFC 已启用。
     */
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
    /** 页面回到可交互的前台时记录 resumed；初始化完成后订阅变化并同步详情会话。 */
    override fun onResume() {
        super.onResume(); resumed = true
        if (initialized) resumeSession()
    }
    /** 订阅仓库变化，只在详情页允许实际模拟，并刷新当前可见的日志。 */
    private fun resumeSession() {
        store.addListener(listener)
        session.foreground(screen == Screen.DETAIL); updateStatus()
        if (screen == Screen.LOGS || (screen == Screen.DETAIL && detailTab == 2)) logs.update()
    }
    /** 页面失去前台交互时取消日志刷新和监听，并暂停实际模拟，保留用户的启动意图。 */
    override fun onPause() {
        resumed = false
        logRefreshHandler.removeCallbacks(refreshLogs)
        logRefreshPending = false
        if (initialized) store.removeListener(listener)
        // 退出可见详情即停止，包括切到后台；onResume 根据当前页面决定是否恢复。
        if (initialized) session.foreground(false)
        super.onPause()
    }
    /** 页面销毁时取消启动结果订阅，关闭本页面的文件任务执行器。 */
    override fun onDestroy() { cancelStartup?.invoke(); documents.close(); super.onDestroy() }
    /** 创建首页共用工具栏和内容容器；具体页面由 render 填充。 */
    private fun buildShell() = with(ui) {
        content = column().apply { setPadding(dp(16), dp(4), dp(16), 0) }
        toolbar = shell(this@MainActivity, content)
    }
    /** 进入独立编辑页前标记 editing 并暂停模拟；用稳定卡片 key 和复制标志传递编辑目标。 */
    private fun editCard(card: CardScript?, copy: Boolean) {
        editorPending = true; session.editing(true); session.foreground(false); updateStatus()
        editorLauncher.launch(Intent(this, CardEditorActivity::class.java).apply {
            putExtra("cardId", card?.key); putExtra("copy", copy)
        })
    }
    /** 打开卡片详情并申请前台模拟；同卡重复展示是否重选由详情会话控制。 */
    private fun openCard(card: CardScript) {
        session.open(card.key); screen = Screen.DETAIL; detailTab = 0
        session.foreground(true); render()
    }
    /** 所有返回列表的路径共用此出口：先结束详情会话，再重绘列表。 */
    private fun showList() {
        // 顶部返回、系统返回、删除和导入完成均经过同一出口，先停止再显示列表。
        session.leave(); screen = Screen.CARDS; render()
    }
    /** 显示设备设置菜单；仅真正跳转系统页面才会触发 onPause 暂停模拟。 */
    private fun settings(anchor: View) {
        DeviceSettingsMenu(this, nfc, ::message).show(anchor)
    }
    /**
     * 按当前页面和详情标签重建视图、绑定操作，并显示实际模拟状态。
     * 重绘不直接重建脚本引擎；交易步骤由 HCE 服务和手动调试页各自持有。
     */
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
    /** 把仓库中的实际启用状态及详情会话的启动错误同步给控制卡。 */
    private fun updateStatus() {
        if (::simulation.isInitialized) simulation.update(store.enabled, session.error)
    }
    /** 在主线程显示较长时长的短提示；业务处理结果由调用方决定。 */
    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
}
