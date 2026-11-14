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


class SimulatorStore(context: Context) {
    private val preferences = context.getSharedPreferences("simulator", Context.MODE_PRIVATE)
    private val repository = CardPackRepository(context)
    @Volatile var activePackId: String = preferences.getString("activePack", CardPacks.DEMO) ?: CardPacks.DEMO
        private set
    private val history = LogRepository(File(context.filesDir, "history.json"))
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile var selectedId: String? = preferences.getString("selected", null)
        private set
    @Volatile var enabled: Boolean = false
        private set
    @Volatile var sessionRevision: Long = 0
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
    fun packs() = repository.packs()
    fun currentPack() = repository.find(activePackId) ?: repository.find(CardPacks.DEMO)!!
    fun currentCards() = currentPack().cards
    fun cards(): List<CardScript> = repository.cards()
    fun findCard(key: String): CardScript? = cards().firstOrNull { it.key == key }
        ?: cards().filter { it.id == key }.singleOrNull()
    fun selected(): CardScript? = selectedId?.let(::findCard)
    @Synchronized fun select(id: String) {
        val card = requireNotNull(findCard(id)) { "卡片不存在或编号不唯一" }
        selectedId = card.key
        enabled = false
        sessionRevision++
        preferences.edit().putString("selected", card.key).remove("enabled").apply()
        log("卡片", "已选择 " + card.name)
    }
    @Synchronized fun switchPack(id: String) {
        require(repository.find(id) != null) { "卡包不存在" }
        setEnabled(false)
        selectedId = null
        activePackId = id
        preferences.edit().putString("activePack", id).remove("selected").apply()
    }
    @Synchronized fun setEnabled(value: Boolean) {
        require(!value || selected() != null) { "请先选择卡片" }
        enabled = value; sessionRevision++
        log("状态", if (value) "模拟已启动，请保持手机解锁" else "模拟已停止")
    }
    /** 编辑已有卡保留归属；新建和复制由草稿显式指向自建卡片。 */
    @Synchronized fun importCards(cards: List<CardScript>): Int {
        repository.saveCards(cards)
        setEnabled(false)
        return cards.size
    }
    @Synchronized fun importPack(pack: CardPack, targetId: String? = null): CardPack {
        val saved = repository.importPack(pack, targetId)
        switchPack(saved.id)
        return saved
    }
    @Synchronized fun renamePack(id: String, name: String) { repository.rename(id, name) }
    @Synchronized fun createPack(name: String, description: String): CardPack {
        val pack = repository.createPack(name, description); switchPack(pack.id); return pack
    }
    @Synchronized fun saveEditedCard(card: CardScript, originalKey: String?) {
        repository.saveEditedCard(card, originalKey)
        if (originalKey != null && selectedId == originalKey) {
            selectedId = card.key; preferences.edit().putString("selected", card.key).apply()
        }
        setEnabled(false)
    }
    @Synchronized fun deletePack(id: String) {
        repository.delete(id)
        if (selected()?.packId == id || selected() == null) { selectedId = null; preferences.edit().remove("selected").apply() }
        setEnabled(false)
        if (activePackId == id) switchPack(CardPacks.DEMO)
    }
    @Synchronized fun delete(id: String) {
        val key = requireNotNull(findCard(id)) { "卡片不存在" }.key
        repository.deleteCard(key)
        if (selectedId == key) { selectedId = null; preferences.edit().remove("selected").apply() }
        setEnabled(false)
    }
    fun beginSession(kind: String, card: String): String {
        val id = kind + "-" + java.util.UUID.randomUUID().toString().take(8)
        log("会话开始", card, id)
        return id
    }
    fun log(kind: String, text: String, session: String = "系统") {
        history.append(LogEntry(System.currentTimeMillis(), kind, text, session, selectedId.orEmpty())); notifyChanged()
    }
    fun logs(): List<LogEntry> = history.snapshot()
    fun clearLogs(cardId: String? = null) { history.clear(cardId); notifyChanged() }
    fun flushLogs() = history.flush()
    fun logText(cardId: String? = null): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
        return logs().filter { cardId == null || it.cardId == cardId }.joinToString("\n") { "${formatter.format(Date(it.time))} [${it.session}][${it.kind}] ${it.message}" }
    }
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyChanged() { handler.post { listeners.forEach { it() } } }
}
