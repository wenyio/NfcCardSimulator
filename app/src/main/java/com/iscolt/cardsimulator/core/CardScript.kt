package com.iscolt.cardsimulator.core

/** EXACT 为完整相等，MASK 为按位掩码比较，PREFIX 为前缀相等；名称是持久化值，不翻译。 */
enum class MatchMode { EXACT, MASK, PREFIX }

/**
 * 一条命令对应一份固定回复，字段使用十六进制文本，每两个字符表示一字节。
 * APDU（Application Protocol Data Unit，应用协议数据单元）是终端与卡片应用交换的报文。
 * command 是 C-APDU（Command APDU，命令报文）；response 是完整 R-APDU（Response APDU，响应报文）。
 * response 的最后两字节为 SW1/SW2（Status Word 1/2，状态字节 1/2），本应用不会自动追加。
 * label 只作展示；mask 仅在 MASK 模式参与匹配，不代表重新计算回复中的密文。
 */
data class ApduRule(
    val command: String, val response: String,
    val mode: MatchMode = MatchMode.EXACT, val mask: String = "", val label: String = ""
) {
    /**
     * 判断规范化的十六进制输入是否满足本规则；不推进步骤，也不生成回复。
     * 调用前须保证规则有效：MASK 模式的 mask 与 command 等长，字符串每两字符代表一字节。
     */
    fun matches(input: String): Boolean = when (mode) {
        MatchMode.EXACT -> input == command
        // 前缀之后可以有额外数据；规则列表顺序决定多条命中时选哪一条。
        MatchMode.PREFIX -> input.startsWith(command)
        // 掩码不允许改变总长度；步长 2 是跳过一个字节的两个十六进制字符。
        MatchMode.MASK -> input.length == command.length && command.indices.step(2).all { offset ->
            val bits = mask.substring(offset, offset + 2).toInt(16)
            // and 是按位与：掩码位为 1 才比较，为 0 则忽略。FF 比较全部位，00 忽略整字节。
            // 例如期望 A0、实际 AF、掩码 F0，双方与 F0 后均为 A0，因此该字节匹配。
            (input.substring(offset, offset + 2).toInt(16) and bits) ==
                    (command.substring(offset, offset + 2).toInt(16) and bits)
        }
    }
}

/**
 * 一张模拟卡的配置，而非实体卡芯片的完整实现。
 * aids 是 AID（Application Identifier，应用标识符）列表，启动时交给 Android 注册路由。
 * ordered 为 true 时按步骤执行，否则按列表顺序找首条匹配；无命中使用 fallback。
 * scheme、description、metadata 是分类或说明信息；实际回复由 rules 决定。
 * packId 表示所属包，builtIn 表示只读来源；id 只要求在包内唯一。
 */
data class CardScript(
    val id: String, val name: String, val scheme: String, val aids: List<String>,
    val description: String, val rules: List<ApduRule>, val ordered: Boolean = false,
    val fallback: String = "6D00", val builtIn: Boolean = false,
    val metadata: CardMetadata = CardMetadata(), val packId: String = ""
) {
    /** 运行时组合身份，用于选卡与日志；独立卡尚未绑定包时使用卡片 id。 */
    val key: String get() = if (packId.isBlank()) id else packId + ":" + id
}

/** 一次命令处理结果：response 用于回复，rule/difference 用于说明，matched 表示是否实际命中规则。 */
data class Exchange(
    val response: String,
    val rule: String,
    val matched: Boolean,
    val difference: String = ""
)
