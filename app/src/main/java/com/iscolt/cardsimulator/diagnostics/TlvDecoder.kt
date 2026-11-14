package com.iscolt.cardsimulator.diagnostics
import com.iscolt.cardsimulator.core.Hex

/** BER-TLV 查看：先检查边界，再读长度/值，限制嵌套以处理损坏日志。 */
object TlvDecoder {
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
