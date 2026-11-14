package com.iscolt.cardsimulator.io

import android.app.AlertDialog
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.importer.TransactionLogParser
import com.iscolt.cardsimulator.importer.ImportEvidence
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.ui.cards.CardPackImportDialog
import com.iscolt.cardsimulator.ui.cards.TransactionImportDialog
import com.iscolt.cardsimulator.ui.cards.PackWork
import java.util.concurrent.Executors
import com.iscolt.cardsimulator.R

/** SAF 文件访问与解析在后台执行；页面只负责触发操作。 */
class DocumentController(private val activity: AppCompatActivity, private val storeProvider: () -> SimulatorStore,
    private val onImported: () -> Unit, private val message: (String) -> Unit) {
    constructor(activity: AppCompatActivity, store: SimulatorStore, onImported: () -> Unit, message: (String) -> Unit) :
        this(activity, { store }, onImported, message)
    private val store get() = storeProvider()
    private val io = Executors.newSingleThreadExecutor()
    private var pendingExport = ""
    private val importer = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) read(uri)
    }
    private val jsonExporter = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) write(uri)
    }
    private val logExporter = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) write(uri)
    }
    /** 启动 SAF（Storage Access Framework，存储访问框架）的系统文件选择器；选中文档后才读取。 */
    fun import() = importer.launch(arrayOf("*/*"))
    /** 暂存单卡 JSON 并打开系统创建文档界面；用户选定目标后由 write 写出。 */
    fun exportCard(card: CardScript) {
        stage(ScriptCodec.encode(listOf(card))); jsonExporter.launch(card.id + ".json")
    }
    /** 暂存完整卡包 JSON 并打开系统创建文档界面，不直接写任意外部路径。 */
    fun exportPack(pack: CardPack) {
        stage(CardPackCodec.encode(pack)); jsonExporter.launch(pack.id + ".json")
    }
    /** 按可选卡片过滤导出日志，暂存文本后请系统创建文档；null 表示全部日志。 */
    fun exportLogs(cardId: String? = null) { stage(store.logText(cardId)); logExporter.launch("nfc-apdu-log.txt") }
    /**
     * 保存待导出文本到内存及私有缓存，供系统返回导出结果时使用。
     * 此处缓存写入在调用线程执行；实际目标文档写入由 write 的后台任务完成。
     */
    private fun stage(text: String) {
        pendingExport = text
        java.io.File(activity.cacheDir, "pending-export.txt").writeText(text)
    }
    /**
     * 在单线程执行器中按大小上限读取文档，计算原始证据并分流 JSON 或交易文本解析。
     * 成功后回主线程展示预览，确认保存再提交；页面销毁后不展示结果。
     */
    private fun read(uri: Uri) {
        io.execute {
            try {
                val bytes = activity.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        require(output.size() + count <= ScriptCodec.MAX_BYTES) { activity.getString(R.string.file_too_large) }
                        output.write(buffer, 0, count)
                    }; output.toByteArray()
                } ?: error(activity.getString(R.string.file_read_failed))
                val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                val fileName = runCatching {
                    activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
                    }.orEmpty()
                }.getOrDefault("")
                val evidence = ImportEvidence.from(bytes, fileName)
                if (text.trimStart().startsWith("{") || text.trimStart().startsWith("[")) {
                    val parsed = CardPackCodec.parse(text, fileName.substringBeforeLast(".").ifBlank { activity.getString(R.string.file_imported_pack) })
                    val pack = parsed.copy(cards = parsed.cards.map { evidence.attach(it, "卡片 JSON") })
                    activity.runOnUiThread {
                        if (!activity.isDestroyed) CardPackImportDialog(UiKit(activity), store, onImported, message).show(pack)
                    }
                } else {
                    val transactions = TransactionLogParser.parse(text)
                    activity.runOnUiThread {
                        if (!activity.isDestroyed) TransactionImportDialog(UiKit(activity)).show(transactions, evidence) {
                            val card = it
                            PackWork.run(activity, { store.importPack(CardPack("pack-" + java.util.UUID.randomUUID(), card.name, cards = listOf(card))) }) { result ->
                                result.onSuccess { onImported() }.onFailure { message(it.message ?: activity.getString(R.string.import_failed)) }
                            }
                        }
                    }
                }
            } catch (e: Exception) { activity.runOnUiThread { if (!activity.isDestroyed) message(activity.getString(R.string.file_import_error, e.message)) } }
        }
    }
    /**
     * 先捕获待导出文本快照，再后台写入系统返回的文档 URI；必要时恢复私有缓存。
     * URI（Uniform Resource Identifier，统一资源标识符）由系统授权文档访问，不等同于本地文件路径。
     */
    private fun write(uri: Uri) {
        val snapshot = pendingExport
        io.execute {
            try {
                // 只有系统补发导出结果时才在后台恢复缓存，不拖慢每次打开首页。
                val text = snapshot.ifBlank { java.io.File(activity.cacheDir, "pending-export.txt").readText() }
                activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: error(activity.getString(R.string.file_write_failed))
                activity.runOnUiThread { if (!activity.isDestroyed) message(activity.getString(R.string.file_export_done)) }
            } catch (e: Exception) { activity.runOnUiThread { if (!activity.isDestroyed) message(activity.getString(R.string.file_export_error, e.message)) } }
        }
    }
    /** 停止接受新文件任务；已提交任务继续执行，完成回调自行检查页面是否销毁。 */
    fun close() { io.shutdown() }
}
