package com.iscolt.cardsimulator.core

import org.json.JSONArray
import org.json.JSONObject

/** 可选扩展字段独立编解码，兼容 schemaVersion=1；旧卡片无需补齐字段。 */
object CardMetadataCodec {
    private fun text(json: JSONObject, key: String, limit: Int = 4000): String {
        if (!json.has(key)) return ""
        require(json.get(key) is String) { "metadata.$key 须为字符串" }
        return json.getString(key).also { require(it.length <= limit) { "metadata.$key 过长" } }
    }
    private fun child(json: JSONObject, key: String): JSONObject =
        if (json.has(key)) json.getJSONObject(key) else JSONObject()

    fun parse(card: JSONObject): CardMetadata {
        if (!card.has("metadata")) return legacy(card)
        val json = card.getJSONObject("metadata")
        val s = child(json, "source"); val t = child(json, "scenario")
        val sha = text(s, "sha256", 64)
        require(sha.isEmpty() || sha.matches(Regex("[a-fA-F0-9]{64}"))) { "来源 SHA-256 须为 64 位十六进制" }
        val warnings = if (json.has("warnings")) json.getJSONArray("warnings") else JSONArray()
        require(warnings.length() <= 30) { "转换提示最多 30 条" }
        val messages = (0 until warnings.length()).map {
            require(warnings.get(it) is String) { "转换提示须为字符串" }
            warnings.getString(it).also { value -> require(value.length <= 2000) { "转换提示过长" } }
        }
        return CardMetadata(CardSource(text(s, "format", 120), text(s, "fileName", 512), sha.lowercase(),
            text(s, "originalCardId", 120), text(s, "configId", 240)),
            TestScenario(text(t, "category", 120), text(t, "purpose"), text(t, "expectedResult"),
                text(t, "conditions"), text(t, "amount", 120), text(t, "legacyPdol"), text(t, "legacyCdol")), messages)
    }

    private fun legacy(card: JSONObject): CardMetadata {
        if (!card.has("APDU") || card.has("rules")) return CardMetadata()
        // 识别的是字段结构，不凭文件名断言来源软件，也不从卡片名称猜测预期结果。
        return CardMetadata(
            source = CardSource(format = "旧版 APDU JSON", originalCardId = text(card, "cardID", 120),
                configId = text(card, "config", 240)),
            scenario = TestScenario(category = text(card, "category", 120), amount = text(card, "amount", 120),
                legacyPdol = text(card, "PDOL"), legacyCdol = text(card, "CDOL")),
            warnings = listOf("旧版 APDU 已转为完整匹配；未复现来源工具的隐式前缀或 GAC 回退逻辑。",
                "固定回复不会重新计算密文或动态签名；原金额、PDOL/CDOL 数据仅供核对，不自动限制交易。")
        )
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
