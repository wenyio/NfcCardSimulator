package com.iscolt.cardsimulator.data

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class LogEntry(val time: Long, val kind: String, val message: String, val session: String = "系统", val cardId: String = "")

/** 有界日志仓库；射频回调只追加内存，单线程延迟合并写盘，避免每条 APDU 同步写文件。 */
class LogRepository(file: File) : AutoCloseable {
    private val disk = AtomicFile(file)
    private val writer = Executors.newSingleThreadScheduledExecutor()
    private var pending: ScheduledFuture<*>? = null
    private val entries = ArrayDeque<LogEntry>()
    init {
        if (disk.baseFile.exists()) runCatching {
            val array = JSONArray(disk.openRead().bufferedReader().use { it.readText() })
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                entries.addLast(LogEntry(item.getLong("time"), item.getString("kind"), item.getString("message"), item.optString("session", "系统"), item.optString("cardId")))
            }
            trim()
        }.onFailure { entries.clear(); entries.addLast(LogEntry(System.currentTimeMillis(), "错误", "历史日志读取失败：${it.message}")) }
    }
    private fun trim() {
        // 条数及单条大小都有限制，防止损坏脚本或长 APDU 使日志无限增长。
        while (entries.size > 600 || entries.sumOf { it.message.length } > 1_000_000) entries.removeFirst()
    }
    @Synchronized fun append(entry: LogEntry) { entries.addLast(entry.copy(message = if (entry.message.length > 16000) entry.message.take(16000) + " …[长记录截断]" else entry.message)); trim(); schedule() }
    @Synchronized fun snapshot(): List<LogEntry> = entries.toList()
    @Synchronized fun clear(cardId: String? = null) {
        // 详情页只清当前卡；全部日志入口才清所有记录，包括旧版本没有卡片 ID 的记录。
        if (cardId == null) entries.clear() else entries.removeAll { it.cardId == cardId }
        schedule()
    }
    private fun schedule() {
        // 第一条记录后固定 300 ms 落盘；持续收发也不会无限延后保存。
        if (pending == null || pending!!.isDone) pending = writer.schedule({ persist() }, 300, TimeUnit.MILLISECONDS)
    }
    private fun persist() {
        // 在锁内取得快照后立即释放；写盘期间 APDU 仍可追加日志。
        val snapshot = synchronized(this) { pending = null; entries.toList() }
        val array = JSONArray()
        snapshot.forEach { array.put(JSONObject().put("time", it.time).put("kind", it.kind).put("message", it.message).put("session", it.session).put("cardId", it.cardId)) }
        var stream: java.io.FileOutputStream? = null
        try {
            stream = disk.startWrite()
            stream.write(array.toString().toByteArray(Charsets.UTF_8)); disk.finishWrite(stream)
        } catch (e: Exception) {
            stream?.let { disk.failWrite(it) }
            android.util.Log.e("SimulatorLogs", "日志保存失败", e)
        }
    }
    /** 测试/显式同步点；正常 APDU 路径不等待磁盘。 */
    fun flush() { writer.submit { persist() }.get() }
    override fun close() {
        synchronized(this) { pending?.cancel(false); pending = null }
        flush(); writer.shutdown()
    }

}
