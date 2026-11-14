package com.iscolt.cardsimulator.core

/** 字节与十六进制文本的边界工具；一字节等于 8 位，也等于两个十六进制字符。 */
object Hex {
    /**
     * 去除正则匹配的空白并转为大写；不检查内容是否构成有效十六进制。
     * Kotlin 普通字符串要用两个反斜杠表示正则所需的一个反斜杠，因此这里的正则语义为匹配空白。
     * 规范化与合法性验证分开，调用方仍需执行 valid；特殊 Unicode 空白不保证均被此正则覆盖。
     */
    fun clean(value: String): String = value.replace(Regex("\\s+"), "").uppercase()

    /** 检查是否仅含大写十六进制字符且字符数为偶数；空串也返回 true，最小长度由业务层限制。 */
    fun valid(value: String): Boolean = value.length % 2 == 0 && value.matches(Regex("[0-9A-F]*"))

    /** 规范化并验证十六进制字符串，再将每两个字符转换为一个 Byte；非法输入抛异常。 */
    fun bytes(value: String): ByteArray {
        val text = clean(value)
        require(valid(text)) { "十六进制格式不正确" }
        return ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /** 将字节数组转为无空格的大写十六进制字符串；每字节固定输出两字符，包括前导零。 */
    fun text(bytes: ByteArray): String = bytes.joinToString("") {
        // Kotlin Byte 是有符号数，and 255（0xFF）保留低 8 位，得到 0～255 的值再格式化。
        "%02X".format(it.toInt() and 255)
    }
}
