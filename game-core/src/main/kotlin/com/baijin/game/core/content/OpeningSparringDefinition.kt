package com.baijin.game.core.content

import com.baijin.game.core.QuestStage
import kotlinx.serialization.Serializable

@Serializable
data class OpeningSparringDefinition(
    val id: String,
    val npcId: String,
    val playerDefense: SparringDefenseDefinition,
    val opponent: SparringOpponentDefinition,
    val damageVariance: Int,
    val lootGroups: List<SparringLootGroup> = listOf(SparringLootGroup(1)),
    val requiredLoot: List<SparringQuestLoot> = emptyList(),
)

@Serializable
data class SparringLootGroup(val weight: Int, val items: Map<String, Int> = emptyMap())

@Serializable
data class SparringQuestLoot(
    val questId: String,
    val stage: QuestStage,
    val items: Map<String, Int>,
    val objectiveId: String? = null,
)

@Serializable
data class SparringOpponentDefinition(
    val strength: Int,
    val agility: Int,
    val maxHealth: Int,
    val maxInnerPower: Int,
    val defense: SparringDefenseDefinition,
)

@Serializable
data class SparringDefenseDefinition(
    val baseParryPercent: Int,
    val protection: Int = 0,
    val evasionBonus: Int = 0,
)
