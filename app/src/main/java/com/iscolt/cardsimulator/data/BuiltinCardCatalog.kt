package com.iscolt.cardsimulator.data

import com.iscolt.cardsimulator.core.*

/** 内置文件各自形成卡包，文件顺序不决定首页默认选择。 */
object BuiltinCardCatalog {
    val assetNames = listOf("demo-cards.json")
    fun loadPacks(readAsset: (String) -> String): List<CardPack> {
        val packs = assetNames.map { CardPackCodec.parse(readAsset(it), builtIn = true) }
        require(packs.map { it.id }.distinct().size == packs.size) { "内置卡包 ID 重复" }
        require(packs.flatMap { it.cards }.map { it.key }.distinct().size == packs.sumOf { it.cards.size }) { "内置卡片 ID 重复" }
        return packs
    }
    fun load(readAsset: (String) -> String): List<CardScript> = loadPacks(readAsset).flatMap { it.cards }
}
