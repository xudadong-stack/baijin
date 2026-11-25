package com.baijin.game

import com.baijin.game.core.ActionRequirement
import com.baijin.game.core.ActionResult
import com.baijin.game.core.GameAction
import com.baijin.game.core.GameEngine
import com.baijin.game.core.GameState
import com.baijin.game.core.Gender
import com.baijin.game.core.QuestRound
import com.baijin.game.core.QuestStage
import com.baijin.game.core.SparringOpening
import com.baijin.game.core.SparringResult
import com.baijin.game.core.WorldPosition
import com.baijin.game.core.content.ContentLoadResult
import com.baijin.game.core.content.ContentLoader
import com.baijin.game.core.content.SparringQuestLoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SparringPersistenceTest {
    private val content = (ContentLoader.load(
        javaClass.getResource("/content/sample.json")!!.readText(),
    ) as ContentLoadResult.Loaded).content
    private val definition = checkNotNull(content.openingSparring)
    private val engine = GameEngine(content) { 50 }
    private val opponent = content.npcs.single { it.id == definition.npcId }
    private val location = content.locations.single { it.id == opponent.locationId }
    private val before = (engine.dispatch(null, GameAction.Start(Gender.FEMALE, engine.creation.balancedAttributes))
        as ActionResult.Changed).state.let { start ->
        start.copy(
            position = WorldPosition(location.sceneId, location.tile),
            gameTime = 23,
            hunger = 10,
            thirst = 10,
            inventory = mapOf("ration" to 2),
            potential = 17,
            experience = 8,
            questRounds = mapOf("training_message" to QuestRound(1, "confirm_arrangement", QuestStage.READY)),
            character = start.character.copy(effectiveMaxHealth = start.character.maxHealth - 4,
                health = start.character.maxHealth - 4, innerPower = 7),
        )
    }

    @Test
    fun resumedRunMatchesContinuousRunAndRejectsStaleOrExplorationActions() {
        val saved = restored(begin(before, 853))
        val firstRound = attack(saved)
        val resumed = restored(firstRound)
        val battle = checkNotNull(resumed.sparring)
        assertEquals(1, battle.fight.rounds.size)
        assertEquals(before, battle.fight.explorationAtStart)
        assertEquals(firstRound.copy(completedSparringRound = null), resumed)
        assertEquals(attack(firstRound), attack(resumed))
        assertEquals(terminal(firstRound), terminal(resumed))
        assertEquals(before.gameTime, resumed.state.gameTime)
        assertEquals(before.hunger, resumed.state.hunger)
        assertEquals(before.thirst, resumed.state.thirst)
        assertEquals(ActionResult.Blocked(ActionRequirement.SparringChanged),
            engine.dispatch(resumed.state, GameAction.AttackSparring(battle.boutId, 1), battle))
        assertEquals(ActionResult.Blocked(ActionRequirement.SparringChanged),
            engine.dispatch(resumed.state, GameAction.Meditate, battle))
    }

    @Test
    fun contentVersionTwoBattleKeepsItsRoundAndPreBattleSave() {
        val round = attack(begin(before, 853))
        val battle = checkNotNull(round.sparring)
        val original = SaveSnapshot.encode(round.state.copy(contentVersion = 2),
            battle.copy(fight = battle.fight.copy(explorationAtStart = before.copy(contentVersion = 2))))

        val resumed = SaveSnapshot.restore(original, engine) as ActionResult.Display
        assertEquals(round.state, resumed.state)
        assertEquals(battle, resumed.sparring)
    }

    @Test
    fun specialOpeningUpdatesOnlyInnerPowerUntilTheResultAndFullLogSurvivesResume() {
        val started = begin(before, 853)
        val initial = checkNotNull(started.sparring).fight
        val completed = attack(started)
        val battle = checkNotNull(completed.sparring)
        val originalRound = checkNotNull(completed.completedSparringRound)
        val innerPower = initial.player.innerPower - 4
        val effectiveLimit = initial.opponent.maxHealth - 3
        val firstHit = originalRound.strikes.first().copy(
            opening = SparringOpening("压锋斩", "你沉肩压刃，刀锋直落。", innerPower),
            defenderEffectiveMaxHealth = effectiveLimit,
        )
        val sampleRound = originalRound.copy(strikes = listOf(firstHit) + originalRound.strikes.drop(1))
        val readingsBefore = SparringReadings(initial.player, initial.opponent)
        val cues = sampleRound.playback(readingsBefore)
        assertEquals(listOf(300L, 1_000L, 0L), cues.map { it.delayAfterMillis })
        assertEquals(listOf(SparringLogLine("你沉肩压刃，刀锋直落。", "压锋斩")), cues[0].lines)
        assertEquals(readingsBefore.copy(player = initial.player.copy(innerPower = innerPower)), cues[0].readings)
        assertEquals(firstHit.defenderHealth, cues[1].readings.opponent.health)
        assertEquals(effectiveLimit, cues[1].readings.opponent.effectiveMaxHealth)
        assertEquals(initial.player.health, cues[1].readings.player.health)
        assertEquals(originalRound.strikes.last().defenderHealth, cues.last().readings.player.health)

        val finalReadings = cues.last().readings
        val snapshot = completed.copy(
            state = completed.state.copy(character = completed.state.character.copy(innerPower = innerPower)),
            sparring = battle.copy(fight = battle.fight.copy(
                player = finalReadings.player, opponent = finalReadings.opponent, rounds = listOf(sampleRound),
            )),
        )
        val resumed = checkNotNull(restored(snapshot).sparring).fight
        assertEquals(cues.flatMap { it.lines }, resumed.rounds.single().logLines())
        assertEquals(finalReadings, SparringReadings(resumed.player, resumed.opponent))
    }

    @Test
    fun emptyAndMultiItemVictoriesRemainFixedAndConfirmOncePerBout() {
        for (expected in listOf(emptyMap(), mapOf("sample_light_wood_staff" to 1, "cloth_cap" to 1))) {
            val win = findWin(expected)
            val pending = restored(win)
            val battle = checkNotNull(pending.sparring)
            assertNull(battle.fight.explorationAtStart)
            assertEquals(SparringResult.Victory(expected), battle.result)
            assertEquals(before.inventory, pending.state.inventory)
            val confirm = GameAction.ClaimSparringLoot(battle.boutId)
            val confirmed = restored(engine.dispatch(pending.state, confirm, battle) as ActionResult.Changed)
            assertEquals(before.inventory + expected, confirmed.state.inventory)
            assertEquals(before.potential, confirmed.state.potential)
            assertEquals(before.experience, confirmed.state.experience)
            assertNull(confirmed.sparring)
            assertEquals(battle.boutId, confirmed.state.lastSparring?.boutId)
            assertEquals(ActionResult.Blocked(ActionRequirement.SparringChanged), engine.dispatch(confirmed.state, confirm))

            val again = findWin(expected, source = confirmed.state.copy(character = before.character))
            val secondBattle = checkNotNull(again.sparring)
            assertNotEquals(battle.boutId, secondBattle.boutId)
            assertEquals(ActionResult.Blocked(ActionRequirement.SparringChanged),
                engine.dispatch(again.state, confirm, secondBattle))
            val twice = engine.dispatch(again.state, GameAction.ClaimSparringLoot(secondBattle.boutId), secondBattle)
                as ActionResult.Changed
            assertEquals(before.inventory + expected.mapValues { (_, quantity) -> 2 * quantity }, twice.state.inventory)
        }
    }

    @Test
    fun requiredDropsJoinEmptyOrOrdinaryLootAndUseTheSameConfirmation() {
        val staff = "sample_light_wood_staff"
        val questDrop = SparringQuestLoot("training_message", QuestStage.READY,
            mapOf("ration" to 3, staff to 1), "confirm_arrangement")
        val fixtureEngine = GameEngine(content.copy(openingSparring = definition.copy(requiredLoot = listOf(questDrop)))) { 50 }
        for (ordinary in listOf(emptyMap(), mapOf(staff to 1, "cloth_cap" to 1))) {
            val expected = ordinary + ("ration" to 3) + (staff to (ordinary.getOrDefault(staff, 0) + 1))
            val result = restored(findWin(expected, fixtureEngine), fixtureEngine)
            val battle = checkNotNull(result.sparring)
            assertEquals(SparringResult.Victory(expected), battle.result)
            val changed = fixtureEngine.dispatch(result.state, GameAction.ClaimSparringLoot(battle.boutId), battle)
                as ActionResult.Changed
            assertEquals(expected + ("ration" to 5), changed.state.inventory)
            assertEquals(before.questRounds, changed.state.questRounds)
        }
        val source = before.copy(questRounds = emptyMap())
        val withoutQuest = findWin(emptyMap(), fixtureEngine, source)
        assertEquals(SparringResult.Victory(emptyMap()), withoutQuest.sparring!!.result)
    }

    @Test
    fun deathAndMercyRespectBothInputsAtTheBoundary() {
        for ((morality, affinity) in listOf(-60 to 59, -60 to 60, 59 to -60, 60 to -60, 0 to 0, 30 to 0)) {
            val fixtureEngine = GameEngine(content.copy(npcs = content.npcs.map {
                if (it.id == opponent.id) it.copy(morality = morality) else it
            })) { 50 }
            val start = before.copy(character = before.character.copy(health = 1),
                affinities = mapOf(opponent.id to affinity))
            val result = terminal(begin(start, 853, fixtureEngine), fixtureEngine)
            assertTrue(checkNotNull(result.completedSparringRound).resultText!!.contains("气血耗尽"))
            val recovered = restored(result, fixtureEngine)
            if (morality + affinity < 0) {
                val battle = checkNotNull(recovered.sparring)
                assertEquals(SparringResult.Death, battle.result)
                assertEquals(0, recovered.state.character.health)
                val action = GameAction.ReadBeforeSparring(battle.boutId)
                val readBefore = restored(
                    fixtureEngine.dispatch(recovered.state, action, battle) as ActionResult.Changed, fixtureEngine,
                )
                assertEquals(start, readBefore.state)
                assertNull(readBefore.sparring)
                assertTrue(fixtureEngine.dispatch(readBefore.state, GameAction.Meditate) is ActionResult.Changed)
            } else {
                assertNull(recovered.sparring)
                assertEquals(start, recovered.state.copy(lastSparring = null))
                assertTrue(recovered.state.lastSparring!!.sparedNotice!!.contains("放过"))
                assertTrue(fixtureEngine.dispatch(recovered.state, GameAction.Meditate) is ActionResult.Changed)
            }
        }
    }

    @Test
    fun currentSaveRejectsAnInconsistentBattleAndVersionSevenRemainsPlayable() {
        val initial = begin(before, 853)
        val battle = checkNotNull(initial.sparring)
        val encoded = SaveSnapshot.encode(initial.state, battle.copy(result = SparringResult.Death))
        assertEquals(ActionResult.Blocked(ActionRequirement.InvalidSparring), SaveSnapshot.restore(encoded, engine))
        val legacy = SaveSnapshot.encode(before).replace("\"schemaVersion\":9", "\"schemaVersion\":7")
        val loaded = SaveSnapshot.restore(legacy, engine) as ActionResult.Display
        assertEquals(before, loaded.state)
        assertNull(loaded.sparring)
        assertTrue(engine.dispatch(loaded.state, GameAction.BeginSparring(opponent.id, 853)) is ActionResult.Changed)
    }

    private fun begin(state: GameState, seed: Long, rules: GameEngine = engine): ActionResult.Changed =
        rules.dispatch(state, GameAction.BeginSparring(opponent.id, seed)) as ActionResult.Changed

    private fun attack(saved: ActionResult.Changed, rules: GameEngine = engine): ActionResult.Changed {
        val battle = checkNotNull(saved.sparring)
        val action = GameAction.AttackSparring(battle.boutId, battle.fight.rounds.size + 1)
        return rules.dispatch(saved.state, action, battle) as ActionResult.Changed
    }

    private fun terminal(start: ActionResult.Changed, rules: GameEngine = engine): ActionResult.Changed {
        var saved = start
        repeat(50) {
            if (saved.sparring == null || saved.sparring?.result != null) return saved
            saved = attack(saved, rules)
        }
        error("Expected the sparring bout to finish within 50 rounds")
    }

    private fun findWin(
        loot: Map<String, Int>,
        rules: GameEngine = engine,
        source: GameState = before,
    ): ActionResult.Changed = (0L..199L).asSequence().map { terminal(begin(source, it, rules), rules) }
        .first { it.sparring?.result == SparringResult.Victory(loot) }

    private fun restored(source: ActionResult.Changed, rules: GameEngine = engine): ActionResult.Changed {
        val read = SaveSnapshot.restore(SaveSnapshot.encode(source.state, source.sparring), rules) as ActionResult.Display
        return ActionResult.Changed(read.state, read.exploration, read.sparring)
    }
}
