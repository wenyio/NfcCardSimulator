package com.iscolt.cardsimulator.data

import android.content.Context
import com.iscolt.cardsimulator.core.CardSchemes
import org.json.JSONArray

/** 新增组织独立持久化，即使编辑卡片时取消，也可在下次选择。 */
class CardSchemeStore(context: Context, private val cards: () -> List<com.iscolt.cardsimulator.core.CardScript>) {
    private val preferences = context.getSharedPreferences("card-schemes", Context.MODE_PRIVATE)
    /** 从偏好设置读取新增组织列表；读取失败返回空列表，不修改已保存内容。 */
    private fun saved(): List<String> = runCatching {
        val array = JSONArray(preferences.getString("names", "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())
    /** 合并独立保存的组织和卡片中使用的组织，生成统一去重选项。 */
    fun options() = CardSchemes.options(saved() + cards().map { it.scheme })
    /** 校验并保存新增组织，返回规范名称；此保存独立于卡片草稿，取消编辑不会撤销它。 */
    fun add(name: String): String {
        val value = CardSchemes.validateNew(name, options())
        preferences.edit().putString("names", JSONArray(saved() + value).toString()).apply()
        return value
    }
}
