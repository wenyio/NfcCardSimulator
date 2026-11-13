package com.iscolt.cardsimulator.core

enum class MatchMode { EXACT, MASK, PREFIX }
data class ApduRule(
    val command: String, val response: String,
    val mode: MatchMode = MatchMode.EXACT, val mask: String = "", val label: String = ""
) {
    fun matches(input: String): Boolean = when (mode) {
        MatchMode.EXACT -> input == command
        MatchMode.PREFIX -> input.startsWith(command)
        MatchMode.MASK -> input.length == command.length && command.indices.step(2).all { i ->
            val bits = mask.substring(i, i + 2).toInt(16)
            (input.substring(i, i + 2).toInt(16) and bits) ==
                (command.substring(i, i + 2).toInt(16) and bits)
        }
    }
}
data class CardScript(
    val id: String, val name: String, val scheme: String, val aids: List<String>,
    val description: String, val rules: List<ApduRule>, val ordered: Boolean = false,
    val fallback: String = "6D00", val builtIn: Boolean = false,
    val metadata: CardMetadata = CardMetadata(), val packId: String = ""
) {
    val key: String get() = if (packId.isBlank()) id else packId + ":" + id
}
data class Exchange(val response: String, val rule: String, val matched: Boolean, val difference: String = "")
