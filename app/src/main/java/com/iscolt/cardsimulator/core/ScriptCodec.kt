package com.iscolt.cardsimulator.core

import org.json.JSONArray
import org.json.JSONObject

object ScriptCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
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
    fun cardJson(card: CardScript): JSONObject =
        JSONObject().put("id", card.id).put("name", card.name).put("scheme", card.scheme)
                .put("aids", JSONArray(card.aids)).put("description", card.description)
                .put("ordered", card.ordered).put("fallback", card.fallback).put("rules", JSONArray(card.rules.map { RuleCodec.encode(it) })).apply {
                    if (card.metadata != CardMetadata()) put("metadata", CardMetadataCodec.encode(card.metadata))
                }
    fun json(cards: List<CardScript>): JSONObject = JSONObject().put("schemaVersion", 1)
        .put("cards", JSONArray(cards.map(::cardJson)))
    fun encode(cards: List<CardScript>): String = json(cards).toString(2)
    fun checkSize(text: String) {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "文件超过 2 MB" }
    }
}
