package com.baijin.game.core.content

import com.baijin.game.core.Direction
import com.baijin.game.core.QuestStage
import com.baijin.game.core.Tile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentLoaderTest {
    private val sample = loadBundledSample()

    @Test
    fun rejectsBrokenOpeningTrainingReferencesAndInvalidLessonCosts() {
        val training = checkNotNull(sample.openingTraining)
        val index = sample.martialArts.indexOfFirst { it.id == training.basicUnarmedArtId }
        val changed = sample.copy(
            openingTraining = training.copy(sects = listOf(
                training.sects[0].copy(unarmedArtId = "missing_art"),
                training.sects[1].copy(unarmedArtId = training.basicUnarmedArtId),
            )),
            martialArts = sample.martialArts.mapIndexed { i, art ->
                if (i == index) art.copy(openingLevels = listOf(art.openingLevels.first().copy(potentialCost = 0)))
                else art
            },
        )
        assertEquals(setOf(
            "$.openingTraining.basicUnarmedArtId",
            "$.openingTraining.sects[0].unarmedArtId",
            "$.openingTraining.sects[1].unarmedArtId",
            "$.martialArts[$index].openingLevels[0].potentialCost",
        ), paths(changed))
    }

    @Test
    fun validatesSupplyReferencesAndPositiveValues() {
        val invalid = sample.copy(supplies = sample.supplies.copy(sellerNpcId = "missing_npc",
            foodItemId = "missing_food", wellLocationId = "missing_well", foodPrice = 0,
            hungerReduction = -1, thirstReduction = 0))
        assertEquals(setOf("$.supplies.sellerNpcId", "$.supplies.foodItemId", "$.supplies.wellLocationId",
            "$.supplies.foodPrice", "$.supplies.hungerReduction", "$.supplies.thirstReduction"), paths(invalid))
        assertEquals(setOf("$.supplies.wellLocationId"), paths(sample.copy(
            supplies = sample.supplies.copy(wellLocationId = "town_start"))))
    }

    @Test
    fun validatesOpeningArmourStockAndAcceptsContentWithoutTheShop() {
        val shop = checkNotNull(sample.openingArmourShop)
        val invalid = shop.copy(stock = listOf(
            ArmourStock("missing_armour", 2),
            ArmourStock(sample.supplies.foodItemId, 0),
            shop.stock.first(),
            shop.stock.first(),
        ))
        assertEquals(setOf(
            "$.openingArmourShop.stock[0].itemId",
            "$.openingArmourShop.stock[1].itemId",
            "$.openingArmourShop.stock[1].price",
            "$.openingArmourShop.stock[3].itemId",
        ), paths(sample.copy(openingArmourShop = invalid)))
        val previous = sample.copy(openingArmourShop = null,
            npcs = sample.npcs.map { it.copy(services = it.services - NpcService.ARMOUR) })
        assertEquals(ContentLoadResult.Loaded(previous), ContentLoader.load(Json.encodeToString(previous)))
    }

    @Test
    fun validatesSparringReferenceAndTrialParameters() {
        val sparring = checkNotNull(sample.openingSparring)
        val invalid = sparring.copy(
            id = "Bad encounter",
            npcId = "missing_npc",
            damageVariance = -1,
            playerDefense = sparring.playerDefense.copy(baseParryPercent = 101),
            opponent = sparring.opponent.copy(
                strength = 0,
                agility = -1,
                maxHealth = 0,
                maxInnerPower = 0,
                defense = sparring.opponent.defense.copy(protection = -1, evasionBonus = 101),
            ),
        )
        assertEquals(setOf(
            "id", "npcId", "damageVariance", "playerDefense.baseParryPercent",
            "opponent.strength", "opponent.agility", "opponent.maxHealth", "opponent.maxInnerPower",
            "opponent.defense.protection", "opponent.defense.evasionBonus",
        ).map { "$.openingSparring.$it" }.toSet(), paths(sample.copy(openingSparring = invalid)))
        assertEquals(setOf("$.openingSparring.npcId"), paths(sample.copy(
            openingSparring = sparring.copy(npcId = sample.supplies.sellerNpcId),
        )))
    }

    @Test
    fun validatesOpeningAttackLinesAndAcceptsContentWithoutSparring() {
        val previous = sample.copy(openingSparring = null,
            martialArts = sample.martialArts.map { it.copy(openingAttackLines = emptyList()) })
        assertEquals(ContentLoadResult.Loaded(previous), ContentLoader.load(Json.encodeToString(previous)))
        val changed = sample.copy(martialArts = sample.martialArts.mapIndexed { index, art ->
            when (index) {
                0 -> art.copy(openingAttackLines = listOf("挥棍"))
                1 -> art.copy(openingAttackLines = listOf("挥棍", " ", "横扫"))
                else -> art
            }
        })
        assertEquals(setOf("$.martialArts[0].openingAttackLines", "$.martialArts[1].openingAttackLines"), paths(changed))
    }

    @Test
    fun validatesSparringLootWeightsAndReferences() {
        val sparring = checkNotNull(sample.openingSparring).copy(
            lootGroups = listOf(SparringLootGroup(-1, mapOf("missing_item" to 0))),
            requiredLoot = listOf(SparringQuestLoot(
                "training_message", QuestStage.READY, mapOf("ration" to 1), "missing_objective",
            )),
        )
        assertEquals(setOf(
            "$.openingSparring.lootGroups",
            "$.openingSparring.lootGroups[0].weight",
            "$.openingSparring.lootGroups[0].items.missing_item",
            "$.openingSparring.requiredLoot[0].objectiveId",
        ), paths(sample.copy(openingSparring = sparring)))
    }

    @Test
    fun acceptsInclusiveNumericBoundariesAndEmptyRewards() {
        val content = sample.copy(
            npcs = listOf(
                sample.npcs[0].copy(morality = -100, initialAffinity = 100, luck = 1),
                sample.npcs[1].copy(morality = 100, initialAffinity = -100, luck = 100),
            ) + sample.npcs.drop(2),
            quests = sample.quests.map { quest ->
                if (quest.id == "training_message") quest.copy(reward = QuestReward(0, 0, 0, emptyList(), emptyList()))
                else quest
            },
        )
        assertEquals(ContentLoadResult.Loaded(content), ContentLoader.load(Json.encodeToString(content)))
    }

    @Test
    fun rejectsMalformedIdsAndBlankNamesAtTheirFields() {
        val content = sample.copy(
            locations = sample.locations + sample.locations.first().copy(id = "Sample Town", name = "测试地点", tile = Tile(6, 3)),
            items = sample.items + ItemDefinition("sample_unnamed_item", " "),
        )
        assertEquals(
            setOf("$.locations[${sample.locations.size}].id", "$.items[${sample.items.size}].name"),
            paths(content),
        )
    }

    @Test
    fun reportsTheDuplicateEntryInEachTable() {
        val content = sample.copy(
            locations = sample.locations + sample.locations.first().copy(tile = Tile(6, 3)),
            npcs = sample.npcs + sample.npcs.first(),
            quests = sample.quests + sample.quests.first(),
            martialArts = sample.martialArts + sample.martialArts.first(),
            items = sample.items + sample.items.first(),
        )
        assertEquals(
            setOf(
                "$.locations[${sample.locations.size}].id",
                "$.npcs[${sample.npcs.size}].id",
                "$.npcs[${sample.npcs.size}].locationId",
                "$.quests[${sample.quests.size}].id",
                "$.martialArts[${sample.martialArts.size}].id",
                "$.items[${sample.items.size}].id",
            ),
            paths(content),
        )
    }

    @Test
    fun reportsBrokenReferencesAcrossTables() {
        val quest = sample.quests.single { it.id == "sample_sect_introduction" }
        val content = sample.copy(
            startLocationId = "unknown_start",
            npcs = listOf(sample.npcs[0].copy(locationId = "unknown_location")) + sample.npcs.drop(1),
            quests = listOf(quest.copy(
                giverNpcId = "unknown_giver",
                turnInNpcId = "unknown_turn_in",
                reward = quest.reward.copy(
                    items = listOf(ItemAmount("unknown_item", 1)),
                    martialArts = listOf(MartialArtGrant("unknown_martial_art", 1)),
                ),
            )),
        )
        assertEquals(
            setOf(
                "$.startLocationId",
                "$.npcs[0].locationId",
                "$.quests[0].giverNpcId",
                "$.quests[0].turnInNpcId",
                "$.quests[0].reward.items[0].itemId",
                "$.quests[0].reward.martialArts[0].martialArtId",
            ),
            paths(content),
        )
    }

    @Test
    fun rejectsDuplicateItemAndMartialArtRewards() {
        val quest = sample.quests.single { it.id == "sample_sect_introduction" }
        val reward = quest.reward
        val duplicated = reward.copy(
            items = reward.items + reward.items.first(),
            martialArts = reward.martialArts + reward.martialArts.first(),
        )
        assertEquals(
            setOf(
                "$.quests[0].reward.items[${reward.items.size}].itemId",
                "$.quests[0].reward.martialArts[${reward.martialArts.size}].martialArtId",
            ),
            paths(sample.copy(quests = listOf(quest.copy(reward = duplicated)))),
        )
    }

    @Test
    fun rejectsOutOfRangeNumbers() {
        val quest = sample.quests.single { it.id == "sample_sect_introduction" }
        val reward = quest.reward.copy(
            coins = -1,
            potential = -1,
            experience = -1,
            items = listOf(quest.reward.items.single().copy(quantity = 0)),
            martialArts = quest.reward.martialArts.map { it.copy(level = 0) },
        )
        val content = sample.copy(
            contentVersion = 0,
            npcs = listOf(
                sample.npcs[0].copy(morality = -101, initialAffinity = 101, luck = 0),
                sample.npcs[1].copy(morality = 101, initialAffinity = -101, luck = 101),
            ) + sample.npcs.drop(2),
            quests = listOf(quest.copy(reward = reward)),
            martialArts = sample.martialArts.mapIndexed { index, art -> if (index == 0) art.copy(maxLevel = 0) else art },
        )
        assertEquals(
            setOf(
                "$.contentVersion", "$.npcs[0].morality", "$.npcs[0].initialAffinity", "$.npcs[0].luck",
                "$.npcs[1].morality", "$.npcs[1].initialAffinity", "$.npcs[1].luck",
                "$.martialArts[0].maxLevel", "$.quests[0].reward.coins", "$.quests[0].reward.potential",
                "$.quests[0].reward.experience", "$.quests[0].reward.items[0].quantity",
                "$.quests[0].reward.martialArts[0].level", "$.quests[0].reward.martialArts[1].level",
            ),
            paths(content),
        )
    }

    @Test
    fun rejectsAMartialArtGrantAboveItsConfiguredMaximum() {
        val quest = sample.quests.single { it.id == "sample_sect_introduction" }
        val grant = quest.reward.martialArts.first()
        val maximum = sample.martialArts.single { it.id == grant.martialArtId }.maxLevel
        val reward = quest.reward.copy(martialArts = listOf(grant.copy(level = maximum + 1)))
        assertEquals(
            setOf("$.quests[0].reward.martialArts[0].level"),
            paths(sample.copy(quests = listOf(quest.copy(reward = reward)))),
        )
    }

    @Test
    fun rejectsAppearanceCountsWeightsAndBlankDescriptions() {
        val appearance = sample.appearance.copy(
            weights = AppearanceWeights(0, -1, 0, -1),
            thresholds = sample.appearance.thresholds.dropLast(1),
            descriptions = listOf(AppearanceDescription(" ", "")),
        )
        assertEquals(
            setOf(
                "$.npcs[0].dialogue.NORMAL[3].minAppearanceTier",
                "$.appearance.weights.strength", "$.appearance.weights.agility",
                "$.appearance.weights.insight", "$.appearance.weights.constitution",
                "$.appearance.thresholds", "$.appearance.descriptions",
                "$.appearance.descriptions[0].male", "$.appearance.descriptions[0].female",
            ),
            paths(sample.copy(appearance = appearance)),
        )
    }

    @Test
    fun rejectsEqualAndDescendingAppearanceThresholds() {
        val appearance = sample.appearance.copy(thresholds = listOf(30, 30, 29, 48, 63, 72))
        assertEquals(
            setOf("$.appearance.thresholds[1]", "$.appearance.thresholds[2]"),
            paths(sample.copy(appearance = appearance)),
        )
    }

    @Test
    fun rejectsMalformedTerrainAndWalkableWater() {
        val town = sample.scenes.single { it.id == "town" }
        val index = sample.scenes.indexOf(town)
        val water = town.terrain.toMutableList().apply { this[5] = this[5].replaceRange(7, 8, "w") }
        val malformed = sample.copy(scenes = sample.scenes.toMutableList().apply {
            this[index] = town.copy(terrain = water)
        })
        assertTrue("Water cannot be walkable", "$.scenes[$index].terrain[5][7]" in paths(malformed))
        assertTrue("Width is checked", "$.scenes[$index].terrain[0]" in paths(sample.copy(
            scenes = sample.scenes.toMutableList().apply { this[index] = town.copy(terrain = listOf("x") + town.terrain.drop(1)) })))
    }

    @Test
    fun rejectsUnpairedDoorsAndDoorsThatReplaceWalkableSteps() {
        val town = sample.scenes.single { it.id == "town" }
        val home = sample.scenes.single { it.id == "player_home" }
        val townIndex = sample.scenes.indexOf(town)
        val homeIndex = sample.scenes.indexOf(home)
        val townDoor = town.exits.first { it.id == "town_home_door" }
        val homeDoor = home.exits.single()
        val malformed = sample.copy(scenes = sample.scenes.toMutableList().apply {
            this[townIndex] = town.copy(exits = town.exits.map {
                if (it == townDoor) it.copy(destinationExitId = "unknown_door") else it
            })
            this[homeIndex] = home.copy(exits = listOf(homeDoor.copy(direction = Direction.WEST)))
        })
        assertTrue(paths(malformed).containsAll(setOf(
            "$.scenes[$townIndex].exits[0].destinationExitId",
            "$.scenes[$homeIndex].exits[0].destinationExitId",
            "$.scenes[$homeIndex].exits[0].direction",
        )))
    }

    @Test
    fun rejectsImpossibleAllocationAndInvalidTimeParameters() {
        val malformed = sample.copy(
            creation = sample.creation.copy(attributePoints = 25, maxLuckDraw = 89),
            time = sample.time.copy(hungerInterval = 0, maxThirst = 0, meditationDuration = 0, meditationRecovery = -1),
        )
        assertEquals(setOf(
            "$.creation.attributePoints", "$.creation.maxLuckDraw", "$.time.hungerInterval", "$.time.maxThirst",
            "$.time.meditationDuration", "$.time.meditationRecovery",
        ), paths(malformed))
    }

    @Test
    fun reportsMissingNestedDescription() {
        val json = Json.parseToJsonElement(readBundledSample()).jsonObject
        val appearance = json.getValue("appearance").jsonObject
        val descriptions = appearance.getValue("descriptions").jsonArray
        val first = JsonObject(descriptions.first().jsonObject - "female")
        val badAppearance = JsonObject(appearance + ("descriptions" to JsonArray(listOf(first) + descriptions.drop(1))))
        val badJson = JsonObject(json + ("appearance" to badAppearance))
        assertEquals(setOf("$.appearance.descriptions[0].female"), paths(badJson.toString()))
    }

    @Test
    fun locatesWrongFieldTypes() {
        val json = Json.parseToJsonElement(readBundledSample()).jsonObject
        val badJson = JsonObject(json + ("contentVersion" to JsonPrimitive(false)))
        assertEquals(setOf("$.contentVersion"), paths(badJson.toString()))
    }

    @Test
    fun rejectsMalformedJson() {
        val result = ContentLoader.load("{") as ContentLoadResult.Invalid
        assertTrue(result.issues.single().message.isNotBlank())
        assertEquals("$", result.issues.single().path)
    }

    private fun paths(content: GameContent): Set<String> = paths(Json.encodeToString(content))

    private fun paths(json: String): Set<String> {
        val result = ContentLoader.load(json)
        assertTrue("Expected invalid content, got $result", result is ContentLoadResult.Invalid)
        return (result as ContentLoadResult.Invalid).issues.map(ContentIssue::path).toSet()
    }
}
