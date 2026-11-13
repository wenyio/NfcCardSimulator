package com.iscolt.cardsimulator.core

/** 来源只记录证据，不表示已经验证原工具行为或交易结果。 */
data class CardSource(
    val format: String = "", val fileName: String = "", val sha256: String = "",
    val originalCardId: String = "", val configId: String = ""
)

data class TestScenario(
    val category: String = "", val purpose: String = "", val expectedResult: String = "",
    val conditions: String = "", val amount: String = "",
    // 旧配置中的 PDOL/CDOL 保留为原始数据；绝不用于推断 DOL 定义或自动匹配。
    val legacyPdol: String = "", val legacyCdol: String = ""
)

data class CardMetadata(
    val source: CardSource = CardSource(), val scenario: TestScenario = TestScenario(),
    val warnings: List<String> = emptyList()
)
