package com.iscolt.cardsimulator.diagnostics

import com.iscolt.cardsimulator.core.Hex

/** 展开后的节点：tag/value 是十六进制文本，depth 为原始嵌套层级，最外层为 0。 */
internal data class TlvValue(val tag: String, val value: String, val depth: Int)

/** 严格检查整个 BER-TLV 结构后再提供标签，损坏回复不产生部分定义。 */
internal object BerTlv {
    /**
     * 解析本项目支持的 BER-TLV 子集，严格校验边界、标签和嵌套深度；损坏输入直接抛异常。
     * BER（Basic Encoding Rules，基本编码规则）；TLV（Tag-Length-Value，标签—长度—值）。
     * hex 重载输入为十六进制文本；bytes 重载的 end 是不包含在内的字节结束位置，可排除响应尾部状态字。
     */
    fun parse(hex: String): List<TlvValue> {
        require(Hex.valid(hex)) { "TLV 十六进制无效" }
        return parse(Hex.bytes(hex))
    }
    /**
     * 解析本项目支持的 BER-TLV 子集，严格校验边界、标签和嵌套深度；损坏输入直接抛异常。
     * BER（Basic Encoding Rules，基本编码规则）；TLV（Tag-Length-Value，标签—长度—值）。
     * hex 重载输入为十六进制文本；bytes 重载的 end 是不包含在内的字节结束位置，可排除响应尾部状态字。
     */
    fun parse(bytes: ByteArray, end: Int = bytes.size): List<TlvValue> {
        val result = mutableListOf<TlvValue>()
        /**
         * 递归读取半开区间 [start, limit) 中的 TLV，depth 从 0 开始；所有偏移和长度均以字节为单位。
         * 构造型标签递归展开；结果包含父节点和子节点，但整个解析成功才返回给调用方。
         */
        fun read(start: Int, limit: Int, depth: Int) {
            require(depth <= 12) { "嵌套超过 12 层" }
            var offset = start
            while (offset < limit) {
                // Byte 转无符号整数；偏移按字节推进，不是十六进制字符串字符位置。
                val first = bytes[offset].toInt() and 255
                val tagStart = offset++
                // 首字节低 5 位均为 1（0x1F）表示标签还有后续字节，例如 9F38。
                if (first and 31 == 31) {
                    var next: Int
                    do {
                        require(offset < limit && offset - tagStart < 4) { "Tag 截断或过长" }
                        next = bytes[offset++].toInt() and 255
                        // 后续标签字节最高位（0x80）为 1 表示仍有下一字节。
                    } while (next and 128 != 0)
                }
                val tag = Hex.text(bytes.copyOfRange(tagStart, offset))
                require(offset < limit) { "$tag 缺少长度" }
                var length = bytes[offset++].toInt() and 255
                // 短格式：00～7F 直接表示值长度；长格式：低 7 位表示后续长度字节的数量。
                if (length and 128 != 0) {
                    val count = length and 127
                    // 本实现只接收 1～3 个长度字节；count=0 的不定长编码不受支持。
                    require(count in 1..3 && offset + count <= limit) { "$tag 长度编码错误" }
                    length = 0
                    // 大端累积：例如 81 80 表示值长 128 字节，82 01 00 表示 256 字节。
                    repeat(count) { length = (length shl 8) or (bytes[offset++].toInt() and 255) }
                }
                require(length <= limit - offset) { "$tag 声明长度超过剩余数据" }
                result.add(TlvValue(tag, Hex.text(bytes.copyOfRange(offset, offset + length)), depth))
                // 首字节 0x20 位为 1 表示构造型：值本身仍是一组 TLV，例如 6F、A5。
                if (first and 32 != 0) read(offset, offset + length, depth + 1)
                // 无论是否递归，都跳过完整值，继续读取当前层的下一个节点。
                offset += length
            }
        }
        read(0, end, 0)
        return result
    }
}
