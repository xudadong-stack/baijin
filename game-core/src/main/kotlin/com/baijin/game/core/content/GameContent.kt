package com.baijin.game.core.content

import com.baijin.game.core.EquipmentDefinition
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
    val scenes: List<SceneDefinition>,
    val creation: CharacterCreationDefinition,
    val time: GameTimeDefinition,
    val supplies: SupplyDefinition,
    val openingTraining: OpeningTrainingDefinition? = null,
    val openingSparring: OpeningSparringDefinition? = null,
    val openingArmourShop: OpeningArmourShopDefinition? = null,
)

@Serializable
data class OpeningArmourShopDefinition(val npcId: String, val stock: List<ArmourStock>)

@Serializable
data class ArmourStock(val itemId: String, val price: Int)

@Serializable
data class SupplyDefinition(
    val sellerNpcId: String,
    val foodItemId: String,
    val foodPrice: Int,
    val hungerReduction: Int,
    val wellLocationId: String,
    val thirstReduction: Int,
)

interface ContentEntry {
    val id: String
    val name: String
}

@Serializable
data class NpcDefinition(
    override val id: String,
    override val name: String,
    val locationId: String,
    val morality: Int,
    val initialAffinity: Int,
    val luck: Int,
    val dialogue: Map<AffinityBand, List<DialogueTopic>>,
    val services: List<NpcService> = emptyList(),
) : ContentEntry

@Serializable
enum class AffinityBand { WARY, NORMAL, CLOSE }

@Serializable
enum class NpcService { HOUSEWORK, FOOD, MESSAGE, MESSAGE_HINT, SECT, TRAINING, SPAR, FIND, HERBS, ARMOUR }

@Serializable
data class DialogueTopic(
    val title: String,
    val text: String,
    val minAppearanceTier: Int? = null,
    val maxAppearanceTier: Int? = null,
    val service: NpcService? = null,
)

@Serializable
data class QuestDefinition(
    override val id: String,
    override val name: String,
    val giverNpcId: String,
    val turnInNpcId: String,
    val reward: QuestReward,
    val objectives: List<QuestObjective> = emptyList(),
    val repeatBelowExperience: Int? = null,
    val assignOnDialogue: Boolean = false,
    val settleOnObjective: Boolean = false,
) : ContentEntry

@Serializable
data class QuestObjective(
    override val id: String,
    override val name: String,
    val actionLabel: String,
    val locationId: String? = null,
    val npcId: String? = null,
    val completionText: String,
    val assignmentText: String? = null,
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
    val openingLevels: List<OpeningMartialArtLevel> = emptyList(),
    val openingAttackLines: List<String> = emptyList(),
) : ContentEntry

@Serializable
data class OpeningMartialArtLevel(
    val potentialCost: Int,
    val powerBonus: Int,
    val hitBonus: Int = 0,
    val criticalBonusBasisPoints: Int = 0,
)

@Serializable
data class OpeningTrainingDefinition(
    val basicUnarmedArtId: String,
    val sects: List<OpeningSectDefinition>,
    val staff: OpeningStaffDefinition? = null,
)

@Serializable
data class OpeningStaffDefinition(val questId: String, val basicArtId: String, val appliedArtId: String)

@Serializable
data class OpeningSectDefinition(
    override val id: String,
    override val name: String,
    val unarmedArtId: String,
) : ContentEntry

@Serializable
enum class ItemKind { GENERAL, MATERIAL, QUEST }

@Serializable
data class ItemDefinition(
    override val id: String,
    override val name: String,
    val equipment: EquipmentDefinition? = null,
    val healthRecovery: Int? = null,
    val description: String? = null,
    val kind: ItemKind = ItemKind.GENERAL,
) : ContentEntry

@Serializable
data class AppearanceDefinition(
    val weights: AppearanceWeights,
    val thresholds: List<Int>,
    val descriptions: List<AppearanceDescription>,
) {
    internal fun tierFor(score: Long): Int = 1 + thresholds.count { score >= it }
}

@Serializable
data class AppearanceWeights(
    val strength: Int,
    val agility: Int,
    val insight: Int,
    val constitution: Int,
)

@Serializable
data class AppearanceDescription(val male: String, val female: String)
