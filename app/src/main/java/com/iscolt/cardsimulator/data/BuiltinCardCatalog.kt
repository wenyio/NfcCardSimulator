package com.iscolt.cardsimulator.data

import com.iscolt.cardsimulator.core.*

/** 内置文件各自形成卡包，文件顺序不决定首页默认选择。 */
object BuiltinCardCatalog {
    val assetNames = listOf("demo-cards.json")
    /** 按资源文件清单解析内置只读卡包，并检查包 ID 和组合卡片 key 不重复；读取方式由调用方提供。 */
    fun loadPacks(readAsset: (String) -> String): List<CardPack> {
        val packs = assetNames.map { CardPackCodec.parse(readAsset(it), builtIn = true) }
        require(packs.map { it.id }.distinct().size == packs.size) { "内置卡包 ID 重复" }
        require(packs.flatMap { it.cards }.map { it.key }.distinct().size == packs.sumOf { it.cards.size }) { "内置卡片 ID 重复" }
        return packs
    }
    /** 加载内置包后展开为卡片列表，保留已绑定的包归属和只读标记。 */
    fun load(readAsset: (String) -> String): List<CardScript> = loadPacks(readAsset).flatMap { it.cards }
}
