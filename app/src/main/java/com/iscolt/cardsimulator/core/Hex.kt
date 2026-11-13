package com.iscolt.cardsimulator.core

object Hex {
    fun clean(value: String): String = value.replace(Regex("\\s+"), "").uppercase()
    fun valid(value: String): Boolean = value.length % 2 == 0 && value.matches(Regex("[0-9A-F]*"))
    fun bytes(value: String): ByteArray {
        val text = clean(value)
        require(valid(text)) { "十六进制格式不正确" }
        return ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
    fun text(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it.toInt() and 255) }
}
