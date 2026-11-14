package com.iscolt.cardsimulator.data

import android.content.Context
import android.util.AtomicFile
import com.iscolt.cardsimulator.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 用户包一次原子写入，避免导入留下半个包。 */
class CardPackRepository(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "card-packs.json"))
    private val builtins = BuiltinCardCatalog.loadPacks {
        context.assets.open(it).bufferedReader().use { r -> r.readText() }
    }
    var readError: String? = null
        private set
    @Volatile
    private var custom: List<CardPack>

    init {
        custom = try {
            val loaded = if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
                val root = file.openRead().bufferedReader().use { JSONObject(it.readText()) }
                require(root.getInt("storageVersion") == 1) { "不支持的本地存储版本" }
                val packs = root.getJSONArray("packs")
                (0 until packs.length()).map {
                    CardPackCodec.parse(
                        packs.getJSONObject(it).toString(), allowEmpty = true
                    )
                }
            } else {
                listOf(CardPack(CardPacks.PERSONAL, "自建卡片").bound()).also { write(it) }
            }
            validate(loaded)
            loaded
        } catch (e: Exception) {
            readError = "卡包读取失败，原文件保留，修复前禁止写入：" + e.message
            listOf(CardPack(CardPacks.PERSONAL, "自建卡片"))
        }
    }

    /** 返回内置包与用户包组成的列表快照；调用方通过仓库方法提交修改。 */
    fun packs() = builtins + custom

    /** 按稳定包 ID 查找，找不到返回 null。 */
    fun find(id: String) = packs().firstOrNull { it.id == id }

    /** 展开所有包中的卡片，包含内置只读卡和用户卡。 */
    fun cards() = packs().flatMap { it.cards }

    /** 校验用户包总量、标识冲突、必需个人包和每包内容；不写文件、不替换内存状态。 */
    private fun validate(packs: List<CardPack>) {
        require(packs.size <= 100 && packs.map { it.id }
            .distinct().size == packs.size) { "卡包数量过多或 ID 重复" }
        require(packs.none { p -> builtins.any { it.id == p.id } }) { "不能覆盖内置卡包" }
        require(packs.any { it.id == CardPacks.PERSONAL }) { "自建卡片包缺失" }
        packs.forEach(CardPackCodec::validate)
        require(packs.sumOf { it.cards.size } <= 2000) { "用户卡片总数最多 2000 张" }
    }

    /**
     * 将完整用户包集合编码后检查 20 MiB 上限，再使用 AtomicFile（原子文件）提交。
     * 写入失败调用 failWrite 回滚文件并抛出异常；内存集合由 commit 在成功后替换。
     */
    private fun write(packs: List<CardPack>) {
        val root = JSONObject().put("storageVersion", 1)
            .put("packs", JSONArray(packs.map(CardPackCodec::json)))
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 20 * 1024 * 1024) { "用户卡包累计数据超过 20 MB" }
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    /**
     * 统一写入出口：读取失败时禁止覆盖，校验和原子写入均成功后才更新内存 custom。
     * 调用此仓库的修改操作由上层 SimulatorStore 串行保护；本函数自身不加锁。
     */
    private fun commit(packs: List<CardPack>) {
        require(readError == null) { readError.orEmpty() }
        validate(packs)
        write(packs)
        // 文件提交成功才切换读者可见的内存集合；任何前置异常都会停在旧状态。
        custom = packs
    }

    /** 生成随机包 ID 并提交空用户包；校验或文件写入失败时不更新内存包列表。 */
    fun createPack(name: String, description: String): CardPack {
        val pack = CardPack(
            "pack-" + java.util.UUID.randomUUID(),
            CardPacks.validateName(name),
            description.trim()
        )
        commit(custom + pack)
        return pack
    }

    /**
     * 校验目标包、原卡身份和目标 ID 冲突，再构造整个新集合一次提交。
     * 跨包移动与同包编辑共用此事务；任何检查或写入失败均不先移除原卡。
     */
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
            val updated =
                if (samePack) pack.cards.map { if (it.key == originalKey) card else it } else {
                    val remaining = pack.cards.filterNot { it.key == originalKey }
                    if (pack.id == card.packId) remaining + card else remaining
                }
            pack.copy(cards = updated).bound()
        })
    }

    /** 按调用方选择新建或整包更新，拒绝覆盖内置包和个人包；成功后返回绑定归属的新包。 */
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

    /** 仅允许删除用户卡，构造过滤后的包集合后原子提交；找不到可删卡时抛异常。 */
    fun deleteCard(key: String) {
        require(custom.any { p -> p.cards.any { it.key == key } }) { "内置卡片不能删除" }
        commit(custom.map { it.copy(cards = it.cards.filterNot { c -> c.key == key }) })
    }

    /** 仅修改用户包名称，保留 ID 和卡片；名称校验或写入失败时保留原数据。 */
    fun rename(id: String, name: String) {
        require(custom.any { it.id == id }) { "内置卡包不能重命名" }
        val value = CardPacks.validateName(name)
        commit(custom.map { if (it.id == id) it.copy(name = value) else it })
    }

    /** 删除指定用户包；个人包和内置包不可删除，实际提交仍经过统一校验。 */
    fun delete(id: String) {
        require(id != CardPacks.PERSONAL && custom.any { it.id == id }) { "该卡包不能删除" }
        commit(custom.filterNot { it.id == id })
    }
}
