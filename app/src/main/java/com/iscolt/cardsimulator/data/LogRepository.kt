package com.iscolt.cardsimulator.data

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** time 为 Unix 毫秒时间；kind 为方向或事件，session 供分组，cardId 保存组合 key 供按卡筛选。 */
data class LogEntry(
    val time: Long,
    val kind: String,
    val message: String,
    val session: String = "系统",
    val cardId: String = ""
)

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
                entries.addLast(
                    LogEntry(
                        item.getLong("time"),
                        item.getString("kind"),
                        item.getString("message"),
                        item.optString("session", "系统"),
                        item.optString("cardId")
                    )
                )
            }
            trim()
        }.onFailure {
            entries.clear(); entries.addLast(
            LogEntry(
                System.currentTimeMillis(),
                "错误",
                "历史日志读取失败：${it.message}"
            )
        )
        }
    }

    /** 移除最早日志，直到满足最多 600 条及消息总字符数上限；调用方在受保护的修改路径执行。 */
    private fun trim() {
        // 条数及单条大小都有限制，防止损坏脚本或长 APDU 使日志无限增长。
        while (entries.size > 600 || entries.sumOf { it.message.length } > 1_000_000) entries.removeFirst()
    }

    /** 在对象锁内截断过长消息、追加日志、裁剪总量并安排异步写盘；不等待磁盘完成。 */
    @Synchronized
    fun append(entry: LogEntry) {
        entries.addLast(
            entry.copy(
                message = if (entry.message.length > 16000)
                    entry.message.take(16000) + " …[长记录截断]" else entry.message
            )
        )
        trim()
        schedule()
    }

    /** 在对象锁内复制当前日志列表，外部遍历不会直接持有可变队列。 */
    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()

    /** 在对象锁内清全部或指定卡片日志，并安排保存；cardId 为 null 表示全部。 */
    @Synchronized
    fun clear(cardId: String? = null) {
        // 详情页只清当前卡；全部日志入口清所有记录，也包括未绑定卡片的系统事件。
        if (cardId == null) entries.clear() else entries.removeAll { it.cardId == cardId }
        schedule()
    }

    /** 合并写入请求：尚无待执行任务时安排 300 毫秒后写盘，不为每条记录重新计时。 */
    private fun schedule() {
        // 第一条记录后固定 300 ms 落盘；持续收发也不会无限延后保存。
        if (pending == null || pending!!.isDone) pending =
            writer.schedule({ persist() }, 300, TimeUnit.MILLISECONDS)
    }

    /**
     * 短暂加锁复制日志，随后在写线程编码并原子保存；失败记入 Android 系统日志。
     * 写盘期间不持有日志队列锁，新 APDU 仍可追加。
     */
    private fun persist() {
        // 在锁内取得快照后立即释放；写盘期间 APDU 仍可追加日志。
        val snapshot = synchronized(this) {
            pending = null
            entries.toList()
        }
        val array = JSONArray()
        snapshot.forEach {
            array.put(
                JSONObject().put("time", it.time).put("kind", it.kind).put("message", it.message)
                    .put("session", it.session).put("cardId", it.cardId)
            )
        }
        var stream: java.io.FileOutputStream? = null
        try {
            stream = disk.startWrite()
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            disk.finishWrite(stream)
        } catch (e: Exception) {
            stream?.let { disk.failWrite(it) }
            android.util.Log.e("SimulatorLogs", "日志保存失败", e)
        }
    }

    /**
     * 提交一次写盘并等待完成，是会阻塞的显式同步点；写盘内部的失败仍由 persist 记录。
     */
    fun flush() {
        writer.submit { persist() }.get()
    }

    /** 取消尚未执行的延迟任务，等待最后一次保存，再关闭执行器；不要放在 NFC 命令热路径。 */
    override fun close() {
        synchronized(this) { pending?.cancel(false); pending = null }
        flush()
        writer.shutdown()
    }

}
