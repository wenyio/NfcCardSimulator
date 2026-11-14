package com.iscolt.cardsimulator.core

import org.json.JSONObject
import java.util.UUID

/** 卡包采用版本 2；标准单卡或列表文件可导入为新包。 */
object CardPackCodec {
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
    fun json(pack: CardPack): JSONObject = ScriptCodec.json(pack.cards)
        .put("schemaVersion", 2)
        .put("pack", JSONObject().put("id", pack.id).put("name", pack.name).put("description", pack.description))
    fun encode(pack: CardPack): String = json(pack).toString(2)
    /** 保留原序列化大小限制，直接校验对象，避免把整个卡包重新解析一遍。 */
    fun validate(pack: CardPack) {
        ScriptCodec.checkSize(encode(pack))
        CardPacks.validateId(pack.id); CardPacks.validateName(pack.name)
        require(pack.description.length <= 4000) { "卡包说明过长" }
        ScriptValidation.cards(pack.cards, allowEmpty = true)
    }
}
