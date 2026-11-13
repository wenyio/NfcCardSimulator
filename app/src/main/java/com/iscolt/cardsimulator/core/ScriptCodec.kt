package com.iscolt.cardsimulator.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object ScriptCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
    private fun hex(value: String, field: String, minBytes: Int = 0): String {
        val result = Hex.clean(value)
        require(Hex.valid(result) && result.length >= minBytes * 2) { "$field：十六进制长度或内容错误" }
        require(result.length <= 131080) { "$field 过长" }
        return result
    }
    fun parse(text: String, builtIn: Boolean = false, allowEmpty: Boolean = false): List<CardScript> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "文件超过 2 MB" }
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
        val name = json.optString("name", "自定义卡片").trim()
        require(name.isNotBlank() && name.length <= 120) { "卡片名称须为 1–120 个字符" }
        val scheme = json.optString("scheme", "自定义").trim()
        val id = json.optString("id", "").ifBlank {
            "import-" + MessageDigest.getInstance("SHA-256").digest(json.toString().toByteArray())
                .take(10).joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        require(id.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) { "卡片 ID 仅允许字母、数字、下划线、点和连字符" }
        val aidArray = json.getJSONArray("aids")
        val aids = (0 until aidArray.length()).map { aidArray.getString(it) }
        val normalizedAids = aids.map { hex(it, "AID", 5) }.distinct()
        require(normalizedAids.isNotEmpty() && normalizedAids.size <= 16 && normalizedAids.all { it.length <= 32 }) {
            "须包含 1–16 个 AID，每个 5–16 字节"
        }
        val rulesArray = json.getJSONArray("rules")
        val rules = (0 until rulesArray.length()).map { index ->
            val item = rulesArray.getJSONObject(index)
            val command = hex(item.getString("command"), "规则 ${index + 1} command", 4)
            val response = hex(item.getString("response"), "规则 ${index + 1} response", 2)
            val mode = try { MatchMode.valueOf(item.optString("match", "EXACT").uppercase()) }
                catch (_: Exception) { throw IllegalArgumentException("规则 ${index + 1} match 须为 EXACT、MASK 或 PREFIX") }
            val mask = if (mode == MatchMode.MASK) hex(item.getString("mask"), "mask") else ""
            require(mode != MatchMode.MASK || mask.length == command.length) { "mask 必须与 command 等长" }
            ApduRule(command, response, mode, mask, item.optString("label"))
        }
        require(rules.size in 1..1000) { "每张卡须包含 1–1000 条规则" }
        return CardScript(id, name, scheme, normalizedAids, json.optString("description"), rules,
            json.optBoolean("ordered", false), hex(json.optString("fallback", "6D00"), "fallback", 2), builtIn, CardMetadataCodec.parse(json))
    }
    fun encode(cards: List<CardScript>): String {
        val array = JSONArray()
        cards.forEach { card ->
            val rules = JSONArray()
            card.rules.forEach { rule ->
                val item = JSONObject().put("command", rule.command).put("response", rule.response)
                    .put("match", rule.mode.name).put("label", rule.label)
                if (rule.mode == MatchMode.MASK) item.put("mask", rule.mask)
                rules.put(item)
            }
            array.put(JSONObject().put("id", card.id).put("name", card.name).put("scheme", card.scheme)
                .put("aids", JSONArray(card.aids)).put("description", card.description)
                .put("ordered", card.ordered).put("fallback", card.fallback).put("rules", rules).apply {
                    if (card.metadata != CardMetadata()) put("metadata", CardMetadataCodec.encode(card.metadata))
                })
        }
        return JSONObject().put("schemaVersion", 1).put("cards", array).toString(2)
    }
}
