package com.baijin.game.core.content

import kotlinx.serialization.Serializable

@Serializable
data class GameContent(
    val contentVersion: Int,
    val startLocationId: String,
    val locations: List<LocationDefinition>,
    val npcs: List<NpcDefinition>,
    val quests: List<QuestDefinition>,
    val martialArts: List<MartialArtDefinition>,
    val items: List<ItemDefinition>,
    val appearance: AppearanceDefinition,
)

interface ContentEntry {
    val id: String
    val name: String
}

@Serializable
data class LocationDefinition(override val id: String, override val name: String) : ContentEntry

@Serializable
data class NpcDefinition(
    override val id: String,
    override val name: String,
    val locationId: String,
    val morality: Int,
    val initialAffinity: Int,
    val luck: Int,
) : ContentEntry

@Serializable
data class QuestDefinition(
    override val id: String,
    override val name: String,
    val giverNpcId: String,
    val turnInNpcId: String,
    val reward: QuestReward,
) : ContentEntry

@Serializable
data class QuestReward(
    val coins: Int,
    val potential: Int,
    val experience: Int,
    val items: List<ItemAmount>,
    val martialArts: List<MartialArtGrant>,
)

@Serializable
data class ItemAmount(val itemId: String, val quantity: Int)

@Serializable
data class MartialArtGrant(val martialArtId: String, val level: Int)

@Serializable
data class MartialArtDefinition(
    override val id: String,
    override val name: String,
    val maxLevel: Int,
) : ContentEntry

@Serializable
data class ItemDefinition(override val id: String, override val name: String) : ContentEntry

@Serializable
data class AppearanceDefinition(
    val weights: AppearanceWeights,
    val thresholds: List<Int>,
    val descriptions: List<AppearanceDescription>,
)

@Serializable
data class AppearanceWeights(
    val strength: Int,
    val agility: Int,
    val insight: Int,
    val constitution: Int,
)

@Serializable
data class AppearanceDescription(val male: String, val female: String)
