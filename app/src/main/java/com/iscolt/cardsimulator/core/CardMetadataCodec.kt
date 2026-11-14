package com.iscolt.cardsimulator.core

import org.json.JSONArray
import org.json.JSONObject

/** 可选扩展字段独立编解码；未提供 metadata 时使用空元数据。 */
object CardMetadataCodec {
    private fun text(json: JSONObject, key: String): String {
        if (!json.has(key)) return ""
        require(json.get(key) is String) { "metadata.$key 须为字符串" }
        return json.getString(key)
    }
    private fun bounded(value: String, key: String, limit: Int = 4000): String =
        value.also { require(it.length <= limit) { "metadata.$key 过长" } }
    fun validate(value: CardMetadata): CardMetadata {
        val s = value.source; val t = value.scenario
        val sha = bounded(s.sha256, "sha256", 64)
        require(sha.isEmpty() || sha.matches(Regex("[a-fA-F0-9]{64}"))) { "来源 SHA-256 须为 64 位十六进制" }
        require(value.warnings.size <= 30) { "转换提示最多 30 条" }
        value.warnings.forEach { require(it.length <= 2000) { "转换提示过长" } }
        return value.copy(source = CardSource(bounded(s.format, "format", 120), bounded(s.fileName, "fileName", 512), sha.lowercase(),
            bounded(s.originalCardId, "originalCardId", 120), bounded(s.configId, "configId", 240)),
            scenario = TestScenario(bounded(t.category, "category", 120), bounded(t.purpose, "purpose"), bounded(t.expectedResult, "expectedResult"),
                bounded(t.conditions, "conditions"), bounded(t.amount, "amount", 120), bounded(t.legacyPdol, "legacyPdol"), bounded(t.legacyCdol, "legacyCdol")))
    }
    private fun child(json: JSONObject, key: String): JSONObject =
        if (json.has(key)) json.getJSONObject(key) else JSONObject()

    fun parse(card: JSONObject): CardMetadata {
        if (!card.has("metadata")) return CardMetadata()
        val json = card.getJSONObject("metadata")
        val s = child(json, "source"); val t = child(json, "scenario")
        val warnings = if (json.has("warnings")) json.getJSONArray("warnings") else JSONArray()
        val messages = (0 until warnings.length()).map {
            require(warnings.get(it) is String) { "转换提示须为字符串" }
            warnings.getString(it)
        }
        return validate(CardMetadata(CardSource(text(s, "format"), text(s, "fileName"), text(s, "sha256"),
            text(s, "originalCardId"), text(s, "configId")),
            TestScenario(text(t, "category"), text(t, "purpose"), text(t, "expectedResult"),
                text(t, "conditions"), text(t, "amount"), text(t, "legacyPdol"), text(t, "legacyCdol")), messages))
    }

    fun encode(value: CardMetadata): JSONObject {
        val s = value.source; val t = value.scenario
        return JSONObject().put("source", JSONObject().put("format", s.format).put("fileName", s.fileName)
            .put("sha256", s.sha256).put("originalCardId", s.originalCardId).put("configId", s.configId))
            .put("scenario", JSONObject().put("category", t.category).put("purpose", t.purpose)
                .put("expectedResult", t.expectedResult).put("conditions", t.conditions).put("amount", t.amount)
                .put("legacyPdol", t.legacyPdol).put("legacyCdol", t.legacyCdol))
            .put("warnings", JSONArray(value.warnings))
    }
}
