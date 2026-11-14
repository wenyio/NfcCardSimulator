package com.iscolt.cardsimulator.core

/** 卡组织与“自定义”来源分类分离；未知组织保留原文，固定组织统一大小写。 */
object CardSchemes {
    val fixed = listOf("Amex", "Discover", "JCB", "Mastercard", "UnionPay", "Visa", "其他")
    private val reserved = listOf("全部", "自定义", "＋ 添加卡组织")

    /** 合并固定组织和已有名称，去除空项并按不区分大小写的方式去重，保留首次出现的拼写。 */
    fun options(names: List<String>): List<String> = (fixed + names.map(String::trim))
        .filter { it.isNotEmpty() }.distinctBy { it.lowercase(java.util.Locale.ROOT) }

    /** 找到已知组织的统一拼写；未知名称仅去除首尾空白，保留其原文。 */
    fun canonical(name: String, names: List<String> = emptyList()): String =
        options(names).firstOrNull { it.equals(name.trim(), true) } ?: name.trim()

    /** 校验新增组织名称，拒绝控制字符、页面保留名称和已有选项；成功返回去空白后的名称。 */
    fun validateNew(name: String, existing: List<String>): String {
        val value = name.trim()
        require(value.isNotEmpty() && value.length <= 40) { "卡组织名称须为 1–40 个字符" }
        require(value.none { it.isISOControl() }) { "卡组织名称不能包含换行或控制字符" }
        require(reserved.none { it.equals(value, true) }) { "该名称已用于页面分类，请换一个名称" }
        require(options(existing).none {
            it.equals(
                value,
                true
            )
        }) { "该卡组织已存在，请从下拉列表选择" }
        return value
    }

    /** 按实际卡片的组织生成筛选分类，首项为全部；不把无卡片的固定组织加入标签页。 */
    fun categories(names: List<String>): List<String> = listOf("全部") +
            options(names).filter { value ->
                value !in reserved && names.any {
                    it.equals(
                        value,
                        true
                    )
                }
            }
}
