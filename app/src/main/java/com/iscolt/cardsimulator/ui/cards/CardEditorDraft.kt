package com.iscolt.cardsimulator.ui.cards

import com.iscolt.cardsimulator.core.*
import org.json.JSONObject
import java.util.UUID

/** 草稿只在保存时校验，半成品也能恢复；不能用导入校验器读取未完成的输入。 */
class CardEditorDraft(var card: CardScript, val initial: String, val title: String,
    val originalKey: String? = null, val initialPackId: String = card.packId) {
    val dirty get() = ScriptCodec.encode(listOf(card)) != initial || card.packId != initialPackId
    fun state(): JSONObject = JSONObject().put("card", JSONObject(ScriptCodec.encode(listOf(card))).getJSONArray("cards").getJSONObject(0))
        .put("packId", card.packId).put("initial", initial).put("title", title)
        .put("originalKey", originalKey).put("initialPackId", initialPackId)
    fun validated() = ScriptCodec.parse(ScriptCodec.encode(listOf(card))).single().copy(packId = card.packId)
    companion object {
        fun create(source: CardScript?, copy: Boolean, targetPackId: String = CardPacks.PERSONAL): CardEditorDraft {
            val card = (source ?: CardScript("", "", "其他", emptyList(), "", emptyList())).copy(
                id = if (source == null || copy) "card-" + UUID.randomUUID() else source.id,
                packId = if (source == null || copy) targetPackId else source.packId.ifBlank { CardPacks.PERSONAL },
                name = source?.name?.let { it + if (copy) " 副本" else "" }.orEmpty(), builtIn = false)
            return CardEditorDraft(card, ScriptCodec.encode(listOf(card)),
                if (source == null) "新建卡片" else if (copy) "复制卡片" else "编辑卡片",
                originalKey = if (source != null && !copy) card.key else null)
        }
        fun restore(state: JSONObject): CardEditorDraft {
            val c = state.getJSONObject("card")
            val aids = c.getJSONArray("aids"); val rules = c.getJSONArray("rules")
            val card = CardScript(c.getString("id"), c.getString("name"), c.getString("scheme"),
                (0 until aids.length()).map { aids.getString(it) }, c.getString("description"),
                (0 until rules.length()).map { i -> rules.getJSONObject(i).let {
                    ApduRule(it.getString("command"), it.getString("response"), MatchMode.valueOf(it.getString("match")),
                        it.optString("mask"), it.optString("label")) } },
                c.getBoolean("ordered"), c.getString("fallback"), metadata = CardMetadataCodec.parse(c), packId = state.optString("packId", CardPacks.PERSONAL))
            return CardEditorDraft(card, state.getString("initial"), state.getString("title"),
                if (state.isNull("originalKey")) null else state.getString("originalKey"), state.optString("initialPackId", card.packId))
        }
    }
}
