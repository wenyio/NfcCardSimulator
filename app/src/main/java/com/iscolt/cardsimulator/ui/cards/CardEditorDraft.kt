package com.iscolt.cardsimulator.ui.cards

import com.iscolt.cardsimulator.core.*
import org.json.JSONObject
import java.util.UUID

/** 编辑意图的稳定状态值；标题在 Activity 中映射到资源，保存/恢复不依赖中文显示文本。 */
enum class EditorMode { NEW, COPY, EDIT }

/** 草稿只在保存时校验，半成品也能恢复；不能用导入校验器读取未完成的输入。 */
class CardEditorDraft(
    var card: CardScript, val initial: String, val mode: EditorMode,
    val originalKey: String? = null, val initialPackId: String = card.packId
) {
    /** 单卡导出不含包归属，所以除内容快照外还必须单独比较 packId，防止遗漏跨包移动。 */
    val dirty get() = ScriptCodec.encode(listOf(card)) != initial || card.packId != initialPackId

    /** 编码当前草稿及比较基线，保留包归属和枚举模式；允许字段尚未填完。 */
    fun state(): JSONObject = JSONObject().put("card", ScriptCodec.cardJson(card))
        .put("packId", card.packId).put("initial", initial).put("editorMode", mode.name)
        .put("originalKey", originalKey).put("initialPackId", initialPackId)

    /** 保存前检查编码后的大小并规范化完整卡片，返回可写的用户卡副本；不执行持久化。 */
    fun validated(): CardScript {
        ScriptCodec.checkSize(ScriptCodec.encode(listOf(card)))
        return ScriptValidation.card(card).copy(builtIn = false)
    }

    companion object {
        /**
         * 按新建、复制或编辑创建工作副本：新建/复制生成新 ID，编辑保留原身份和归属。
         * 记录初始快照用于判断未保存修改，原卡内容不受草稿修改影响。
         */
        fun create(
            source: CardScript?,
            copy: Boolean,
            targetPackId: String = CardPacks.PERSONAL
        ): CardEditorDraft {
            val card = (source ?: CardScript("", "", "其他", emptyList(), "", emptyList())).copy(
                id = if (source == null || copy) "card-" + UUID.randomUUID() else source.id,
                packId = if (source == null || copy) targetPackId else source.packId.ifBlank { CardPacks.PERSONAL },
                name = source?.name?.let { it + if (copy) " 副本" else "" }.orEmpty(),
                builtIn = false
            )
            return CardEditorDraft(
                card, ScriptCodec.encode(listOf(card)),
                if (source == null) EditorMode.NEW else if (copy) EditorMode.COPY else EditorMode.EDIT,
                originalKey = if (source != null && !copy) card.key else null
            )
        }

        /**
         * 只按当前草稿字段恢复半成品，不调用要求完整卡片的导入校验器。
         * editorMode 直接读枚举名，不根据中文标题推测模式，不处理历史草稿格式。
         */
        fun restore(state: JSONObject): CardEditorDraft {
            val c = state.getJSONObject("card")
            val aids = c.getJSONArray("aids");
            val rules = c.getJSONArray("rules")
            val card = CardScript(
                c.getString("id"),
                c.getString("name"),
                c.getString("scheme"),
                (0 until aids.length()).map { aids.getString(it) },
                c.getString("description"),
                (0 until rules.length()).map { RuleCodec.decode(rules.getJSONObject(it)) },
                c.getBoolean("ordered"),
                c.getString("fallback"),
                metadata = CardMetadataCodec.parse(c),
                packId = state.getString("packId")
            )
            return CardEditorDraft(
                card,
                state.getString("initial"),
                EditorMode.valueOf(state.getString("editorMode")),
                if (state.isNull("originalKey")) null else state.getString("originalKey"),
                state.getString("initialPackId")
            )
        }
    }
}
