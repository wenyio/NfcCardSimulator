package com.iscolt.cardsimulator.core

/** 卡组织与“自定义”来源分类分离；未知组织保留原文，固定组织统一大小写。 */
object CardSchemes {
    val fixed = listOf("Amex", "Discover", "JCB", "Mastercard", "UnionPay", "Visa", "其他")
    private val reserved = listOf("全部", "自定义", "＋ 添加卡组织")
    fun options(names: List<String>): List<String> = (fixed + names.map(String::trim))
        .filter { it.isNotEmpty() }.distinctBy { it.lowercase(java.util.Locale.ROOT) }
    fun canonical(name: String, names: List<String> = emptyList()): String =
        options(names).firstOrNull { it.equals(name.trim(), true) } ?: name.trim()
    fun validateNew(name: String, existing: List<String>): String {
        val value = name.trim()
        require(value.isNotEmpty() && value.length <= 40) { "卡组织名称须为 1–40 个字符" }
        require(value.none { it.isISOControl() }) { "卡组织名称不能包含换行或控制字符" }
        require(reserved.none { it.equals(value, true) }) { "该名称已用于页面分类，请换一个名称" }
        require(options(existing).none { it.equals(value, true) }) { "该卡组织已存在，请从下拉列表选择" }
        return value
    }
    fun categories(names: List<String>): List<String> = listOf("全部") +
        options(names).filter { value -> value !in reserved && names.any { it.equals(value, true) } }
}
