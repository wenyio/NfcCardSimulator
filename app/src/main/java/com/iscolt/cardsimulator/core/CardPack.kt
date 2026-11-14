package com.iscolt.cardsimulator.core

/** 卡片编号仅要求包内唯一，会话与日志使用卡包和卡片组成的 key。 */
data class CardPack(
    val id: String, val name: String, val description: String = "",
    val cards: List<CardScript> = emptyList(), val builtIn: Boolean = false
) {
    /** 返回新卡包，把包内每张卡的 packId 和只读标记绑定到所属包；不修改原列表。 */
    fun bound() = copy(cards = cards.map { it.copy(packId = id, builtIn = builtIn) })
}

/** 项目保留包标识和公共校验；包显示名称可变，身份必须使用稳定 ID。 */
object CardPacks {
    const val PERSONAL = "personal"
    const val DEMO = "connectivity-demo"

    /** 验证卡包稳定标识的字符集和长度；非法标识抛异常，不自动改写。 */
    fun validateId(id: String) {
        require(id.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) { "卡包 ID 仅允许 1–100 位字母、数字、下划线、点和连字符" }
    }

    /** 去除名称首尾空白后检查长度和控制字符，返回可显示的名称。 */
    fun validateName(name: String): String {
        val value = name.trim()
        require(value.length in 1..80 && value.none { it.isISOControl() }) { "卡包名称须为 1–80 个字符，不能包含控制字符" }
        return value
    }
}
