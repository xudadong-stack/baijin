package com.baijin.game.core

import com.baijin.game.core.content.AffinityBand
import com.baijin.game.core.content.ContentLoadResult
import com.baijin.game.core.content.ContentLoader
import com.baijin.game.core.content.NpcService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StarterQuestsTest {
    private val content = (ContentLoader.load(checkNotNull(javaClass.getResource("/content/sample.json"))
        .readText(Charsets.UTF_8)) as ContentLoadResult.Loaded).content
    private val engine = GameEngine(content) { 44 }
    private val home = "granny_service"
    private val master = "town_training_shed"
    private val partner = "town_training_east"
    private val housework = "granny_housework"
    private val talkToGranny = GameAction.TalkToNpc("sample_granny", seed = 0)
    private val message = "training_message"
    private val confirm = GameAction.CompleteQuestObjective(message, "confirm_arrangement", 1)

    @Test fun allThreeChoresWorkWithEmptyPurseAndBothMetersFull() {
        val initial = at(home).copy(coins = 0, hunger = content.time.maxHunger, thirst = content.time.maxThirst)
        for ((objective, location) in mapOf(
            "sweep" to "town_granny_yard", "fetch_water" to "town_well", "chop_wood" to "town_firewood",
        )) {
            val accepted = change(initial, talkFor(objective))
            assertEquals(QuestRound(1, objective, QuestStage.IN_PROGRESS), accepted.questRounds[housework])
            val action = GameAction.CompleteQuestObjective(housework, objective, 1)
            assertEquals(ActionResult.Blocked(ActionRequirement.QuestLocation), engine.dispatch(accepted, action))
            val atWork = at(location, accepted)
            if (objective == "fetch_water") {
                assertEquals(QuestStage.IN_PROGRESS, change(atWork, GameAction.Drink).questRounds[housework]?.stage)
                assertEquals(ActionResult.Blocked(ActionRequirement.QuestLocation),
                    engine.dispatch(at("town_upper_well", accepted), action))
            }
            val finished = change(atWork, action)
            assertEquals(atWork.thirst, finished.thirst)
            assertEquals(atWork.gameTime, finished.gameTime)
            val view = inspect(finished).quests.first { it.quest.id == housework }
            assertEquals(QuestStage.TURNED_IN, view.round?.stage)
            assertEquals(listOf(50, 10, 20), listOf(finished.coins, finished.potential, finished.experience))
            assertEquals(ActionResult.Blocked(ActionRequirement.QuestRoundChanged), engine.dispatch(finished, action))
            assertEquals(ActionResult.Blocked(ActionRequirement.QuestRoundChanged),
                engine.dispatch(at(home, finished), GameAction.TurnInQuest(housework, 1)))
        }
    }

    @Test fun repeatedRoundsAndMessageRemainIndependentAndRejectStaleActions() {
        val start = at(master).copy(coins = 0)
        val acceptMessage = GameAction.AcceptQuest(message, confirm.objectiveId, 0)
        assertFalse(inspect(at(partner, start)).quests.first { it.quest.id == message }.canCompleteHere)
        assertEquals(ActionResult.Blocked(ActionRequirement.QuestRoundChanged), engine.dispatch(at(partner, start), confirm))
        assertEquals(ActionResult.Blocked(ActionRequirement.QuestLocation), engine.dispatch(start, talkToGranny))
        assertEquals(ActionResult.Blocked(ActionRequirement.QuestUnavailable),
            engine.dispatch(at(home, start), GameAction.AcceptQuest(housework, "sweep", 0)))
        var state = change(start, acceptMessage)
        assertTrue(inspect(at(partner, state)).quests.first { it.quest.id == message }.canCompleteHere)
        for (previous in 0..1) {
            state = change(at(home, state), talkFor("sweep"))
            val restored = engine.restoreSavedGame(state) as ActionResult.Display
            val repeated = engine.dispatch(restored.state, talkFor("chop_wood")) as ActionResult.Display
            assertEquals(state, repeated.state)
            assertEquals(QuestRound(previous + 1, "sweep", QuestStage.IN_PROGRESS), state.questRounds[housework])
            val atWork = at("town_granny_yard", state)
            if (previous == 1) assertEquals(ActionResult.Blocked(ActionRequirement.QuestRoundChanged),
                engine.dispatch(atWork, GameAction.CompleteQuestObjective(housework, "sweep", 1)))
            state = change(atWork, GameAction.CompleteQuestObjective(housework, "sweep", previous + 1))
            assertEquals(QuestStage.IN_PROGRESS, state.questRounds[message]?.stage)
        }
        assertEquals(listOf(100, 20, 40), listOf(state.coins, state.potential, state.experience))
        state = change(at(home, state), GameAction.BuyFood(content.supplies.sellerNpcId))
        assertEquals(96, state.coins)
        state = change(at(partner, state), confirm)
        state = change(at(master, state), GameAction.TurnInQuest(message, 1))
        assertEquals(46, state.potential)
        assertEquals(ActionResult.Blocked(ActionRequirement.QuestAlreadyTurnedIn),
            engine.dispatch(state, acceptMessage.copy(previousRound = 1)))
    }

    @Test fun onlyNewHouseworkIsBlockedAtTheExperienceBoundary() {
        val retired = at(home).copy(experience = 5000)
        assertEquals(retired, (engine.dispatch(retired, talkToGranny) as ActionResult.Display).state)
        assertEquals(ActionRequirement.HouseworkRetired(5000), inspect(retired).quests.first { it.quest.id == housework }.acceptanceBlock)
        assertTrue(NpcService.FOOD in inspect(retired).dialogue!!.npc.services)
        for ((before, during, after) in listOf(Triple(4999, 4999, 5019), Triple(4980, 4980, 5000),
            Triple(4999, 5000, 5020))) {
            val accepted = change(retired.copy(experience = before), talkFor("sweep"))
            val paid = change(at("town_granny_yard", accepted.copy(experience = during)),
                GameAction.CompleteQuestObjective(housework, "sweep", 1))
            assertEquals(after, paid.experience)
            val returned = at(home, paid)
            assertEquals(returned, (engine.dispatch(returned, talkToGranny) as ActionResult.Display).state)
        }
    }

    private fun talkFor(objectiveId: String): GameAction.TalkToNpc {
        val objectives = content.quests.first { it.id == housework }.objectives
        val seed = (0L..100L).first { objectives.random(Random(it)).id == objectiveId }
        return talkToGranny.copy(seed = seed)
    }

    @Test fun npcDialogueUsesSavedAffinityAndAppearanceAndKeepsBusinessEntries() {
        val states = mapOf("sample_granny" to home, "town_seamstress" to "town_mending_stall",
            "town_physician" to "town_medicine_stall", "sample_master" to master)
        for ((id, location) in states) {
            val base = at(location)
            for ((value, band) in mapOf(-20 to AffinityBand.WARY, -19 to AffinityBand.NORMAL,
                19 to AffinityBand.NORMAL, 20 to AffinityBand.CLOSE)) {
                val state = base.copy(affinities = mapOf(id to value))
                val original = inspect(state).dialogue!!
                assertEquals(band, original.affinity)
                assertEquals(4, original.topics.size)
                assertEquals(state, (engine.dispatch(state, GameAction.InspectLocation) as ActionResult.Display).state)
                if (id == "sample_master") assertTrue(NpcService.SECT in original.npc.services)
                if (id != "sample_master") assertEquals(AffinityBand.NORMAL, inspect(at(master, state)).dialogue!!.affinity)
                val plain = inspect(state.copy(character = state.character.copy(appearanceTier = 1))).dialogue!!
                assertEquals(if (id == "sample_granny" && band == AffinityBand.NORMAL) 3 else 4, plain.topics.size)
                assertEquals(original.npc.services, plain.npc.services)
            }
        }
        val granny = inspect(at(home)).dialogue!!
        for ((value, band) in mapOf(-20 to MoralityBand.SELF_SERVING, -19 to MoralityBand.NEUTRAL,
            19 to MoralityBand.NEUTRAL, 20 to MoralityBand.BENEVOLENT)) {
            assertEquals(band, granny.copy(npc = granny.npc.copy(morality = value)).morality)
        }
    }

    private fun inspect(state: GameState) = (engine.dispatch(state, GameAction.InspectLocation) as ActionResult.Display).exploration

    private fun at(locationId: String, state: GameState? = null): GameState {
        val place = content.locations.first { it.id == locationId }
        val start = state ?: (engine.dispatch(null, GameAction.Start(Gender.FEMALE, engine.creation.balancedAttributes))
            as ActionResult.Changed).state
        return start.copy(position = WorldPosition(place.sceneId, place.tile))
    }

    private fun change(state: GameState, action: GameAction) = (engine.dispatch(state, action) as ActionResult.Changed).state
}
