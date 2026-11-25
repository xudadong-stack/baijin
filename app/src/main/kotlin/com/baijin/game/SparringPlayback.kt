package com.baijin.game

import com.baijin.game.core.SparringFighter
import com.baijin.game.core.SparringRound
import com.baijin.game.core.SparringSide
import com.baijin.game.core.SparringStrike

internal data class SparringReadings(val player: SparringFighter, val opponent: SparringFighter)

internal data class SparringLogLine(val text: String, val moveName: String? = null)

internal fun SparringRound.logLines(): List<SparringLogLine> = buildList {
    strikes.forEach { strike ->
        strike.openingLine()?.let(::add)
        add(SparringLogLine(strike.text))
    }
    resultText?.let { add(SparringLogLine(it)) }
}

private fun SparringStrike.openingLine() = opening?.let { SparringLogLine(it.text, it.name) }

internal data class SparringCue(
    val lines: List<SparringLogLine>,
    val readings: SparringReadings,
    val delayAfterMillis: Long,
)

internal fun SparringRound.playback(before: SparringReadings): List<SparringCue> = buildList {
    var readings = before
    strikes.forEachIndexed { index, strike ->
        val playerAttacks = strike.attacker == SparringSide.PLAYER
        strike.opening?.let { opening ->
            readings = if (playerAttacks) {
                readings.copy(player = readings.player.copy(innerPower = opening.innerPower))
            } else {
                readings.copy(opponent = readings.opponent.copy(innerPower = opening.innerPower))
            }
            add(SparringCue(listOf(checkNotNull(strike.openingLine())), readings, 300))
        }

        val defender = if (playerAttacks) readings.opponent else readings.player
        val after = defender.copy(
            health = strike.defenderHealth,
            effectiveMaxHealth = strike.defenderEffectiveMaxHealth ?: defender.effectiveMaxHealth,
        )
        readings = if (playerAttacks) readings.copy(opponent = after) else readings.copy(player = after)
        val last = index == strikes.lastIndex
        val lines = listOfNotNull(SparringLogLine(strike.text), resultText?.takeIf { last }?.let(::SparringLogLine))
        val delay = when {
            !last -> 1_000L
            resultText != null -> 750L
            else -> 0L
        }
        add(SparringCue(lines, readings, delay))
    }
}
