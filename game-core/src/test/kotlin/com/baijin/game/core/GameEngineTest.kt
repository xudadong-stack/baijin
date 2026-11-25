package com.baijin.game.core

import com.baijin.game.core.content.loadBundledSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    private val content = loadBundledSample()
    private val engine = GameEngine(content) { 44 }
    private val balanced = Attributes(7, 7, 7, 7)

    @Test
    fun startSetsTheHomeSpawnAndFullResources() {
        val start = start()
        assertEquals(content.contentVersion, start.state.contentVersion)
        assertEquals(WorldPosition("player_home", Tile(6, 2)), start.state.position)
        assertEquals("player_home_start", start.exploration.currentLocation?.id)
        assertEquals(
            CharacterState(Gender.MALE, balanced, 50, 4, 51, 51, 51, 22, 22),
            start.state.character,
        )
        assertEquals(12, start.state.coins)
        assertEquals(0L, start.state.gameTime)
        assertEquals(0, start.state.hunger)
        assertEquals(0, start.state.thirst)
        assertEquals(16, start(Attributes(10, 10, 4, 4)).state.character.innerPower)
        assertEquals(28, start(Attributes(4, 4, 10, 10)).state.character.innerPower)
    }

    @Test
    fun invalidAllocationIsRejectedBeforeDrawingLuck() {
        val noDraw = GameEngine(content) { error("Invalid creation must not draw luck") }
        fun attempt(attributes: Attributes) = noDraw.dispatch(null, GameAction.Start(Gender.MALE, attributes))
        assertEquals(ActionResult.Blocked(ActionRequirement.AttributeOutOfRange(4, 10)), attempt(Attributes(11, 5, 6, 6)))
        assertEquals(ActionResult.Blocked(ActionRequirement.AttributeOutOfRange(4, 10)), attempt(Attributes(3, 7, 9, 9)))
        assertEquals(ActionResult.Blocked(ActionRequirement.AttributeBudgetMismatch), attempt(Attributes(4, 4, 4, 4)))
        assertEquals(ActionResult.Blocked(ActionRequirement.AttributeBudgetMismatch), attempt(Attributes(10, 10, 10, 10)))
    }

    @Test
    fun fixedDrawsCoverLuckEndpointsAndBalancedAppearance() {
        assertEquals(1, start(Attributes(10, 10, 4, 4), draw = 1).state.character.luck)
        assertEquals(100, start(Attributes(4, 4, 10, 10), draw = 88).state.character.luck)
        assertEquals(listOf(7, 50, 94), listOf(1, 44, 88).map { start(draw = it).state.character.luck })
        assertEquals(listOf(2, 4, 6), listOf(1, 44, 88).map { start(draw = it).state.character.appearanceTier })
        val strengthAndInsight = start(Attributes(10, 4, 10, 4))
        val agilityAndConstitution = start(Attributes(4, 10, 4, 10))
        assertEquals(listOf(50, 50), listOf(strengthAndInsight, agilityAndConstitution).map { it.state.character.luck })
        assertEquals(listOf(3, 5), listOf(strengthAndInsight, agilityAndConstitution).map { it.state.character.appearanceTier })
    }

    @Test
    fun fixedDrawsExerciseBothSidesOfAllSixAppearanceThresholds() {
        val lowerScoring = Attributes(10, 4, 10, 4)
        val higherScoring = Attributes(4, 10, 4, 10)
        // Scores with these builds are 18 + (draw + 6) / 2 and 42 + (draw + 6) / 2.
        val tiersAt29Through63 = listOf(16, 18, 34, 36, 52, 54, 82, 84).map {
            start(lowerScoring, draw = it).state.character.appearanceTier
        }
        val tiersAt71Through81 = listOf(52, 54, 70, 72).map {
            start(higherScoring, draw = it).state.character.appearanceTier
        }
        assertEquals(listOf(1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7), tiersAt29Through63 + tiersAt71Through81)
    }

    @Test
    fun inspectUsesTheSavedAppearanceAfterAttributesChange() {
        val male = start()
        val female = start(gender = Gender.FEMALE)
        assertEquals("五官端正，举止利落。", male.exploration.appearanceDescription)
        assertEquals("五官匀称，仪态端方。", female.exploration.appearanceDescription)
        assertEquals(male.state.character.appearanceTier, female.state.character.appearanceTier)
        val grown = female.state.copy(character = female.state.character.copy(attributes = Attributes(7, 7, 12, 7)))
        val noDraw = GameEngine(content) { error("Inspecting must not draw luck") }
        val resumed = noDraw.dispatch(grown, GameAction.InspectLocation) as ActionResult.Display
        assertEquals(grown, resumed.state)
        assertEquals(50, resumed.state.character.luck)
        assertEquals(female.exploration.appearanceDescription, resumed.exploration.appearanceDescription)
    }

    @Test
    fun inspectReportsMissingOrIncompatibleState() {
        val state = start().state
        assertEquals(ActionResult.Blocked(ActionRequirement.StartRequired), engine.dispatch(null, GameAction.InspectLocation))
        assertEquals(
            ActionResult.Blocked(ActionRequirement.ContentVersionMismatch(content.contentVersion, content.contentVersion + 1)),
            engine.dispatch(state.copy(contentVersion = content.contentVersion + 1), GameAction.InspectLocation),
        )
        assertEquals(
            ActionResult.Blocked(ActionRequirement.UnknownScene("missing_scene")),
            engine.dispatch(state.copy(position = state.position.copy(sceneId = "missing_scene")), GameAction.InspectLocation),
        )
        assertEquals(
            ActionResult.Blocked(ActionRequirement.InvalidPosition),
            engine.dispatch(state.copy(position = state.position.copy(tile = Tile(4, 10))), GameAction.InspectLocation),
        )
    }

    @Test
    fun movementTicksHungerAndThirstAndRestoresOnlyToEffectiveLimits() {
        val initial = start().state
        var state = initial.copy(character = initial.character.copy(health = 34, effectiveMaxHealth = 35, innerPower = 19))
        repeat(6) { step ->
            state = move(state, if (step % 2 == 0) Direction.SOUTH else Direction.NORTH).state
            assertEquals((step + 1).toLong(), state.gameTime)
            assertEquals((step + 1) / 4, state.hunger)
            assertEquals((step + 1) / 3, state.thirst)
            assertEquals(if (step == 0) 34 else 35, state.character.health)
            assertEquals(listOf(19, 19, 21, 21, 21, 22)[step], state.character.innerPower)
        }
    }

    @Test
    fun reachingEitherNeedLimitPreventsRecoveryOnThatSameTick() {
        val initial = start().state
        val injured = initial.character.copy(health = 20, innerPower = 1)
        val hungry = initial.copy(gameTime = 47, hunger = 11, character = injured)
        val thirsty = initial.copy(gameTime = 29, thirst = 9, character = injured)
        val atHungerLimit = move(hungry, Direction.SOUTH).state
        val atThirstLimit = move(thirsty, Direction.SOUTH).state
        assertEquals(injured, atHungerLimit.character)
        assertEquals(injured, atThirstLimit.character)
        assertEquals(12, atHungerLimit.hunger)
        assertEquals(10, atThirstLimit.thirst)
        val capped = (engine.dispatch(atHungerLimit.copy(thirst = 10), GameAction.Meditate) as ActionResult.Changed).state
        assertEquals(12, capped.hunger)
        assertEquals(10, capped.thirst)
        assertEquals(injured, capped.character)
    }

    @Test
    fun meditationReplacesInnerPowerRecoveryAndChecksNeedsEachTick() {
        val initial = start().state
        val depleted = initial.character.copy(health = 20, innerPower = 2)
        val healthyNeeds = initial.copy(character = depleted)
        val soonThirsty = healthyNeeds.copy(gameTime = 27, hunger = 6, thirst = 9)
        val fullSession = (engine.dispatch(healthyNeeds, GameAction.Meditate) as ActionResult.Changed).state
        val partSession = (engine.dispatch(soonThirsty, GameAction.Meditate) as ActionResult.Changed).state
        assertEquals(healthyNeeds.position, fullSession.position)
        assertEquals(4L, fullSession.gameTime)
        assertEquals(24, fullSession.character.health)
        assertEquals(14, fullSession.character.innerPower)
        assertEquals(31L, partSession.gameTime)
        assertEquals(7, partSession.hunger)
        assertEquals(10, partSession.thirst)
        assertEquals(22, partSession.character.health)
        assertEquals(8, partSession.character.innerPower)
        val fullVitals = (engine.dispatch(initial, GameAction.Meditate) as ActionResult.Changed).state
        assertEquals(initial.character, fullVitals.character)
        assertEquals(4L, fullVitals.gameTime)
    }

    private fun start(
        attributes: Attributes = balanced,
        gender: Gender = Gender.MALE,
        draw: Int = 44,
    ) = GameEngine(content) { range ->
        assertEquals(1..88, range)
        draw
    }.dispatch(null, GameAction.Start(gender, attributes)) as ActionResult.Changed

    private fun move(state: GameState, direction: Direction) =
        engine.dispatch(state, GameAction.Move(direction)) as ActionResult.Changed

    private fun atTownStart() =
        (engine.restoreLegacyLocation(content.contentVersion, "town_start") as ActionResult.Display).state
}
