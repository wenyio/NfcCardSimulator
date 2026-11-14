package com.iscolt.cardsimulator.core

import org.json.JSONObject
import java.util.UUID

/** 卡包采用版本 2；标准单卡或列表文件可导入为新包。 */
object CardPackCodec {
    /**
     * 读取当前支持的卡包或独立卡片文件，检查文件大小、格式版本和包信息，再解析卡片。
     * 独立卡片导出格式是当前功能；没有包信息时生成新包标识，最终绑定卡片归属。
     */
    fun parse(text: String, defaultName: String = "导入卡包", builtIn: Boolean = false, allowEmpty: Boolean = false): CardPack {
        require(text.toByteArray(Charsets.UTF_8).size <= ScriptCodec.MAX_BYTES) { "文件超过 2 MB" }
        val clean = text.removePrefix("\uFEFF").trim()
        val root = if (clean.startsWith("{")) JSONObject(clean) else null
        val version = root?.optInt("schemaVersion", 1) ?: 1
        require(version in 1..2) { "不支持的卡包 schemaVersion：" + version }
        val info = root?.optJSONObject("pack")
        if (version == 2) require(info != null && root.has("cards")) { "版本 2 须包含 pack 和 cards" }
        val id = info?.getString("id") ?: "pack-" + UUID.randomUUID()
        CardPacks.validateId(id)
        val name = CardPacks.validateName(info?.getString("name") ?: defaultName)
        val description = info?.optString("description").orEmpty()
        require(description.length <= 4000) { "卡包说明过长" }
        return CardPack(id, name, description, ScriptCodec.parse(clean, builtIn, allowEmpty), builtIn).bound()
    }
    /** 组合卡包的 schemaVersion=2、包信息及卡片数组，返回尚未写出的 JSON 对象。 */
    fun json(pack: CardPack): JSONObject = ScriptCodec.json(pack.cards)
        .put("schemaVersion", 2)
        .put("pack", JSONObject().put("id", pack.id).put("name", pack.name).put("description", pack.description))
    /** 生成带两空格缩进的卡包 JSON 文本，供查看、导出或大小校验使用。 */
    fun encode(pack: CardPack): String = json(pack).toString(2)
    /**
     * 校验待保存卡包；仍先序列化检查 2 MiB 上限，再检查包字段和卡片对象。
     * MiB（Mebibyte，二进制兆字节）为 1,048,576 字节；界面沿用 MB 文案。
     */
    fun validate(pack: CardPack) {
        ScriptCodec.checkSize(encode(pack))
        CardPacks.validateId(pack.id); CardPacks.validateName(pack.name)
        require(pack.description.length <= 4000) { "卡包说明过长" }
        ScriptValidation.cards(pack.cards, allowEmpty = true)
    }
}
