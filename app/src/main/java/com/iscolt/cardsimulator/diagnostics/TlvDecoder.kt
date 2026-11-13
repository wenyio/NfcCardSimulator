package com.iscolt.cardsimulator.diagnostics
import com.iscolt.cardsimulator.core.Hex

/** BER-TLV 查看：先检查边界，再读长度/值，限制嵌套以处理损坏日志。 */
object TlvDecoder {
    fun decodeResponse(response: String): String = try {
        val bytes = Hex.bytes(response)
        require(bytes.size >= 2) { "缺少 SW1/SW2" }
        val end = bytes.size - 2
        val lines = mutableListOf("SW=${Hex.text(bytes.copyOfRange(end, bytes.size))}")
        fun parse(start: Int, limit: Int, depth: Int) {
            require(depth <= 12) { "嵌套超过 12 层" }
            var offset = start
            while (offset < limit) {
                val tagStart = offset
                val first = bytes[offset++].toInt() and 255
                if ((first and 31) == 31) {
                    var next: Int
                    do {
                        require(offset < limit && offset - tagStart < 4) { "Tag 截断或过长" }
                        next = bytes[offset++].toInt() and 255
                    } while ((next and 128) != 0)
                }
                val tag = Hex.text(bytes.copyOfRange(tagStart, offset))
                require(offset < limit) { "$tag 缺少长度" }
                var length = bytes[offset++].toInt() and 255
                if ((length and 128) != 0) {
                    val count = length and 127
                    require(count in 1..3 && offset + count <= limit) { "$tag 长度编码错误" }
                    length = 0
                    repeat(count) { length = (length shl 8) or (bytes[offset++].toInt() and 255) }
                }
                require(length <= limit - offset) { "$tag 声明长度超过剩余数据" }
                lines.add("  ".repeat(depth) + "$tag [$length] " + Hex.text(bytes.copyOfRange(offset, offset + length)))
                if ((first and 32) != 0) parse(offset, offset + length, depth + 1)
                offset += length
            }
        }
        parse(0, end, 0)
        lines.joinToString(System.lineSeparator())
    } catch (e: Exception) { "非完整 BER-TLV：${e.message}" }
}
