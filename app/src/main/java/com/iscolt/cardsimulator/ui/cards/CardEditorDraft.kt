package com.iscolt.cardsimulator.ui.cards

import com.iscolt.cardsimulator.core.*
import org.json.JSONObject
import java.util.UUID

enum class EditorMode { NEW, COPY, EDIT }

/** 草稿只在保存时校验，半成品也能恢复；不能用导入校验器读取未完成的输入。 */
class CardEditorDraft(var card: CardScript, val initial: String, val mode: EditorMode,
    val originalKey: String? = null, val initialPackId: String = card.packId) {
    val dirty get() = ScriptCodec.encode(listOf(card)) != initial || card.packId != initialPackId
    fun state(): JSONObject = JSONObject().put("card", ScriptCodec.cardJson(card))
        .put("packId", card.packId).put("initial", initial).put("editorMode", mode.name)
        .put("originalKey", originalKey).put("initialPackId", initialPackId)
    fun validated(): CardScript {
        ScriptCodec.checkSize(ScriptCodec.encode(listOf(card)))
        return ScriptValidation.card(card).copy(builtIn = false)
    }
    companion object {
        fun create(source: CardScript?, copy: Boolean, targetPackId: String = CardPacks.PERSONAL): CardEditorDraft {
            val card = (source ?: CardScript("", "", "其他", emptyList(), "", emptyList())).copy(
                id = if (source == null || copy) "card-" + UUID.randomUUID() else source.id,
                packId = if (source == null || copy) targetPackId else source.packId.ifBlank { CardPacks.PERSONAL },
                name = source?.name?.let { it + if (copy) " 副本" else "" }.orEmpty(), builtIn = false)
            return CardEditorDraft(card, ScriptCodec.encode(listOf(card)),
                if (source == null) EditorMode.NEW else if (copy) EditorMode.COPY else EditorMode.EDIT,
                originalKey = if (source != null && !copy) card.key else null)
        }
        fun restore(state: JSONObject): CardEditorDraft {
            val c = state.getJSONObject("card")
            val aids = c.getJSONArray("aids"); val rules = c.getJSONArray("rules")
            val card = CardScript(c.getString("id"), c.getString("name"), c.getString("scheme"),
                (0 until aids.length()).map { aids.getString(it) }, c.getString("description"),
                (0 until rules.length()).map { RuleCodec.decode(rules.getJSONObject(it)) },
                c.getBoolean("ordered"), c.getString("fallback"), metadata = CardMetadataCodec.parse(c), packId = state.getString("packId"))
            return CardEditorDraft(card, state.getString("initial"), EditorMode.valueOf(state.getString("editorMode")),
                if (state.isNull("originalKey")) null else state.getString("originalKey"), state.getString("initialPackId"))
        }
    }
}
