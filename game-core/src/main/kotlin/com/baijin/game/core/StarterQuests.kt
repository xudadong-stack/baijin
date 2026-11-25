package com.baijin.game.core

import com.baijin.game.core.content.AffinityBand
import com.baijin.game.core.content.DialogueTopic
import com.baijin.game.core.content.GameContent
import com.baijin.game.core.content.NpcDefinition
import com.baijin.game.core.content.OpeningStaffDefinition
import com.baijin.game.core.content.QuestDefinition
import com.baijin.game.core.content.QuestObjective
import kotlinx.serialization.Serializable
import kotlin.random.Random

enum class MoralityBand { SELF_SERVING, NEUTRAL, BENEVOLENT }

data class NpcDialogue(val npc: NpcDefinition, val affinity: AffinityBand, val topics: List<DialogueTopic>) {
    val morality: MoralityBand = when {
        npc.morality <= -20 -> MoralityBand.SELF_SERVING
        npc.morality >= 20 -> MoralityBand.BENEVOLENT
        else -> MoralityBand.NEUTRAL
    }
}

@Serializable
enum class QuestStage { IN_PROGRESS, READY, TURNED_IN }

@Serializable
data class QuestRound(val number: Int, val objectiveId: String, val stage: QuestStage)

data class SectPreparation(val questId: String, val stage: QuestStage?) {
    val unlocked: Boolean get() = stage == QuestStage.TURNED_IN
}

internal fun OpeningStaffDefinition.preparation(state: GameState) =
    SectPreparation(questId, state.questRounds[questId]?.stage)

data class QuestView(
    val quest: QuestDefinition,
    val round: QuestRound?,
    val objective: QuestObjective?,
    val objectivePlace: String?,
    val giverPlace: String,
    val turnInPlace: String,
    val acceptanceBlock: ActionRequirement?,
    val canCompleteHere: Boolean,
)

data class NpcMarker(val name: String, val tile: Tile, val serviceTile: Tile)

internal class StarterQuests(private val content: GameContent, private val training: MartialTraining) {
    private val quests = content.quests.filter { it.objectives.isNotEmpty() }.associateBy { it.id }
    private val npcs = content.npcs.associateBy { it.id }
    private val locations = content.locations.associateBy { it.id }
    private val scenes = content.scenes.associateBy { it.id }
    private val staff = content.openingTraining?.staff

    fun validState(state: GameState): Boolean =
        state.potential >= 0 && state.experience >= 0 &&
            state.affinities.all { (id, value) -> id in npcs && value in -100..100 } &&
            state.questRounds.all { (id, round) ->
                val quest = quests[id]
                quest != null && round.number > 0 &&
                    (quest.repeatBelowExperience != null || round.number == 1) &&
                    quest.objectives.any { it.id == round.objectiveId }
            }

    fun npcAt(state: GameState): NpcDialogue? {
        val npc = content.npcs.firstOrNull { at(state, it.locationId) } ?: return null
        val affinity = when (state.affinities[npc.id] ?: npc.initialAffinity) {
            in -100..-20 -> AffinityBand.WARY
            in 20..100 -> AffinityBand.CLOSE
            else -> AffinityBand.NORMAL
        }
        val tier = state.character.appearanceTier
        val topics = npc.dialogue.getValue(affinity).filter {
            tier >= (it.minAppearanceTier ?: 1) && tier <= (it.maxAppearanceTier ?: Int.MAX_VALUE)
        }
        return NpcDialogue(npc, affinity, topics)
    }

    fun markers(scene: String): List<NpcMarker> = content.npcs.mapNotNull { npc ->
        val place = locations.getValue(npc.locationId)
        if (place.sceneId == scene) NpcMarker(npc.name, place.objectTile ?: place.tile, place.tile) else null
    }

    fun views(state: GameState): List<QuestView> = quests.values.map { quest ->
        val round = state.questRounds[quest.id]
        val objective = quest.objectives.firstOrNull { it.id == round?.objectiveId }
        QuestView(
            quest, round, objective, objective?.let { target ->
                target.npcId?.let(::npcPlace) ?: locationPlace(target.locationId!!)
            },
            npcPlace(quest.giverNpcId), npcPlace(quest.turnInNpcId), acceptanceBlock(state, quest, round),
            round?.stage == QuestStage.IN_PROGRESS && objective != null && at(state, objectiveLocation(objective)),
        )
    }

    fun accept(state: GameState, action: GameAction.AcceptQuest): QuestChange {
        val quest = quests[action.questId] ?: return QuestChange.Blocked(ActionRequirement.QuestUnavailable)
        if (quest.assignOnDialogue) return QuestChange.Blocked(ActionRequirement.QuestUnavailable)
        if (!at(state, npcLocation(quest.giverNpcId))) return QuestChange.Blocked(ActionRequirement.QuestLocation)
        val round = state.questRounds[quest.id]
        if ((round?.number ?: 0) != action.previousRound) return QuestChange.Blocked(ActionRequirement.QuestRoundChanged)
        acceptanceBlock(state, quest, round)?.let { return QuestChange.Blocked(it) }
        val objective = quest.objectives.find { it.id == action.objectiveId }
            ?: return QuestChange.Blocked(ActionRequirement.QuestUnavailable)
        return change(state, quest, QuestRound(action.previousRound + 1, objective.id, QuestStage.IN_PROGRESS))
    }

