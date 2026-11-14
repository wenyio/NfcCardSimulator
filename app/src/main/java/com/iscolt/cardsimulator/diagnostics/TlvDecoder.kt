package com.iscolt.cardsimulator.diagnostics
import com.iscolt.cardsimulator.core.Hex

/** BER-TLV 查看：先检查边界，再读长度/值，限制嵌套以处理损坏日志。 */
object TlvDecoder {
    /**
     * 先分离响应最后两字节 SW1/SW2，再把前面的数据按 BER-TLV 展示；解析失败返回提示文字。
     * SW（Status Word，状态字）描述命令执行结果；这里仅解码展示，不修改脚本回复。
     */
    fun decodeResponse(response: String): String = try {
        val bytes = Hex.bytes(response)
        require(bytes.size >= 2) { "缺少 SW1/SW2" }
        val end = bytes.size - 2
        val lines = mutableListOf("SW=${Hex.text(bytes.copyOfRange(end, bytes.size))}")
        BerTlv.parse(bytes, end).forEach {
            lines.add("  ".repeat(it.depth) + "${it.tag} [${it.value.length / 2}] ${it.value}")
        }
        lines.joinToString(System.lineSeparator())
    } catch (e: Exception) { "非完整 BER-TLV：${e.message}" }
}
