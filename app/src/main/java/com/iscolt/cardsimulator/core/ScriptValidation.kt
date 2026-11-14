package com.iscolt.cardsimulator.core

import java.security.MessageDigest

/** 导入和编辑共用的对象校验；返回规范化副本，不修改草稿或已保存数据。 */
object ScriptValidation {
    fun hex(value: String, field: String, minBytes: Int = 0): String {
        val result = Hex.clean(value)
        require(Hex.valid(result) && result.length >= minBytes * 2) { "$field：十六进制长度或内容错误" }
        require(result.length <= 131080) { "$field 过长" }
        return result
    }
    fun rule(rule: ApduRule, index: Int = 0): ApduRule {
        val command = hex(rule.command, "规则 ${index + 1} command", 4)
        val response = hex(rule.response, "规则 ${index + 1} response", 2)
        val mask = if (rule.mode == MatchMode.MASK) hex(rule.mask, "mask") else ""
        require(rule.mode != MatchMode.MASK || mask.length == command.length) { "mask 必须与 command 等长" }
        return rule.copy(command = command, response = response, mask = mask)
    }
    fun cards(cards: List<CardScript>, allowEmpty: Boolean = false): List<CardScript> {
        require(cards.size in (if (allowEmpty) 0 else 1)..200) { "每个文件须包含 1–200 张卡片" }
        val normalized = cards.map(::card)
        require(normalized.map { it.id }.distinct().size == normalized.size) { "文件中存在重复卡片 ID" }
        return normalized
    }
    fun generatedId(json: String) = "import-" + MessageDigest.getInstance("SHA-256").digest(json.toByteArray())
        .take(10).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun card(card: CardScript): CardScript {
        val name = card.name.trim()
        require(name.isNotBlank() && name.length <= 120) { "卡片名称须为 1–120 个字符" }
        val id = card.id.ifBlank { generatedId(ScriptCodec.cardJson(card).toString()) }
        require(id.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) { "卡片 ID 仅允许字母、数字、下划线、点和连字符" }
        val aids = card.aids.map { hex(it, "AID", 5) }.distinct()
        require(aids.isNotEmpty() && aids.size <= 16 && aids.all { it.length <= 32 }) { "须包含 1–16 个 AID，每个 5–16 字节" }
        val rules = card.rules.mapIndexed { index, entry -> rule(entry, index) }
        require(rules.size in 1..1000) { "每张卡须包含 1–1000 条规则" }
        return card.copy(id = id, name = name, scheme = card.scheme.trim(), aids = aids, rules = rules,
            fallback = hex(card.fallback, "fallback", 2), metadata = CardMetadataCodec.validate(card.metadata))
    }
}
