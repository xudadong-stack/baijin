package com.baijin.game.core

import com.baijin.game.core.content.loadBundledSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MartialTrainingTest {
    private val content = loadBundledSample()
    private val engine = GameEngine(content) { 44 }
    private val master = "sample_master"
    private val basic = "basic_unarmed"

    @Test
    fun eitherSectCanLearnBothArtsOnTheMessageRewardAndApplyItsOwnTechnique() {
        val started = start(Attributes(7, 8, 4, 9))
        val atMaster = atNpc(started, master)
        val accepted = change(atMaster, GameAction.AcceptQuest("training_message", "confirm_arrangement", 0))
        val confirmed = change(atNpc(accepted, "town_sparring_partner"),
            GameAction.CompleteQuestObjective("training_message", "confirm_arrangement", 1))
        val paid = change(atNpc(confirmed, master), GameAction.TurnInQuest("training_message", 1))
        assertEquals(26, paid.potential)
        assertEquals(UnarmedAttack(11, 0, 0), view(paid).unarmedAttack)

        for ((sect, art, name, otherSect) in listOf(
            Route("qingheng", "shouxian_palm", "守线掌", "duchuan"),
            Route("duchuan", "huichao_fist", "回潮拳", "qingheng"),
        )) {
            val joined = change(paid, GameAction.JoinSect(master, sect))
            assertEquals(sect, joined.martialTraining.sectId)
            assertEquals(paid.potential, joined.potential)
            assertEquals(ActionResult.Blocked(ActionRequirement.AlreadyInSect(view(joined).sect!!.name)),
                engine.dispatch(joined, GameAction.JoinSect(master, otherSect)))
            assertEquals(ActionResult.Blocked(ActionRequirement.MartialLevelRequired("基本拳脚", 1)),
                engine.dispatch(joined, learn(art, 1)))

            val began = change(joined, learn(basic, 1))
            assertEquals(18, began.potential)
            assertEquals(UnarmedAttack(13, 0, 0), view(began).unarmedAttack)
            assertNull(view(began).appliedUnarmedArt)
            assertEquals(ActionResult.Blocked(ActionRequirement.MartialLevelRequired(name, 1)),
                engine.dispatch(began, learn(basic, 2)))

            val applied = change(began, learn(art, 1))
            assertEquals(8, applied.potential)
            assertEquals(mapOf(basic to 1, art to 1), applied.martialTraining.levels)
            assertEquals(art, applied.martialTraining.appliedUnarmedArtId)
            assertEquals(UnarmedAttack(16, 0, 0), view(applied).unarmedAttack)
            assertEquals(ActionResult.Blocked(ActionRequirement.MartialLevelChanged),
                engine.dispatch(applied, learn(art, 1)))
            assertEquals(ActionResult.Blocked(ActionRequirement.MartialLevelRequired("基本拳脚", 2)),
                engine.dispatch(applied, learn(art, 2)))

            val upgraded = change(applied, learn(basic, 2))
            assertEquals(0, upgraded.potential)
            assertEquals(UnarmedAttack(17, 0, 0), view(upgraded).unarmedAttack)
            assertEquals(ActionResult.Blocked(ActionRequirement.PotentialMissing(12)),
                engine.dispatch(upgraded, learn(art, 2)))
            val finished = change(upgraded.copy(potential = 12), learn(art, 2))
            assertEquals(0, finished.potential)
            assertEquals(MartialTrainingState(sect, mapOf(basic to 2, art to 2), art), finished.martialTraining)
            assertEquals(UnarmedAttack(18, 0, 50), view(finished).unarmedAttack)
            assertEquals(ActionResult.Blocked(ActionRequirement.OpeningTrainingComplete),
                engine.dispatch(finished, learn(art, 3)))
            assertEquals(paid.gameTime, finished.gameTime)
            assertEquals(paid.questRounds, finished.questRounds)
        }
    }

    @Test
    fun teachingUsesTheInteractionTileAndAnIndependentSectRequirement() {
        val start = start(engine.creation.balancedAttributes).copy(potential = 7)
        val atMaster = atNpc(start, master)
        assertEquals(ActionResult.Blocked(ActionRequirement.TrainingLocation),
            engine.dispatch(start, GameAction.JoinSect(master, "qingheng")))
        assertEquals(ActionResult.Blocked(ActionRequirement.TrainingLocation),
            engine.dispatch(atMaster, GameAction.LearnMartialArt("sample_granny", basic, 1)))
        assertEquals(ActionResult.Blocked(ActionRequirement.TrainingUnavailable),
            engine.dispatch(atMaster, learn("huichao_fist", 1)))
        val learned = change(atMaster, learn(basic, 1))
        assertEquals(0, learned.potential)
        assertEquals(ActionResult.Blocked(ActionRequirement.SectRequired), engine.dispatch(learned, learn(basic, 2)))
    }

    @Test
    fun insightDiscountRoundsDownAndLeavesAtLeastOnePointOfCost() {
        for ((insight, expected) in mapOf(4 to 8, 5 to 8, 6 to 7, 7 to 7, 8 to 6, 10 to 5)) {
            val state = start(Attributes(7, 7, insight, 14 - insight))
            assertEquals(expected, view(state).lessons.single().nextCost)
        }
        val cheapContent = content.copy(martialArts = content.martialArts.map { art ->
            if (art.id == basic) art.copy(openingLevels = art.openingLevels.map { it.copy(potentialCost = 1) }) else art
        })
        val changedEngine = GameEngine(cheapContent)
        val inspected = changedEngine.dispatch(start(engine.creation.balancedAttributes), GameAction.InspectLocation)
            as ActionResult.Display
        assertEquals(1, inspected.exploration.martialTraining.lessons.single().nextCost)
    }

    @Test
    fun restorationRejectsImpossibleMembershipLevelsAndAppliedArts() {
        val state = start(engine.creation.balancedAttributes)
        for (training in listOf(
            MartialTrainingState(sectId = "unknown"),
            MartialTrainingState(levels = mapOf(basic to 2)),
            MartialTrainingState("qingheng", mapOf(basic to 1, "huichao_fist" to 1), "huichao_fist"),
            MartialTrainingState("qingheng", mapOf(basic to 1, "shouxian_palm" to 2), "shouxian_palm"),
            MartialTrainingState("qingheng", mapOf(basic to 1, "shouxian_palm" to 1)),
        )) {
            assertEquals(ActionResult.Blocked(ActionRequirement.InvalidMartialTraining),
                engine.dispatch(state.copy(martialTraining = training), GameAction.InspectLocation))
        }
    }

    private fun learn(id: String, level: Int) = GameAction.LearnMartialArt(master, id, level)

    private fun start(attributes: Attributes): GameState =
        (engine.dispatch(null, GameAction.Start(Gender.FEMALE, attributes)) as ActionResult.Changed).state

    private fun atNpc(state: GameState, npcId: String): GameState {
        val place = content.locations.single { it.id == content.npcs.single { npc -> npc.id == npcId }.locationId }
        return state.copy(position = WorldPosition(place.sceneId, place.tile))
    }

    private fun change(state: GameState, action: GameAction) = (engine.dispatch(state, action) as ActionResult.Changed).state

    private fun view(state: GameState) =
        (engine.dispatch(state, GameAction.InspectLocation) as ActionResult.Display).exploration.martialTraining

    private data class Route(val sect: String, val art: String, val name: String, val otherSect: String)
}
