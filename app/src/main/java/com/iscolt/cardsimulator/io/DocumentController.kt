package com.iscolt.cardsimulator.io

import android.app.AlertDialog
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.importer.TransactionLogParser
import com.iscolt.cardsimulator.importer.CardFileImporter
import com.iscolt.cardsimulator.importer.ImportEvidence
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.ui.cards.CardPackImportDialog
import com.iscolt.cardsimulator.ui.cards.TransactionImportDialog
import java.util.concurrent.Executors

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
    fun import() = importer.launch(arrayOf("*/*"))
    fun exportCard(card: CardScript) {
        stage(ScriptCodec.encode(listOf(card))); jsonExporter.launch(card.id + ".json")
    }
    fun exportPack(pack: CardPack) {
        stage(CardPackCodec.encode(pack)); jsonExporter.launch(pack.id + ".json")
    }
    fun exportLogs(cardId: String? = null) { stage(store.logText(cardId)); logExporter.launch("nfc-apdu-log.txt") }
    private fun stage(text: String) {
        pendingExport = text
        java.io.File(activity.cacheDir, "pending-export.txt").writeText(text)
    }
    private fun read(uri: Uri) {
        io.execute {
            try {
                val bytes = activity.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        require(output.size() + count <= ScriptCodec.MAX_BYTES) { "文件超过 2 MB" }
                        output.write(buffer, 0, count)
                    }; output.toByteArray()
                } ?: error("无法读取文件")
                val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                val fileName = runCatching {
                    activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
                    }.orEmpty()
                }.getOrDefault("")
                val evidence = ImportEvidence.from(bytes, fileName)
                if (text.trimStart().startsWith("{") || text.trimStart().startsWith("[")) {
                    val parsed = CardPackCodec.parse(text, fileName.substringBeforeLast(".").ifBlank { "导入卡包" })
                    val pack = parsed.copy(cards = parsed.cards.map { evidence.attach(it, "卡片 JSON") })
                    activity.runOnUiThread {
                        if (!activity.isDestroyed) CardPackImportDialog(UiKit(activity), store, onImported, message).show(pack)
                    }
                } else {
                    val transactions = TransactionLogParser.parse(text)
                    activity.runOnUiThread {
                        if (!activity.isDestroyed) TransactionImportDialog(UiKit(activity)).show(transactions, evidence) {
                            store.importPack(CardPack("pack-" + java.util.UUID.randomUUID(), it.name, cards = listOf(it))); onImported()
                        }
                    }
                }
            } catch (e: Exception) { activity.runOnUiThread { if (!activity.isDestroyed) message("导入失败：${e.message}") } }
        }
    }
    private fun write(uri: Uri) {
        val snapshot = pendingExport
        io.execute {
            try {
                // 只有系统补发导出结果时才在后台恢复缓存，不拖慢每次打开首页。
                val text = snapshot.ifBlank { java.io.File(activity.cacheDir, "pending-export.txt").readText() }
                activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: error("无法写入文件")
                activity.runOnUiThread { if (!activity.isDestroyed) message("导出完成") }
            } catch (e: Exception) { activity.runOnUiThread { if (!activity.isDestroyed) message("导出失败：${e.message}") } }
        }
    }
    fun close() { io.shutdown() }
}
