package com.iscolt.cardsimulator.data

import android.content.Context
import com.iscolt.cardsimulator.core.CardSchemes
import org.json.JSONArray

/** 新增组织独立持久化，即使编辑卡片时取消，也可在下次选择。 */
class CardSchemeStore(context: Context, private val cards: () -> List<com.iscolt.cardsimulator.core.CardScript>) {
    private val preferences = context.getSharedPreferences("card-schemes", Context.MODE_PRIVATE)
    private fun saved(): List<String> = runCatching {
        val array = JSONArray(preferences.getString("names", "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())
    fun options() = CardSchemes.options(saved() + cards().map { it.scheme })
    fun add(name: String): String {
        val value = CardSchemes.validateNew(name, options())
        preferences.edit().putString("names", JSONArray(saved() + value).toString()).apply()
        return value
    }
}
