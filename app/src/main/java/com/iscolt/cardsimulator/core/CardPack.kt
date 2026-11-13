package com.iscolt.cardsimulator.core

/** 卡片编号仅要求包内唯一，会话与日志使用卡包和卡片组成的 key。 */
data class CardPack(val id: String, val name: String, val description: String = "",
    val cards: List<CardScript> = emptyList(), val builtIn: Boolean = false) {
    fun bound() = copy(cards = cards.map { it.copy(packId = id, builtIn = builtIn) })
}
object CardPacks {
    const val PERSONAL = "personal"
    const val DEMO = "connectivity-demo"
    fun validateId(id: String) {
        require(id.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) { "卡包 ID 仅允许 1–100 位字母、数字、下划线、点和连字符" }
    }
    fun validateName(name: String): String {
        val value = name.trim()
        require(value.length in 1..80 && value.none { it.isISOControl() }) { "卡包名称须为 1–80 个字符，不能包含控制字符" }
        return value
    }
}
