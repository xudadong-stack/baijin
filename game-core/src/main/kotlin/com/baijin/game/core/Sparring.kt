package com.baijin.game.core

import com.baijin.game.core.content.SparringDefenseDefinition
import kotlin.random.Random
import kotlin.random.nextInt
import kotlinx.serialization.Serializable

sealed interface SparringStart {
    data class Started(val state: SparringState) : SparringStart
    data class InvalidExploration(val requirement: ActionRequirement) : SparringStart
    data object Unavailable : SparringStart
    data object WrongLocation : SparringStart
    data object NoHealth : SparringStart
}

@Serializable
data class SparringFighter(
    val strength: Int,
    val agility: Int,
    val luck: Int,
    val health: Int,
    val maxHealth: Int,
    val effectiveMaxHealth: Int,
    val innerPower: Int,
    val maxInnerPower: Int,
    val attack: UnarmedAttack,
    val defense: SparringDefenseDefinition,
    val attackLines: List<String> = emptyList(),
)

enum class SparringOutcome { IN_PROGRESS, VICTORY, DEFEAT }

@Serializable
enum class SparringSide { PLAYER, OPPONENT }

@Serializable
enum class StrikeOutcome { DODGED, PARRIED, BLOCKED_BY_PROTECTION, HIT, CRITICAL }

/** An optional opening shown before the saved strike result. */
@Serializable
data class SparringOpening(val name: String, val text: String, val innerPower: Int)

@Serializable
data class SparringStrike(
    val attacker: SparringSide,
    val outcome: StrikeOutcome,
    val damage: Int,
    val defenderHealth: Int,
    val text: String,
    val opening: SparringOpening? = null,
    val defenderEffectiveMaxHealth: Int? = null,
)

@Serializable
data class SparringRound(
    val number: Int,
    val strikes: List<SparringStrike>,
    val resultText: String? = null,
)

@Serializable
data class SparringState(
    val encounterId: String,
    val opponentId: String,
    val opponentName: String,
    val explorationAtStart: GameState? = null,
    val player: SparringFighter,
    val opponent: SparringFighter,
    val damageVariance: Int,
    val nextRoundSeed: Long,
    val rounds: List<SparringRound> = emptyList(),
) {
    val outcome: SparringOutcome
        get() = when {
            opponent.health == 0 -> SparringOutcome.VICTORY
            player.health == 0 -> SparringOutcome.DEFEAT
            else -> SparringOutcome.IN_PROGRESS
        }

    /** A stale round number or finished bout cannot consume another action. */
    fun attack(roundNumber: Int): SparringState? {
        if (outcome != SparringOutcome.IN_PROGRESS || roundNumber != rounds.size + 1) return null
        val random = Random(nextRoundSeed)
        // The next seed is part of the same state update as the completed round.
        return settleRound(random.nextLong()) { random.nextInt(it) }
    }

    internal fun settleRound(nextSeed: Long, draw: (IntRange) -> Int): SparringState {
        val roundNumber = rounds.size + 1
        val first = strike(SparringSide.PLAYER, player, opponent, roundNumber, draw)
        val reply = if (first.defenderHealth > 0) {
            strike(SparringSide.OPPONENT, opponent, player, roundNumber, draw)
        } else {
            null
        }
        val resultText = when {
            first.defenderHealth == 0 -> "${opponentName}气血耗尽，你赢下这场切磋。"
            reply?.defenderHealth == 0 -> "你气血耗尽，${opponentName}赢下这场切磋。"
            else -> null
        }
        return copy(
            player = player.copy(health = reply?.defenderHealth ?: player.health),
            opponent = opponent.copy(health = first.defenderHealth),
            nextRoundSeed = nextSeed,
            rounds = rounds + SparringRound(roundNumber, listOfNotNull(first, reply), resultText),
        )
    }

    private fun strike(
        side: SparringSide,
        attacker: SparringFighter,
        defender: SparringFighter,
        roundNumber: Int,
        draw: (IntRange) -> Int,
    ): SparringStrike {
        val (who, target) = if (side == SparringSide.PLAYER) "你" to opponentName else opponentName to "你"
        val lines = attacker.attackLines.ifEmpty { plainUnarmedLines }
        val action = who + lines[(roundNumber - 1) % lines.size]
        fun result(outcome: StrikeOutcome, damage: Int, text: String) = SparringStrike(
            attacker = side,
            outcome = outcome,
            damage = damage,
            defenderHealth = (defender.health - damage).coerceAtLeast(0),
            text = "$action。$text",
        )

        val hitPercent = (75 + 3L * (attacker.agility.toLong() - defender.agility) +
            attacker.attack.hitBonus - defender.defense.evasionBonus).coerceIn(25, 95).toInt()
        if (draw(0..99) >= hitPercent) {
            return result(StrikeOutcome.DODGED, 0, "${target}侧身避过，未受伤（0 点伤害）。")
        }
        if (draw(0..99) < defender.defense.baseParryPercent.coerceIn(0, 50)) {
            return result(StrikeOutcome.PARRIED, 0, "${target}抬手招架，挡住来招（0 点伤害）。")
        }
        val variance = if (damageVariance == 0) 0 else draw(-damageVariance..damageVariance)
        val ordinary = (attacker.attack.power.toLong() + variance - defender.defense.protection).coerceAtLeast(0)
        if (ordinary == 0L) {
            return result(StrikeOutcome.BLOCKED_BY_PROTECTION, 0, "${target}的防护挡尽来招（0 点伤害）。")
        }
        val nominalCritical = 500 + 50L * (attacker.strength.toLong() - 7) + attacker.attack.criticalBonusBasisPoints
        val criticalThreshold = (nominalCritical * attacker.luck / 50).coerceIn(0, 1500).toInt()
        val critical = draw(0..9999) < criticalThreshold
        val scaledDamage = if (critical) (ordinary * 3 + 1) / 2 else ordinary
        val damage = scaledDamage.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return if (critical) {
            result(StrikeOutcome.CRITICAL, damage, "${target}中招，受到 $damage 点暴击伤害。")
        } else {
            result(StrikeOutcome.HIT, damage, "${target}中招，受到 $damage 点伤害。")
        }
    }
}

private val plainUnarmedLines = listOf(
    "踏前半步，递出一拳",
    "收肘护身，挥拳打出",
    "脚下站稳，转身出拳",
)
