package com.baijin.game.core

import com.baijin.game.core.content.OpeningSparringDefinition
import kotlinx.serialization.Serializable

@Serializable
data class SavedSparring(val boutId: String, val fight: SparringState, val result: SparringResult? = null)

@Serializable
sealed interface SparringResult {
    @Serializable
    data class Victory(val loot: Map<String, Int>) : SparringResult

    @Serializable
    data object Death : SparringResult
}

@Serializable
data class SparringReceipt(val boutId: String, val sparedNotice: String? = null)

internal fun OpeningSparringDefinition.drawLoot(
    state: GameState,
    draw: (Long) -> Long,
): Map<String, Int>? {
    var roll = draw(lootGroups.sumOf { it.weight.toLong() })
    val drawn = lootGroups.first { group ->
        roll -= group.weight
        roll < 0
    }
    val loot = drawn.items.toMutableMap()
    for (drop in requiredLoot) {
        val round = state.questRounds[drop.questId] ?: continue
        if (round.stage != drop.stage || (drop.objectiveId != null && round.objectiveId != drop.objectiveId)) continue
        for ((id, quantity) in drop.items) {
            val added = (loot[id] ?: 0).toLong() + quantity
            if (added > Int.MAX_VALUE) return null
            loot[id] = added.toInt()
        }
    }
    return loot
}

internal fun GameState.afterRound(fighter: SparringFighter, health: Int = fighter.health): GameState =
    copy(character = character.copy(
        health = health,
        effectiveMaxHealth = fighter.effectiveMaxHealth,
        innerPower = fighter.innerPower,
    ))
