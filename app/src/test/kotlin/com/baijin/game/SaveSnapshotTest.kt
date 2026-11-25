package com.baijin.game

import com.baijin.game.core.ActionRequirement
import com.baijin.game.core.ActionResult
import com.baijin.game.core.Attributes
import com.baijin.game.core.Direction
import com.baijin.game.core.EquipmentSlot
import com.baijin.game.core.GameAction
import com.baijin.game.core.GameEngine
import com.baijin.game.core.GameState
import com.baijin.game.core.Gender
import com.baijin.game.core.MartialTrainingState
import com.baijin.game.core.QuestRound
import com.baijin.game.core.QuestStage
import com.baijin.game.core.Tile
import com.baijin.game.core.WorldPosition
import com.baijin.game.core.content.AffinityBand
import com.baijin.game.core.content.ContentLoadResult
import com.baijin.game.core.content.ContentLoader
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveSnapshotTest {
    private val content = (ContentLoader.load(resource("/content/sample.json")) as ContentLoadResult.Loaded).content
    private val engine = GameEngine(content) { error("Save restoration must not draw luck") }

    @Test
    fun currentSnapshotRoundTripsTheCompleteState() {
        val source = GameEngine(content) { 44 }.dispatch(null, GameAction.Start(Gender.FEMALE, Attributes(7, 7, 7, 7)))
            as ActionResult.Changed
        val state = source.state.copy(
            position = WorldPosition("town", Tile(7, 5)),
            facing = Direction.EAST,
            character = source.state.character.copy(health = 27, effectiveMaxHealth = 35, innerPower = 9),
            gameTime = 27,
            inventory = mapOf("ration" to 2, "sample_light_wood_staff" to 1),
            hunger = 6,
            thirst = 9,
            potential = 10,
            experience = 20,
            affinities = mapOf("sample_granny" to 20),
            questRounds = mapOf(
                "granny_housework" to QuestRound(2, "fetch_water", QuestStage.IN_PROGRESS),
                "training_message" to QuestRound(1, "confirm_arrangement", QuestStage.IN_PROGRESS),
                "sample_sect_introduction" to QuestRound(1, "confirm_trial", QuestStage.TURNED_IN),
            ),
            martialTraining = MartialTrainingState("qingheng",
                mapOf("basic_unarmed" to 2, "shouxian_palm" to 1, "sample_basic_staff" to 1, "sample_training_staff" to 1),
                "shouxian_palm", "sample_training_staff"),
            equipped = mapOf(EquipmentSlot.WEAPON to "sample_light_wood_staff"),
        )
        val encoded = SaveSnapshot.encode(state)
        assertEquals("9", Json.parseToJsonElement(encoded).jsonObject.getValue("schemaVersion").jsonPrimitive.content)

        val restored = SaveSnapshot.restore(encoded, engine) as ActionResult.Display
        assertEquals(state, restored.state)
        assertEquals(17, restored.exploration.martialTraining.unarmedAttack.power)
        assertEquals(50, restored.state.character.luck)
        assertEquals(4, restored.state.character.appearanceTier)
        assertEquals("五官匀称，仪态端方。", restored.exploration.appearanceDescription)
        val granny = restored.state.copy(position = WorldPosition("granny_home", Tile(4, 4)))
        val dialogue = (engine.dispatch(granny, GameAction.InspectLocation) as ActionResult.Display).exploration.dialogue
        assertEquals(AffinityBand.CLOSE, dialogue?.affinity)
    }

    @Test
    fun oldCompletedHouseworkPaysOnceOnRestoreAwayFromGranny() {
        val yard = (engine.restoreLegacyLocation(content.contentVersion, "town_well") as ActionResult.Display).state
        val pending = yard.copy(questRounds = mapOf("granny_housework" to QuestRound(1, "fetch_water", QuestStage.READY)))
        val oldSave = SaveSnapshot.encode(pending)
        val restored = SaveSnapshot.restore(oldSave, engine) as ActionResult.Display
        assertEquals(yard.position, restored.state.position)
        assertEquals(QuestRound(1, "fetch_water", QuestStage.TURNED_IN), restored.state.questRounds["granny_housework"])
        assertEquals(yard.coins + 50, restored.state.coins)
        assertEquals(yard.potential + 10, restored.state.potential)
        assertEquals(yard.experience + 20, restored.state.experience)
        assertEquals(restored.state, (SaveSnapshot.restore(oldSave, engine) as ActionResult.Display).state)
        val updatedSave = SaveSnapshot.encode(restored.state)
        assertEquals(restored.state, (SaveSnapshot.restore(updatedSave, engine) as ActionResult.Display).state)
    }

    @Test
    fun introductionResumesAtEachStageAndOpensPreparationOnceOnBothRoutes() {
        val master = "sample_master"
        val questId = "sample_sect_introduction"
        val objectiveId = "confirm_trial"
        val staff = "sample_light_wood_staff"
        val accept = GameAction.AcceptQuest(questId, objectiveId, 0)
        val confirm = GameAction.CompleteQuestObjective(questId, objectiveId, 1)
        val turnIn = GameAction.TurnInQuest(questId, 1)
        val initial = (engine.restoreLegacyLocation(content.contentVersion, "town_training_shed") as ActionResult.Display)
            .state.copy(coins = 5, potential = 26, inventory = mapOf(staff to 1))
        fun act(state: GameState, action: GameAction) = (engine.dispatch(state, action) as ActionResult.Changed).state
        fun restart(state: GameState) =
            SaveSnapshot.restore(SaveSnapshot.encode(state), engine) as ActionResult.Display

        for ((sectId, affinity) in mapOf("qingheng" to -20, "duchuan" to 20)) {
            val art = checkNotNull(content.openingTraining).sects.single { it.id == sectId }.unarmedArtId
            val name = content.martialArts.single { it.id == art }.name
            val joined = act(initial.copy(affinities = mapOf(master to affinity)), GameAction.JoinSect(master, sectId))
            val oneArt = act(joined, GameAction.LearnMartialArt(master, "basic_unarmed", 1))
            val requirement = ActionRequirement.MartialLevelRequired(name, 1)
            assertEquals(ActionResult.Blocked(requirement), engine.dispatch(oneArt, accept))
            assertEquals(requirement, restart(oneArt).exploration.quests.single { it.quest.id == questId }.acceptanceBlock)
            val trained = act(oneArt, GameAction.LearnMartialArt(master, art, 1))
            val accepted = restart(act(trained, accept))
            assertEquals(QuestStage.IN_PROGRESS, accepted.exploration.sectPreparation?.stage)
            assertFalse(checkNotNull(accepted.exploration.sectPreparation).unlocked)

            val ready = restart(act(accepted.state, confirm))
            assertEquals(QuestStage.READY, ready.exploration.sectPreparation?.stage)
            assertFalse(checkNotNull(ready.exploration.sectPreparation).unlocked)
            assertEquals(ActionResult.Blocked(ActionRequirement.EquipmentLocked),
                engine.dispatch(ready.state, GameAction.Equip(staff)))
            assertEquals(trained.coins, ready.state.coins)
            assertEquals(trained.inventory, ready.state.inventory)
            assertEquals(trained.martialTraining, ready.state.martialTraining)

            val paidState = act(ready.state, turnIn)
            val paid = restart(paidState)
            assertEquals(paidState, paid.state)
            assertEquals(QuestStage.TURNED_IN, paid.exploration.sectPreparation?.stage)
            assertTrue(checkNotNull(paid.exploration.sectPreparation).unlocked)
            assertTrue(paid.exploration.inventory.equipmentUnlocked)
            assertEquals(trained.coins + 20, paid.state.coins)
            assertEquals(2, paid.state.inventory[staff])
            assertEquals(1, paid.state.martialTraining.levelOf("sample_basic_staff"))
            assertEquals(1, paid.state.martialTraining.levelOf("sample_training_staff"))
            assertEquals("sample_training_staff", paid.state.martialTraining.appliedStaffArtId)
            assertEquals(art, paid.state.martialTraining.appliedUnarmedArtId)
            assertEquals(ActionResult.Blocked(ActionRequirement.QuestAlreadyTurnedIn),
                engine.dispatch(paid.state, accept.copy(previousRound = 1)))
            assertEquals(ActionResult.Blocked(ActionRequirement.QuestRoundChanged), engine.dispatch(paid.state, confirm))
            assertEquals(ActionResult.Blocked(ActionRequirement.QuestRoundChanged), engine.dispatch(paid.state, turnIn))
        }
    }

    @Test
    fun versionEightKeepsItemsAndStartsWithoutAnyEquipment() {
        val state = (engine.restoreLegacyLocation(content.contentVersion, "town_well") as ActionResult.Display).state
            .copy(inventory = mapOf("sample_light_wood_staff" to 1, "cloth_wristguard" to 2))
        val oldJson = SaveSnapshot.encode(state).replace("\"schemaVersion\":9", "\"schemaVersion\":8")
        val restored = SaveSnapshot.restore(oldJson, engine) as ActionResult.Display
        assertEquals(state, restored.state)
        assertEquals(emptyMap<EquipmentSlot, String>(), restored.exploration.inventory.equipped)
        assertEquals(false, restored.exploration.inventory.equipmentUnlocked)
    }

    @Test
    fun versionFiveDefaultsQuestFieldsAndPreservesThePositionAndSupplies() {
        val state = (engine.restoreLegacyLocation(content.contentVersion, "town_well") as ActionResult.Display).state
            .copy(gameTime = 47, coins = 8, inventory = mapOf("ration" to 1))
        val oldJson = SaveSnapshot.encode(state.copy(contentVersion = 2)).replace("\"schemaVersion\":9", "\"schemaVersion\":5")
        val restored = SaveSnapshot.restore(oldJson, engine) as ActionResult.Display
        assertEquals(state, restored.state)
        assertEquals(3, restored.exploration.quests.count { it.round == null })
    }

    @Test
    fun contentVersionTwoHomeKeepsOpenSquaresAndRelocatesBlockedSquares() {
        val state = (engine.restoreLegacyLocation(2, "player_home_start") as ActionResult.Display).state
            .copy(contentVersion = 2, gameTime = 29, coins = 8, inventory = mapOf("ration" to 1))
        for ((old, expected) in mapOf(Tile(7, 5) to Tile(7, 5), Tile(4, 5) to Tile(6, 2))) {
            val original = state.copy(position = WorldPosition("player_home", old))
            val restored = SaveSnapshot.restore(SaveSnapshot.encode(original), engine) as ActionResult.Display
            assertEquals(original.copy(contentVersion = content.contentVersion,
                position = WorldPosition("player_home", expected)), restored.state)
        }
        val invalid = state.copy(position = WorldPosition("player_home", Tile(2, 1)))
        assertEquals(ActionResult.Blocked(ActionRequirement.InvalidPosition),
            SaveSnapshot.restore(SaveSnapshot.encode(invalid), engine))
    }

    @Test
    fun versionSixPreservesQuestRewardAndStartsWithNoSectOrMartialArts() {
        val state = (engine.restoreLegacyLocation(content.contentVersion, "town_training_shed") as ActionResult.Display).state
            .copy(potential = 26, questRounds = mapOf(
                "training_message" to QuestRound(1, "confirm_arrangement", QuestStage.TURNED_IN),
            ))
        val oldJson = SaveSnapshot.encode(state).replace("\"schemaVersion\":9", "\"schemaVersion\":6")
        val restored = SaveSnapshot.restore(oldJson, engine) as ActionResult.Display
        assertEquals(state, restored.state)
        assertEquals(2, restored.exploration.martialTraining.sectChoices.size)
        assertEquals(11, restored.exploration.martialTraining.unarmedAttack.power)
    }

    @Test
    fun versionThreeRestoresResourcesAndMigratesTheOldHomeWithoutDrawingLuck() {
        val source = (GameEngine(content) { 44 }.dispatch(null,
            GameAction.Start(Gender.FEMALE, Attributes(7, 7, 7, 7))) as ActionResult.Changed).state
            .copy(contentVersion = 1, position = WorldPosition("player_home", Tile(3, 3)),
                gameTime = 29, hunger = 7, thirst = 9, coins = 3)
        val oldJson = SaveSnapshot.encode(source).replace("\"schemaVersion\":9", "\"schemaVersion\":3")
        val restored = SaveSnapshot.restore(oldJson, engine) as ActionResult.Display
        assertEquals(source.copy(contentVersion = content.contentVersion, position = WorldPosition("player_home", Tile(6, 2))), restored.state)
        assertEquals(emptyMap<String, Int>(), restored.state.inventory)
    }

    @Test
    fun versionFourMigratesLandmarksAndPreservesTheEntireCharacterAndInventory() {
        val original = (GameEngine(content) { 44 }.dispatch(null,
            GameAction.Start(Gender.FEMALE, Attributes(7, 7, 7, 7))) as ActionResult.Changed).state
            .copy(contentVersion = 1, gameTime = 79, coins = 8, hunger = 11, thirst = 9,
                inventory = mapOf("ration" to 3))
        for ((old, destination) in mapOf(
            WorldPosition("town", Tile(4, 7)) to WorldPosition("granny_home", Tile(4, 4)),
            WorldPosition("town", Tile(8, 7)) to WorldPosition("town", Tile(5, 8)),
            WorldPosition("town", Tile(3, 3)) to WorldPosition("training_yard", Tile(3, 3)),
        )) {
            val state = original.copy(position = old)
            val json = SaveSnapshot.encode(state).replace("\"schemaVersion\":9", "\"schemaVersion\":4")
            val migrated = SaveSnapshot.restore(json, engine) as ActionResult.Display
            assertEquals(state.copy(contentVersion = content.contentVersion, position = destination), migrated.state)
            assertEquals(migrated, SaveSnapshot.restore(SaveSnapshot.encode(migrated.state), engine))
        }
        val invalid = original.copy(position = WorldPosition("town", Tile(4, 10)))
        val json = SaveSnapshot.encode(invalid).replace("\"schemaVersion\":9", "\"schemaVersion\":4")
        assertEquals(ActionResult.Blocked(ActionRequirement.InvalidPosition), SaveSnapshot.restore(json, engine))
    }

    @Test
    fun versionOneFillsMissingContentVersionAndPreservesLocation() {
        val migrated = SaveSnapshot.restore(resource("/saves/v1-location.json"), engine) as ActionResult.Display
        assertEquals(content.contentVersion, migrated.state.contentVersion)
        assertEquals(WorldPosition("town", Tile(2, 8)), migrated.state.position)
        assertEquals("老婆婆家门外", migrated.exploration.locationName)
        assertEquals(Gender.MALE, migrated.state.character.gender)
        assertEquals(Attributes(7, 7, 7, 7), migrated.state.character.attributes)
        assertEquals(50, migrated.state.character.luck)
        assertEquals(4, migrated.state.character.appearanceTier)
        assertEquals(0L, migrated.state.gameTime)
        assertEquals(migrated, SaveSnapshot.restore(SaveSnapshot.encode(migrated.state), engine))
    }

    @Test
    fun versionTwoResolvesEverySampleLocationToItsTownCoordinates() {
        val positions = mapOf(
            "sample_town_start" to WorldPosition("town", Tile(7, 5)),
            "sample_granny_gate" to WorldPosition("town", Tile(2, 8)),
            "sample_training_shed" to WorldPosition("training_yard", Tile(3, 3)),
        )
        for ((locationId, position) in positions) {
            val legacy = """{"schemaVersion":2,"state":{"contentVersion":1,"locationId":"$locationId"}}"""
            val migrated = SaveSnapshot.restore(legacy, engine) as ActionResult.Display
            assertEquals(position, migrated.state.position)
            assertEquals(migrated, SaveSnapshot.restore(legacy, engine))
        }
    }

    @Test
    fun versionOneChecksAnExplicitContentVersion() {
        val unsupported = content.contentVersion + 1
        val legacy = """{"schemaVersion":1,"locationId":"sample_training_shed","contentVersion":$unsupported}"""

        assertEquals(
            ActionResult.Blocked(ActionRequirement.ContentVersionMismatch(content.contentVersion, unsupported)),
            SaveSnapshot.restore(legacy, engine),
        )
    }

    @Test
    fun unknownLegacyLocationCannotSilentlyBecomeTheSpawn() {
        val legacy = """{"schemaVersion":2,"state":{"contentVersion":1,"locationId":"missing_location"}}"""
        assertEquals(
            ActionResult.Blocked(ActionRequirement.UnknownLocation("missing_location")),
            SaveSnapshot.restore(legacy, engine),
        )
    }

    @Test
    fun versionTwoRequiresTheContentVersion() {
        val incomplete = """{"schemaVersion":2,"state":{"locationId":"sample_granny_gate"}}"""

        assertThrows(SerializationException::class.java) { SaveSnapshot.restore(incomplete, engine) }
    }

    @Test
    fun interruptedSnapshotIsInvalidJson() {
        assertThrows(SerializationException::class.java) {
            SaveSnapshot.restore(resource("/saves/interrupted.json"), engine)
        }
    }

    @Test
    fun unsupportedVersionIsReportedWithoutInterpretingItsState() {
        val future = """{"schemaVersion":10,"characters":[]}"""

        val error = assertThrows(UnsupportedSaveVersion::class.java) { SaveSnapshot.restore(future, engine) }
        assertEquals(10, error.version)
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText(Charsets.UTF_8)
}
