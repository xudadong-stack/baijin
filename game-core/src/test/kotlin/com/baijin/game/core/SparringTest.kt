package com.baijin.game.core

import com.baijin.game.core.content.loadBundledSample
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SparringTest {
    private val content = loadBundledSample()
    private val engine = GameEngine(content) { 44 }
    private val partner = "town_sparring_partner"
    private val master = "sample_master"

    @Test
    fun untrainedCharacterCanStartAtThePartnerWithCurrentVitals() {
        val started = newCharacter()
        assertEquals(SparringStart.InvalidExploration(ActionRequirement.StartRequired),
            engine.startSparring(null, partner))
        assertEquals(SparringStart.WrongLocation, engine.startSparring(started, partner))
        val atPartner = atNpc(started, partner)
        assertEquals(SparringStart.Unavailable, engine.startSparring(atPartner, master))
        assertEquals(SparringStart.NoHealth, engine.startSparring(
            atPartner.copy(character = atPartner.character.copy(health = 0)), partner))

        val character = atPartner.character.copy(health = 37, effectiveMaxHealth = 45, innerPower = 4)
        val exploration = atPartner.copy(character = character)
        val state = start(exploration)
        assertEquals("opening_sparring", state.encounterId)
        assertEquals(partner, state.opponentId)
        assertEquals("校场陪练", state.opponentName)
        assertEquals(exploration, state.explorationAtStart)
        assertEquals(37, state.player.health)
        assertEquals(45, state.player.effectiveMaxHealth)
        assertEquals(character.maxHealth, state.player.maxHealth)
        assertEquals(4, state.player.innerPower)
        assertEquals(character.maxInnerPower, state.player.maxInnerPower)
        assertEquals(45, state.opponent.health)
        assertEquals(18, state.opponent.innerPower)
        assertEquals(50, state.opponent.luck)
        assertEquals(UnarmedAttack(11, 0, 0), state.player.attack)
        assertEquals(state.player.attack, state.opponent.attack)
        assertTrue(state.rounds.isEmpty())
    }

    @Test
    fun hitBoundariesAndEachDefendersOwnParryRateControlTheDrawOrder() {
        val state = start(atNpc(newCharacter(Attributes(7, 10, 4, 7)), partner))
        // The player's hit threshold is 84, the opponent's is 66.
        val first = scripted(state, 0..99 to 83, 0..99 to 7, 0..99 to 66)
        val strikes = first.rounds.single().strikes
        assertEquals(listOf(SparringSide.PLAYER, SparringSide.OPPONENT), strikes.map { it.attacker })
        assertEquals(listOf(StrikeOutcome.PARRIED, StrikeOutcome.DODGED), strikes.map { it.outcome })
        assertEquals(listOf(0, 0), strikes.map { it.damage })
        assertTrue(strikes[0].text.contains("招架") && strikes[1].text.contains("侧身避过"))

        val second = scripted(first, 0..99 to 84, 0..99 to 65, 0..99 to 7, -1..1 to 0, 0..9999 to 9999)
        val reply = second.rounds.last().strikes
        assertEquals(listOf(StrikeOutcome.DODGED, StrikeOutcome.HIT), reply.map { it.outcome })
        assertEquals(state.player.health - 11, second.player.health)
        assertEquals(state.opponent.health, second.opponent.health)
        assertEquals(SparringOutcome.IN_PROGRESS, second.outcome)

        val third = scripted(second, 0..99 to 99, 0..99 to 99)
        assertEquals(listOf(1, 2, 3), third.rounds.map { it.number })
        assertEquals(listOf(StrikeOutcome.DODGED, StrikeOutcome.DODGED), third.rounds.last().strikes.map { it.outcome })
        assertNull(third.rounds.last().resultText)
    }

    @Test
    fun chanceClampsAndAttackAndDefenseModifiersApplyBeforeRolling() {
        val state = start().let {
            it.copy(
                player = it.player.copy(attack = it.player.attack.copy(hitBonus = 100)),
                opponent = it.opponent.copy(defense = it.opponent.defense.copy(
                    evasionBonus = 12, baseParryPercent = 80)),
            )
        }
        val capped = scripted(state, 0..99 to 94, 0..99 to 49, 0..99 to 99)
        assertEquals(StrikeOutcome.PARRIED, capped.rounds.single().strikes.first().outcome)
        assertEquals(StrikeOutcome.DODGED,
            scripted(state, 0..99 to 95, 0..99 to 99).rounds.single().strikes.first().outcome)
        val floor = state.copy(player = state.player.copy(attack = state.player.attack.copy(hitBonus = -100)))
        assertEquals(StrikeOutcome.PARRIED,
            scripted(floor, 0..99 to 24, 0..99 to 0, 0..99 to 99).rounds.single().strikes.first().outcome)
        assertEquals(StrikeOutcome.DODGED,
            scripted(floor, 0..99 to 25, 0..99 to 99).rounds.single().strikes.first().outcome)
        val modified = state.copy(player = state.player.copy(attack = state.player.attack.copy(hitBonus = 5)))
        assertEquals(StrikeOutcome.PARRIED,
            scripted(modified, 0..99 to 67, 0..99 to 0, 0..99 to 99).rounds.single().strikes.first().outcome)
        assertEquals(StrikeOutcome.DODGED,
            scripted(modified, 0..99 to 68, 0..99 to 99).rounds.single().strikes.first().outcome)
    }

    @Test
    fun varianceIsAppliedBeforeProtectionAndZeroDamageSkipsCritical() {
        val state = start().let {
            it.copy(
                player = it.player.copy(attack = it.player.attack.copy(power = 15)),
                opponent = it.opponent.copy(defense = it.opponent.defense.copy(protection = 15)),
            )
        }
        for (variance in -1..0) {
            val blocked = scripted(state, 0..99 to 0, 0..99 to 8, -1..1 to variance, 0..99 to 99)
            val strike = blocked.rounds.single().strikes.first()
            assertEquals(StrikeOutcome.BLOCKED_BY_PROTECTION, strike.outcome)
            assertEquals(0, strike.damage)
            assertEquals(45, strike.defenderHealth)
            assertTrue(strike.text.contains("防护挡尽"))
        }
        val landed = scripted(state, 0..99 to 0, 0..99 to 8, -1..1 to 1, 0..9999 to 9999, 0..99 to 99)
        assertEquals(1, landed.rounds.single().strikes.first().damage)
        assertEquals(44, landed.opponent.health)
    }

    @Test
    fun criticalUsesLuckInBasisPointsAndRoundsDamageUpAfterProtection() {
        val state = start().let {
            it.copy(
                player = it.player.copy(attack = it.player.attack.copy(power = 15)),
                opponent = it.opponent.copy(defense = it.opponent.defense.copy(protection = 6)),
                damageVariance = 0,
            )
        }
        for ((luck, threshold) in mapOf(1 to 10, 10 to 100, 50 to 500, 90 to 900)) {
            val withLuck = state.copy(player = state.player.copy(luck = luck))
            val critical = firstHit(withLuck, threshold - 1)
            assertEquals(StrikeOutcome.CRITICAL, critical.outcome)
            assertEquals(14, critical.damage)
            assertEquals(31, critical.defenderHealth)
            assertTrue(critical.text.contains("14 点暴击伤害"))
            val ordinary = firstHit(withLuck, threshold)
            assertEquals(StrikeOutcome.HIT, ordinary.outcome)
            assertEquals(9, ordinary.damage)
        }
        val withBonus = state.copy(player = state.player.copy(luck = 90,
            attack = state.player.attack.copy(criticalBonusBasisPoints = 500)))
        assertEquals(StrikeOutcome.CRITICAL, firstHit(withBonus, 1499).outcome)
        assertEquals(StrikeOutcome.HIT, firstHit(withBonus, 1500).outcome)
        val negative = state.copy(player = state.player.copy(
            attack = state.player.attack.copy(criticalBonusBasisPoints = -501)))
        assertEquals(StrikeOutcome.HIT, firstHit(negative, 0).outcome)
        val fractional = state.copy(player = state.player.copy(luck = 1,
            attack = state.player.attack.copy(criticalBonusBasisPoints = -1)))
        assertEquals(StrikeOutcome.CRITICAL, firstHit(fractional, 8).outcome)
        assertEquals(StrikeOutcome.HIT, firstHit(fractional, 9).outcome)
    }

    @Test
    fun bothSidesShareDamageAndCriticalRules() {
        val state = start()
        for ((roll, expected) in listOf(499 to 17, 500 to 11)) {
            val round = scripted(state, 0..99 to 0, 0..99 to 8, -1..1 to 0, 0..9999 to roll,
                0..99 to 0, 0..99 to 5, -1..1 to 0, 0..9999 to roll)
            assertEquals(listOf(expected, expected), round.rounds.single().strikes.map { it.damage })
            assertEquals(state.player.health - expected, round.player.health)
            assertEquals(state.opponent.health - expected, round.opponent.health)
        }
    }

    @Test
    fun lethalPlayerHitOmitsResponseAndLethalResponseFinishesDefeat() {
        val state = start()
        val won = scripted(state.copy(opponent = state.opponent.copy(health = 5)),
            0..99 to 0, 0..99 to 99, -1..1 to 1, 0..9999 to 9999)
        assertEquals(SparringOutcome.VICTORY, won.outcome)
        assertEquals(1, won.rounds.single().strikes.size)
        assertEquals(0, won.opponent.health)
        assertEquals(state.player, won.player)
        assertTrue(won.rounds.single().resultText.orEmpty().contains("你赢下"))
        assertNull(won.attack(2))

        val lost = scripted(state.copy(player = state.player.copy(health = 5)),
            0..99 to 99, 0..99 to 0, 0..99 to 99, -1..1 to 1, 0..9999 to 0)
        assertEquals(SparringOutcome.DEFEAT, lost.outcome)
        assertEquals(2, lost.rounds.single().strikes.size)
        assertEquals(0, lost.player.health)
        assertEquals(state.opponent, lost.opponent)
        assertTrue(lost.rounds.single().resultText.orEmpty().contains("校场陪练赢下"))
        assertNull(lost.attack(2))
    }

    @Test
    fun actualLessonsChangeSparringDamageAndAppliedLinesRotate() {
        val character = newCharacter(Attributes(7, 8, 4, 9)).copy(potential = 38)
        val untrained = start(atNpc(character, partner))
        assertEquals(11, ordinaryDamage(untrained))
        for ((sect, art) in listOf("qingheng" to "shouxian_palm", "duchuan" to "huichao_fist")) {
            val joined = change(atNpc(character, master), GameAction.JoinSect(master, sect))
            val basic = change(joined, GameAction.LearnMartialArt(master, "basic_unarmed", 1))
            assertEquals(13, ordinaryDamage(start(atNpc(basic, partner))))
            val learned = change(basic, GameAction.LearnMartialArt(master, art, 1))
            val applied = start(atNpc(learned, partner))
            assertEquals(16, ordinaryDamage(applied))
            val raisedBasic = change(learned, GameAction.LearnMartialArt(master, "basic_unarmed", 2))
            assertEquals(17, ordinaryDamage(start(atNpc(raisedBasic, partner))))
            val raisedArt = change(raisedBasic, GameAction.LearnMartialArt(master, art, 2))
            val upgraded = start(atNpc(raisedArt, partner))
            assertEquals(UnarmedAttack(18, 0, 50), upgraded.player.attack)
            assertEquals(18, ordinaryDamage(upgraded))

            val lines = content.martialArts.single { it.id == art }.openingAttackLines
            var current = applied
            for (index in listOf(0, 1, 2, 0)) {
                current = scripted(current, 0..99 to 99, 0..99 to 99)
                assertTrue(current.rounds.last().strikes.first().text.startsWith("你" + lines[index] + "。"))
            }
        }
    }

    @Test
    fun committedRoundStateReplaysDeterministicallyAndLeavesExplorationFrozen() {
        val atPartner = atNpc(newCharacter(), partner).let {
            it.copy(gameTime = 11, hunger = 3, thirst = 5,
                character = it.character.copy(health = 35, innerPower = 2))
        }
        val ready = start(atPartner)
        assertNull(ready.attack(2))
        val round = checkNotNull(ready.attack(1))
        assertEquals(round, ready.attack(1))
        assertNotEquals(ready.nextRoundSeed, round.nextRoundSeed)
        assertNull(round.attack(1))
        val restored = Json.decodeFromString<SparringState>(Json.encodeToString(round))
        assertEquals(round, restored)
        val next = checkNotNull(round.attack(2))
        assertEquals(next, restored.attack(2))
        assertEquals(listOf(1, 2), next.rounds.map { it.number })
        assertEquals(atPartner, next.explorationAtStart)
        assertEquals(2, next.player.innerPower)
        assertEquals(18, next.opponent.innerPower)
    }

    @Test
    fun drawsOneWholeLootGroupAtEachWeightBoundary() {
        val definition = checkNotNull(content.openingSparring)
        val state = newCharacter()
        val staff = mapOf("sample_light_wood_staff" to 1)
        val wristguard = mapOf("cloth_wristguard" to 1)
        val staffAndCap = staff + ("cloth_cap" to 1)
        val groups = mapOf(0L to emptyMap(), 44L to emptyMap(), 45L to staff, 74L to staff,
            75L to wristguard, 94L to wristguard, 95L to staffAndCap, 99L to staffAndCap)
        for ((roll, loot) in groups) {
            var draws = 0
            assertEquals(loot, definition.drawLoot(state) { total ->
                assertEquals(100L, total)
                draws++
                roll
            })
            assertEquals(1, draws)
        }
    }

    private fun newCharacter(attributes: Attributes = Attributes(7, 7, 7, 7)): GameState =
        (engine.dispatch(null, GameAction.Start(Gender.FEMALE, attributes)) as ActionResult.Changed).state
            .let { it.copy(character = it.character.copy(luck = 50)) }

    private fun start(state: GameState = atNpc(newCharacter(), partner)): SparringState =
        (engine.startSparring(state, partner, seed = 853L) as SparringStart.Started).state

    private fun atNpc(state: GameState, id: String): GameState {
        val npc = content.npcs.single { it.id == id }
        val location = content.locations.single { it.id == npc.locationId }
        return state.copy(position = WorldPosition(location.sceneId, location.tile))
    }

    private fun change(state: GameState, action: GameAction): GameState =
        (engine.dispatch(state, action) as ActionResult.Changed).state

    private fun firstHit(state: SparringState, criticalRoll: Int): SparringStrike =
        scripted(state, 0..99 to 0, 0..99 to 99, 0..9999 to criticalRoll, 0..99 to 99)
            .rounds.single().strikes.first()

    private fun ordinaryDamage(state: SparringState): Int =
        scripted(state, 0..99 to 0, 0..99 to 99, -1..1 to 0, 0..9999 to 9999, 0..99 to 99)
            .rounds.single().strikes.first().damage

    private fun scripted(state: SparringState, vararg rolls: Pair<IntRange, Int>): SparringState {
        val pending = ArrayDeque(rolls.toList())
        val result = state.settleRound(state.nextRoundSeed + 1) { range ->
            val (expected, value) = checkNotNull(pending.removeFirstOrNull()) { "Unexpected draw from $range" }
            assertEquals(expected, range)
            assertTrue(value in range)
            value
        }
        assertTrue("Unused draws: $pending", pending.isEmpty())
        return result
    }
}
