package com.iscolt.cardsimulator.core

import org.json.JSONArray
import org.json.JSONObject

/** 单卡或卡片集合的 JSON 边界：解析时校验，编码时允许草稿；卡包身份由 CardPackCodec 外层处理。 */
object ScriptCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
    /**
     * 检查 UTF-8 文本大小，识别单卡、数组或 cards 容器，再逐卡解析并检查包内 ID 唯一性。
     * JSON（JavaScript Object Notation，JavaScript 对象表示法）是此处的文本交换格式。
     */
    fun parse(text: String, builtIn: Boolean = false, allowEmpty: Boolean = false): List<CardScript> {
        checkSize(text)
        val trimmed = text.removePrefix("\uFEFF").trim()
        val objects = if (trimmed.startsWith("[")) JSONArray(trimmed) else {
            val root = JSONObject(trimmed)
            root.optJSONArray("cards") ?: JSONArray().put(root)
        }
        require(objects.length() in (if (allowEmpty) 0 else 1)..200) { "每个文件须包含 1–200 张卡片" }
        val cards = (0 until objects.length()).map { parseCard(objects.getJSONObject(it), builtIn) }
        require(cards.map { it.id }.distinct().size == cards.size) { "文件中存在重复卡片 ID" }
        return cards
    }
    /**
     * 把单卡 JSON 字段转换为模型，再委托共享校验器规范化；无 ID 时按内容生成标识。
     * 未知匹配模式和缺失的必需字段直接报错，不猜测旧字段含义。
     */
    private fun parseCard(json: JSONObject, builtIn: Boolean): CardScript {
        val id = json.optString("id", "").ifBlank { ScriptValidation.generatedId(json.toString()) }
        val aidArray = json.getJSONArray("aids")
        val aids = (0 until aidArray.length()).map { aidArray.getString(it) }
        val rulesArray = json.getJSONArray("rules")
        val rules = (0 until rulesArray.length()).map { index ->
            val item = rulesArray.getJSONObject(index)
            val mode = try { MatchMode.valueOf(item.optString("match", "EXACT").uppercase()) }
                catch (_: Exception) { throw IllegalArgumentException("规则 ${index + 1} match 须为 EXACT、MASK 或 PREFIX") }
            if (mode == MatchMode.MASK) item.getString("mask")
            RuleCodec.decode(item, mode)
        }
        return ScriptValidation.card(CardScript(id, json.optString("name", "自定义卡片"), json.optString("scheme", "自定义"), aids, json.optString("description"), rules,
            json.optBoolean("ordered", false), json.optString("fallback", "6D00"), builtIn, CardMetadataCodec.parse(json)))
    }
    /** 构造单卡 JSON 对象，不校验草稿完整性；非空元数据才输出，归属由外层卡包保存。 */
    fun cardJson(card: CardScript): JSONObject =
        JSONObject().put("id", card.id).put("name", card.name).put("scheme", card.scheme)
                .put("aids", JSONArray(card.aids)).put("description", card.description)
                .put("ordered", card.ordered).put("fallback", card.fallback).put("rules", JSONArray(card.rules.map { RuleCodec.encode(it) })).apply {
                    if (card.metadata != CardMetadata()) put("metadata", CardMetadataCodec.encode(card.metadata))
                }
    /** 构造当前独立卡片导出容器 schemaVersion=1；卡包导出在此基础上添加包信息。 */
    fun json(cards: List<CardScript>): JSONObject = JSONObject().put("schemaVersion", 1)
        .put("cards", JSONArray(cards.map(::cardJson)))
    /** 把卡片列表编码为可阅读的缩进 JSON 文本，不写文件，也不替调用方校验。 */
    fun encode(cards: List<CardScript>): String = json(cards).toString(2)
    /** 按 UTF-8 编码后的字节数检查 2 MiB 上限；中文字符数不等于文件字节数。 */
    fun checkSize(text: String) {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "文件超过 2 MB" }
    }
}