    fun assignOnDialogue(state: GameState, npcId: String, seed: Long): GameState {
        val random = Random(seed)
        return quests.values.fold(state) { current, quest ->
            val round = current.questRounds[quest.id]
            if (quest.giverNpcId == npcId && quest.assignOnDialogue && acceptanceBlock(current, quest, round) == null) {
                val objective = quest.objectives.random(random)
                change(current, quest, QuestRound((round?.number ?: 0) + 1, objective.id, QuestStage.IN_PROGRESS)).state
            } else current
        }
    }

    fun complete(state: GameState, action: GameAction.CompleteQuestObjective): QuestChange {
        val quest = quests[action.questId] ?: return QuestChange.Blocked(ActionRequirement.QuestUnavailable)
        val round = state.questRounds[quest.id]
        if (round?.number != action.round || round.stage != QuestStage.IN_PROGRESS ||
            round.objectiveId != action.objectiveId
        ) return QuestChange.Blocked(ActionRequirement.QuestRoundChanged)
        val objective = quest.objectives.first { it.id == round.objectiveId }
        if (!at(state, objectiveLocation(objective))) return QuestChange.Blocked(ActionRequirement.QuestLocation)
        if (quest.settleOnObjective) return settle(state, quest, round)
        return change(state, quest, round.copy(stage = QuestStage.READY))
    }

    fun turnIn(state: GameState, action: GameAction.TurnInQuest): QuestChange {
        val quest = quests[action.questId] ?: return QuestChange.Blocked(ActionRequirement.QuestUnavailable)
        if (!at(state, npcLocation(quest.turnInNpcId))) return QuestChange.Blocked(ActionRequirement.QuestLocation)
        val round = state.questRounds[quest.id]
        if (round?.number != action.round || round.stage != QuestStage.READY) {
            return QuestChange.Blocked(ActionRequirement.QuestRoundChanged)
        }
        return settle(state, quest, round)
    }

    fun settlePreviouslyCompletedObjectives(state: GameState): GameState =
        state.questRounds.entries.fold(state) { current, (id, round) ->
            val quest = quests.getValue(id)
            if (quest.settleOnObjective && round.stage == QuestStage.READY) {
                (settle(current, quest, round) as? QuestChange.Changed)?.state ?: current
            } else current
        }

    private fun settle(state: GameState, quest: QuestDefinition, round: QuestRound): QuestChange {
        val isIntroduction = quest.id == staff?.questId
        if (isIntroduction) training.introductionBlock(state)?.let { return QuestChange.Blocked(it) }
        val reward = quest.reward
        if (state.coins > Int.MAX_VALUE - reward.coins || state.potential > Int.MAX_VALUE - reward.potential ||
            state.experience > Int.MAX_VALUE - reward.experience
        ) return QuestChange.Blocked(ActionRequirement.RewardLimit)
        val inventory = state.inventory.toMutableMap()
        for (grant in reward.items) {
            val held = inventory[grant.itemId] ?: 0
            if (held > Int.MAX_VALUE - grant.quantity) return QuestChange.Blocked(ActionRequirement.InventoryFull)
            inventory[grant.itemId] = held + grant.quantity
        }
        val martialTraining = if (isIntroduction) {
            val learned = state.martialTraining.levels.toMutableMap()
            for (grant in reward.martialArts) {
                learned[grant.martialArtId] = maxOf(learned[grant.martialArtId] ?: 0, grant.level)
            }
            state.martialTraining.copy(levels = learned,
                appliedStaffArtId = state.martialTraining.appliedStaffArtId ?: staff?.appliedArtId)
        } else state.martialTraining
        val paid = state.copy(
            coins = state.coins + reward.coins,
            potential = state.potential + reward.potential,
            experience = state.experience + reward.experience,
            inventory = inventory,
            martialTraining = martialTraining,
        )
        return change(paid, quest, round.copy(stage = QuestStage.TURNED_IN))
    }

    private fun acceptanceBlock(state: GameState, quest: QuestDefinition, round: QuestRound?): ActionRequirement? {
        if (round != null && round.stage != QuestStage.TURNED_IN) return ActionRequirement.QuestAlreadyActive
        val limit = quest.repeatBelowExperience
        return when {
            limit == null && round != null -> ActionRequirement.QuestAlreadyTurnedIn
            limit != null && state.experience >= limit -> ActionRequirement.HouseworkRetired(limit)
            round?.number == Int.MAX_VALUE -> ActionRequirement.QuestUnavailable
            quest.id == staff?.questId -> training.introductionBlock(state)
            else -> null
        }
    }

    private fun change(state: GameState, quest: QuestDefinition, round: QuestRound): QuestChange.Changed =
        QuestChange.Changed(state.copy(questRounds = state.questRounds + (quest.id to round)))

    private fun objectiveLocation(objective: QuestObjective) = objective.npcId?.let(::npcLocation) ?: objective.locationId!!

    private fun npcLocation(id: String) = npcs.getValue(id).locationId

    private fun npcPlace(id: String) = locationPlace(npcLocation(id), npcs.getValue(id).name)

    private fun locationPlace(id: String, name: String? = null): String {
        val location = locations.getValue(id)
        return "${scenes.getValue(location.sceneId).name} · ${name ?: location.name}"
    }

    private fun at(state: GameState, locationId: String): Boolean {
        val location = locations.getValue(locationId)
        return state.position.sceneId == location.sceneId && state.position.tile == location.tile
    }
}

internal sealed interface QuestChange {
    data class Changed(val state: GameState) : QuestChange
    data class Blocked(val requirement: ActionRequirement) : QuestChange
}
