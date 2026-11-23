package com.baijin.game.core.content

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
    fun acceptsInclusiveNumericBoundariesAndEmptyRewards() {
        val quest = sample.quests.single()
        val content = sample.copy(
            npcs = listOf(
                sample.npcs[0].copy(morality = -100, initialAffinity = 100, luck = 1),
                sample.npcs[1].copy(morality = 100, initialAffinity = -100, luck = 100),
            ),
            quests = listOf(quest.copy(reward = QuestReward(0, 0, 0, emptyList(), emptyList()))),
        )
        assertEquals(ContentLoadResult.Loaded(content), ContentLoader.load(Json.encodeToString(content)))
    }

    @Test
    fun rejectsMalformedIdsAndBlankNamesAtTheirFields() {
        val content = sample.copy(
            locations = sample.locations + LocationDefinition("Sample Town", "测试地点"),
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
            locations = sample.locations + sample.locations.first(),
            npcs = sample.npcs + sample.npcs.first(),
            quests = sample.quests + sample.quests.first(),
            martialArts = sample.martialArts + sample.martialArts.first(),
            items = sample.items + sample.items.first(),
        )
        assertEquals(
            setOf(
                "$.locations[${sample.locations.size}].id",
                "$.npcs[${sample.npcs.size}].id",
                "$.quests[${sample.quests.size}].id",
                "$.martialArts[${sample.martialArts.size}].id",
                "$.items[${sample.items.size}].id",
            ),
            paths(content),
        )
    }

    @Test
    fun reportsBrokenReferencesAcrossTables() {
        val quest = sample.quests.single()
        val content = sample.copy(
            startLocationId = "unknown_start",
            npcs = listOf(sample.npcs[0].copy(locationId = "unknown_location"), sample.npcs[1]),
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
        val quest = sample.quests.single()
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
        val quest = sample.quests.single()
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
            ),
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
        val quest = sample.quests.single()
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
