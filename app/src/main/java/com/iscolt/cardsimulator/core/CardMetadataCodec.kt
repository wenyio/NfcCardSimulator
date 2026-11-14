package com.iscolt.cardsimulator.core

import org.json.JSONArray
import org.json.JSONObject

/** 可选扩展字段独立编解码；未提供 metadata 时使用空元数据。 */
object CardMetadataCodec {
    /** 读取可选字符串字段：缺失返回空串，存在但类型错误则拒绝，防止静默转换类型。 */
    private fun text(json: JSONObject, key: String): String {
        if (!json.has(key)) return ""
        require(json.get(key) is String) { "metadata.$key 须为字符串" }
        return json.getString(key)
    }

    /** 限制元数据字符串的字符数，超限抛出参数异常；返回原值，不裁剪内容。 */
    private fun bounded(value: String, key: String, limit: Int = 4000): String =
        value.also { require(it.length <= limit) { "metadata.$key 过长" } }

    /**
     * 校验来源、场景和提示的长度及 SHA-256 格式，返回哈希已统一小写的副本。
     * SHA-256（Secure Hash Algorithm 256-bit，256 位安全散列算法）用于来源指纹，不证明来源可信。
     */
    fun validate(value: CardMetadata): CardMetadata {
        val s = value.source;
        val t = value.scenario
        val sha = bounded(s.sha256, "sha256", 64)
        require(sha.isEmpty() || sha.matches(Regex("[a-fA-F0-9]{64}"))) { "来源 SHA-256 须为 64 位十六进制" }
        require(value.warnings.size <= 30) { "转换提示最多 30 条" }
        value.warnings.forEach { require(it.length <= 2000) { "转换提示过长" } }
        return value.copy(
            source = CardSource(
                bounded(s.format, "format", 120),
                bounded(s.fileName, "fileName", 512),
                sha.lowercase(),
                bounded(s.originalCardId, "originalCardId", 120),
                bounded(s.configId, "configId", 240)
            ),
            scenario = TestScenario(
                bounded(t.category, "category", 120),
                bounded(t.purpose, "purpose"),
                bounded(t.expectedResult, "expectedResult"),
                bounded(t.conditions, "conditions"),
                bounded(t.amount, "amount", 120),
                bounded(t.legacyPdol, "legacyPdol"),
                bounded(t.legacyCdol, "legacyCdol")
            )
        )
    }

    /** 取得可选子对象；缺失时返回空对象，已有字段类型不符时由 JSON 读取抛错。 */
    private fun child(json: JSONObject, key: String): JSONObject =
        if (json.has(key)) json.getJSONObject(key) else JSONObject()

    /** 从卡片 JSON 读取可选元数据并校验；未提供 metadata 时返回空元数据。 */
    fun parse(card: JSONObject): CardMetadata {
        if (!card.has("metadata")) return CardMetadata()
        val json = card.getJSONObject("metadata")
        val s = child(json, "source");
        val t = child(json, "scenario")
        val warnings = if (json.has("warnings")) json.getJSONArray("warnings") else JSONArray()
        val messages = (0 until warnings.length()).map {
            require(warnings.get(it) is String) { "转换提示须为字符串" }
            warnings.getString(it)
        }
        return validate(
            CardMetadata(
                CardSource(
                    text(s, "format"), text(s, "fileName"), text(s, "sha256"),
                    text(s, "originalCardId"), text(s, "configId")
                ),
                TestScenario(
                    text(t, "category"),
                    text(t, "purpose"),
                    text(t, "expectedResult"),
                    text(t, "conditions"),
                    text(t, "amount"),
                    text(t, "legacyPdol"),
                    text(t, "legacyCdol")
                ), messages
            )
        )
    }

    /** 将来源、场景与提示编码为 JSON 对象；只做字段转换，不执行校验或写盘。 */
    fun encode(value: CardMetadata): JSONObject {
        val s = value.source;
        val t = value.scenario
        return JSONObject().put(
            "source", JSONObject().put("format", s.format).put("fileName", s.fileName)
                .put("sha256", s.sha256).put("originalCardId", s.originalCardId)
                .put("configId", s.configId)
        )
            .put(
                "scenario", JSONObject().put("category", t.category).put("purpose", t.purpose)
                    .put("expectedResult", t.expectedResult).put("conditions", t.conditions)
                    .put("amount", t.amount)
                    .put("legacyPdol", t.legacyPdol).put("legacyCdol", t.legacyCdol)
            )
            .put("warnings", JSONArray(value.warnings))
    }
}
