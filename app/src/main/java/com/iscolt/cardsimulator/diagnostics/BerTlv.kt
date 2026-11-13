package com.iscolt.cardsimulator.diagnostics

import com.iscolt.cardsimulator.core.Hex

internal data class TlvValue(val tag: String, val value: String)

/** 严格检查整个 BER-TLV 结构后再提供标签，损坏回复不产生部分定义。 */
internal object BerTlv {
    fun parse(hex: String): List<TlvValue> {
        require(Hex.valid(hex)) { "TLV 十六进制无效" }
        val bytes = Hex.bytes(hex)
        val result = mutableListOf<TlvValue>()
        fun read(start: Int, limit: Int, depth: Int) {
            require(depth <= 12) { "TLV 嵌套过深" }
            var p = start
            while (p < limit) {
                val first = bytes[p].toInt() and 255
                val tagStart = p++
                if (first and 31 == 31) {
                    var next: Int
                    do {
                        require(p < limit && p - tagStart < 4) { "Tag 截断" }
                        next = bytes[p++].toInt() and 255
                    } while (next and 128 != 0)
                }
                val tag = Hex.text(bytes.copyOfRange(tagStart, p))
                require(p < limit) { "缺少 TLV 长度" }
                var length = bytes[p++].toInt() and 255
                if (length and 128 != 0) {
                    val count = length and 127
                    require(count in 1..3 && p + count <= limit) { "TLV 长度编码错误" }
                    length = 0
                    repeat(count) { length = (length shl 8) or (bytes[p++].toInt() and 255) }
                }
                require(length <= limit - p) { "TLV 长度越界" }
                result.add(TlvValue(tag, Hex.text(bytes.copyOfRange(p, p + length))))
                if (first and 32 != 0) read(p, p + length, depth + 1)
                p += length
            }
        }
        read(0, bytes.size, 0)
        return result
    }
}
