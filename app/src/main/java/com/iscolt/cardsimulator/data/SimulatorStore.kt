package com.iscolt.cardsimulator.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.iscolt.cardsimulator.core.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet


/**
 * 进程共享的应用状态与业务入口，协调卡包仓库、选卡、实际启用状态和日志。
 * 修改卡片/模拟状态的方法使用对象锁，Volatile 使独立读取可见；多字段读取并非天然的原子快照。
 * 变化通知投递到主线程，仓库写入可由 PackWork 后台调用；界面不直接操作存储文件。
 */
class SimulatorStore(context: Context) {
    private val preferences = context.getSharedPreferences("simulator", Context.MODE_PRIVATE)
    private val repository = CardPackRepository(context)
    @Volatile
    var activePackId: String = preferences.getString("activePack", CardPacks.DEMO) ?: CardPacks.DEMO
        private set
    private val history = LogRepository(File(context.filesDir, "history.json"))
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile
    var selectedId: String? = preferences.getString("selected", null)
        private set
    @Volatile
    var enabled: Boolean = false
        private set

    // 选卡、启停或修改使此值增加；服务与调试页在下一条命令前据此识别旧引擎。
    @Volatile
    var sessionRevision: Long = 0
        private set

    init {
        // 模拟状态属于当前详情会话，进程重启不能恢复上次的启动状态。
        preferences.edit().remove("enabled").apply()
        repository.readError?.let { log("错误", it) }
        if (selected() == null) {
            enabled = false
            preferences.edit().putBoolean("enabled", false).apply()
        }
    }

    /** 取得所有卡包，包含内置只读包和用户包。 */
    fun packs() = repository.packs()

    /** 取得当前列表使用的包；当前 ID 无效时显示内置演示包。 */
    fun currentPack() = repository.find(activePackId) ?: repository.find(CardPacks.DEMO)!!

    /** 取得当前列表包中的卡片；与正在选择的模拟卡状态分别维护。 */
    fun currentCards() = currentPack().cards

    /** 取得所有包的卡片，供查找、编辑和组织选项使用。 */
    fun cards(): List<CardScript> = repository.cards()

    /** 优先按包与卡片组合 key 查找；裸 ID 仅在全局唯一时返回，歧义时返回 null。 */
    fun findCard(key: String): CardScript? = cards().firstOrNull { it.key == key }
        ?: cards().filter { it.id == key }.singleOrNull()

    /** 按 selectedId 解析当前选中卡，尚未选择或卡已不存在时返回 null。 */
    fun selected(): CardScript? = selectedId?.let(::findCard)

    /** 在对象锁内选择卡片并停止模拟，递增会话修订号，使后续收发重建引擎。 */
    @Synchronized
    fun select(id: String) {
        val card = requireNotNull(findCard(id)) { "卡片不存在或编号不唯一" }
        selectedId = card.key
        enabled = false
        sessionRevision++
        preferences.edit().putString("selected", card.key).remove("enabled").apply()
        log("卡片", "已选择 " + card.name)
    }

    /** 验证目标包后停止模拟、清除选卡并保存列表包 ID；切包本身不自动选择卡片。 */
    @Synchronized
    fun switchPack(id: String) {
        require(repository.find(id) != null) { "卡包不存在" }
        setEnabled(false)
        selectedId = null
        activePackId = id
        preferences.edit().putString("activePack", id).remove("selected").apply()
    }

    /**
     * 在对象锁内更新实际模拟开关并递增修订号；启用前必须存在选中卡。
     * 此函数不检查硬件、不注册路由；这些前置条件由 NfcController.start 负责。
     */
    @Synchronized
    fun setEnabled(value: Boolean) {
        require(!value || selected() != null) { "请先选择卡片" }
        enabled = value
        sessionRevision++
        log("状态", if (value) "模拟已启动，请保持手机解锁" else "模拟已停止")
    }

    /** 持锁提交卡包，成功后切换列表到目标包并停止模拟；失败不执行后续切包。 */
    @Synchronized
    fun importPack(pack: CardPack, targetId: String? = null): CardPack {
        val saved = repository.importPack(pack, targetId)
        switchPack(saved.id)
        return saved
    }

    /** 在对象锁内委托仓库重命名；涉及写盘，界面通过 PackWork 放到后台执行。 */
    @Synchronized
    fun renamePack(id: String, name: String) {
        repository.rename(id, name)
    }

    /** 持锁创建用户包，保存成功后切换到新包，返回新包。 */
    @Synchronized
    fun createPack(name: String, description: String): CardPack {
        val pack = repository.createPack(name, description)
        switchPack(pack.id)
        return pack
    }

    /** 持锁保存编辑或移动结果，必要时更新选卡 key，再停止模拟让旧脚本会话失效。 */
    @Synchronized
    fun saveEditedCard(card: CardScript, originalKey: String?) {
        repository.saveEditedCard(card, originalKey)
        if (originalKey != null && selectedId == originalKey) {
            selectedId = card.key
            preferences.edit().putString("selected", card.key).apply()
        }
        setEnabled(false)
    }

    /** 持锁删除用户包，清理失效的选卡并停止模拟；删除当前包时切回演示包。 */
    @Synchronized
    fun deletePack(id: String) {
        repository.delete(id)
        if (selected()?.packId == id || selected() == null) {
            selectedId = null; preferences.edit().remove("selected").apply()
        }
        setEnabled(false)
        if (activePackId == id) switchPack(CardPacks.DEMO)
    }

    /** 持锁删除指定用户卡，清理对应选卡并停止模拟；仓库拒绝删除内置卡。 */
    @Synchronized
    fun delete(id: String) {
        val key = requireNotNull(findCard(id)) { "卡片不存在" }.key
        repository.deleteCard(key)
        if (selectedId == key) {
            selectedId = null; preferences.edit().remove("selected").apply()
        }
        setEnabled(false)
    }

    /** 生成用于日志分组的会话标识并记录开始事件；此标识本身不建立射频连接。 */
    fun beginSession(kind: String, card: String): String {
        val id = kind + "-" + java.util.UUID.randomUUID().toString().take(8)
        log("会话开始", card, id)
        return id
    }

    /** 追加含时间、会话和当前选卡 key 的日志，再向主线程投递变化通知；日志写盘异步合并。 */
    fun log(kind: String, text: String, session: String = "系统") {
        history.append(
            LogEntry(
                System.currentTimeMillis(),
                kind,
                text,
                session,
                selectedId.orEmpty()
            )
        )
        notifyChanged()
    }

    /** 取得日志快照供展示或导出，不直接访问可变队列。 */
    fun logs(): List<LogEntry> = history.snapshot()

    /** 清除全部或指定卡片日志后通知界面；null 表示全部。 */
    fun clearLogs(cardId: String? = null) {
        history.clear(cardId); notifyChanged()
    }

    /** 等待日志执行器完成一次保存，用于显式同步；正常命令处理不调用。 */
    fun flushLogs() = history.flush()

    /** 按卡片可选过滤日志，生成含毫秒时间、会话和方向的导出文本。 */
    fun logText(cardId: String? = null): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
        return logs().filter { cardId == null || it.cardId == cardId }
            .joinToString("\n") { "${formatter.format(Date(it.time))} [${it.session}][${it.kind}] ${it.message}" }
    }

    /** 注册无参变化监听；后续通知在主线程执行，页面离开时应对称移除。 */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    /** 移除页面变化监听，避免失去前台后继续刷新视图。 */
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** 通过主线程 Handler 投递通知；同一事件的监听调用不在写入线程直接执行。 */
    private fun notifyChanged() {
        handler.post { listeners.forEach { it() } }
    }
}
