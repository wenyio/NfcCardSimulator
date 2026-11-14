package com.iscolt.cardsimulator.importer

import com.iscolt.cardsimulator.core.*
import java.security.MessageDigest

/** 对原始文件字节计算指纹；已携带的历史来源不会被本次导入文件覆盖。 */
data class ImportEvidence(val fileName: String, val sha256: String) {
    companion object {
        fun from(bytes: ByteArray, fileName: String) = ImportEvidence(fileName.take(512),
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })
    }
    fun attach(card: CardScript, defaultFormat: String): CardScript {
        val source = card.metadata.source
        // 文件名和哈希为同一份证据，不将历史哈希与新文件名混合。
        val origin = if (source.fileName.isBlank() && source.sha256.isBlank())
            source.copy(fileName = fileName, sha256 = sha256) else source
        return card.copy(metadata = card.metadata.copy(source = origin.copy(
            format = origin.format.ifBlank { defaultFormat })))
    }
}
