package com.iscolt.cardsimulator.data

import android.content.Context
import android.util.AtomicFile
import com.iscolt.cardsimulator.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 用户包一次原子写入，避免导入留下半个包；迁移后仍保留旧文件作为备份。 */
class CardPackRepository(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "card-packs.json"))
    private val legacy = File(context.filesDir, "cards.json")
    private val builtins = BuiltinCardCatalog.loadPacks { context.assets.open(it).bufferedReader().use { r -> r.readText() } }
    var readError: String? = null
        private set
    private var custom: List<CardPack>
    init {
        custom = try { val loaded = if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
            val root = file.openRead().bufferedReader().use { JSONObject(it.readText()) }
            require(root.getInt("storageVersion") == 1) { "不支持的本地存储版本" }
            val packs = root.getJSONArray("packs")
            (0 until packs.length()).map { CardPackCodec.parse(packs.getJSONObject(it).toString(), allowEmpty = true) }
        } else {
            val old = AtomicFile(legacy)
            val cards = if (legacy.exists() || File(legacy.path + ".bak").exists())
                old.openRead().bufferedReader().use { ScriptCodec.parse(it.readText(), allowEmpty = true) } else emptyList()
            listOf(CardPack(CardPacks.PERSONAL, "自建卡片", cards = cards).bound()).also { write(it) }
        }
        validate(loaded)
        loaded
        } catch (e: Exception) {
            readError = "卡包读取失败，原文件保留，修复前禁止写入：" + e.message
            listOf(CardPack(CardPacks.PERSONAL, "自建卡片"))
        }
    }
    fun packs() = builtins + custom
    fun find(id: String) = packs().firstOrNull { it.id == id }
    fun cards() = packs().flatMap { it.cards }
    private fun validate(packs: List<CardPack>) {
        require(packs.size <= 100 && packs.map { it.id }.distinct().size == packs.size) { "卡包数量过多或 ID 重复" }
        require(packs.none { p -> builtins.any { it.id == p.id } }) { "不能覆盖内置卡包" }
        require(packs.any { it.id == CardPacks.PERSONAL }) { "自建卡片包缺失" }
        packs.forEach { p -> CardPacks.validateId(p.id); CardPacks.validateName(p.name)
            CardPackCodec.parse(CardPackCodec.encode(p), allowEmpty = true) }
        require(packs.sumOf { it.cards.size } <= 2000) { "用户卡片总数最多 2000 张" }
    }
    private fun write(packs: List<CardPack>) {
        val root = JSONObject().put("storageVersion", 1).put("packs", JSONArray(packs.map { JSONObject(CardPackCodec.encode(it)) }))
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 20 * 1024 * 1024) { "用户卡包累计数据超过 20 MB" }
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    private fun commit(packs: List<CardPack>) { require(readError == null) { readError.orEmpty() }; validate(packs); write(packs); custom = packs }
    fun createPack(name: String, description: String): CardPack {
        val pack = CardPack("pack-" + java.util.UUID.randomUUID(), CardPacks.validateName(name), description.trim())
        commit(custom + pack)
        return pack
    }
    /** 编辑与跨包移动一次提交；目标编号冲突时整笔失败，不能先删除原卡。 */
    fun saveEditedCard(card: CardScript, originalKey: String?) {
        require(!card.builtIn && custom.any { it.id == card.packId }) { "目标卡包不存在或只读" }
        if (originalKey != null) {
            val original = custom.flatMap { it.cards }.firstOrNull { it.key == originalKey }
            require(original != null && original.id == card.id) { "原卡片不存在或已变化，请重新打开" }
        }
        require(custom.flatMap { it.cards }.none { it.key == card.key && it.key != originalKey }) {
            "目标卡包已存在相同编号的卡片，请选择其他卡包或复制卡片"
        }
        commit(custom.map { pack ->
            val samePack = pack.cards.any { it.key == originalKey } && pack.id == card.packId
            val updated = if (samePack) pack.cards.map { if (it.key == originalKey) card else it } else {
                val remaining = pack.cards.filterNot { it.key == originalKey }
                if (pack.id == card.packId) remaining + card else remaining
            }
            pack.copy(cards = updated).bound()
        })
    }
    fun importPack(incoming: CardPack, targetId: String? = null): CardPack {
        val id = targetId ?: incoming.id
        val old = find(id)
        require(id != CardPacks.PERSONAL && old?.builtIn != true) { "内置卡包和自建卡片不能整包替换" }
        require(targetId != null || old == null) { "卡包 ID 已存在，请选择更新或新建副本" }
        require(targetId == null || old != null) { "待更新卡包不存在" }
        val pack = incoming.copy(id = id, builtIn = false).bound()
        commit(custom.filterNot { it.id == id } + pack)
        return pack
    }
    fun saveCards(cards: List<CardScript>) {
        require(cards.none { it.builtIn }) { "内置卡片不能修改，请复制后编辑" }
        val grouped = cards.groupBy { it.packId.ifBlank { CardPacks.PERSONAL } }
        require(grouped.keys.all { id -> custom.any { it.id == id } }) { "目标卡包不存在或只读" }
        val updated = custom.map { p ->
            val incoming = grouped[p.id] ?: return@map p
            p.copy(cards = p.cards.filterNot { old -> incoming.any { it.id == old.id } } + incoming).bound()
        }
        commit(updated)
    }
    fun deleteCard(key: String) {
        require(custom.any { p -> p.cards.any { it.key == key } }) { "内置卡片不能删除" }
        commit(custom.map { it.copy(cards = it.cards.filterNot { c -> c.key == key }) })
    }
    fun rename(id: String, name: String) {
        require(custom.any { it.id == id }) { "内置卡包不能重命名" }
        val value = CardPacks.validateName(name)
        commit(custom.map { if (it.id == id) it.copy(name = value) else it })
    }
    fun delete(id: String) {
        require(id != CardPacks.PERSONAL && custom.any { it.id == id }) { "该卡包不能删除" }
        commit(custom.filterNot { it.id == id })
    }
}
