package com.baijin.game.core

import com.baijin.game.core.content.ContentLoadResult
import com.baijin.game.core.content.ContentLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuppliesTest {
    private val content = (ContentLoader.load(checkNotNull(javaClass.getResource("/content/sample.json"))
        .readText(Charsets.UTF_8)) as ContentLoadResult.Loaded).content
    private val engine = GameEngine(content) { 44 }
    private val rules = content.supplies
    private val buy = GameAction.BuyFood(rules.sellerNpcId)
    private val eat = GameAction.Eat(rules.foodItemId)
    private fun at(id: String): GameState = (engine.restoreLegacyLocation(1, id) as ActionResult.Display).state
    private fun change(state: GameState, action: GameAction) = (engine.dispatch(state, action) as ActionResult.Changed).state
    private fun blocked(state: GameState, action: GameAction, reason: ActionRequirement) {
        assertEquals(ActionResult.Blocked(reason), engine.dispatch(state, action))
    }

    @Test fun purchasesStackAndCostFourCoinsWithoutAdvancingTime() {
        val initial = at("granny_service")
        assertTrue(initial.inventory.isEmpty())
        val first = change(initial, buy)
        assertEquals(initial.copy(coins = 8, inventory = mapOf("ration" to 1)), first)
        val third = change(change(first, buy), buy)
        assertEquals(0, third.coins)
        assertEquals(mapOf("ration" to 3), third.inventory)
        blocked(third, buy, ActionRequirement.CoinsMissing(4))
        blocked(initial.copy(coins = 3), buy, ActionRequirement.CoinsMissing(1))
        blocked(initial, GameAction.BuyFood("sample_master"), ActionRequirement.SellerUnavailable)
        blocked(at("town_start"), buy, ActionRequirement.SellerTooFar)
        blocked(initial.copy(inventory = mapOf("ration" to Int.MAX_VALUE)), buy, ActionRequirement.InventoryFull)
    }

    @Test fun eatingConsumesOneAndRemovesEmptyEntriesWhilePreservingOtherItems() {
        val initial = at("granny_service").copy(hunger = 12,
            inventory = mapOf("ration" to 2, "sample_light_wood_staff" to 1))
        val first = change(initial, eat)
        assertEquals(initial.copy(hunger = 6, inventory = mapOf("ration" to 1, "sample_light_wood_staff" to 1)), first)
        val last = change(first.copy(hunger = 2), eat)
        assertEquals(mapOf("sample_light_wood_staff" to 1), last.inventory)
        assertEquals(0, last.hunger)
        blocked(first.copy(hunger = 0), eat, ActionRequirement.NotHungry)
        blocked(last, eat, ActionRequirement.FoodMissing)
        blocked(initial, GameAction.Eat("sample_light_wood_staff"), ActionRequirement.ItemNotEdible)
    }

    @Test fun onlyTheDesignatedWellTileInTheCorrectSceneAllowsDrinking() {
        val initial = at("town_well").copy(thirst = 10)
        assertEquals(initial.copy(thirst = 0), change(initial, GameAction.Drink))
        assertEquals(initial.copy(thirst = 0), change(initial.copy(thirst = 2), GameAction.Drink))
        blocked(initial.copy(thirst = 0), GameAction.Drink, ActionRequirement.NotThirsty)
        for (position in listOf(WorldPosition("town", Tile(6, 7)), WorldPosition("town", Tile(7, 5)),
            WorldPosition("player_home", Tile(4, 4)))) {
            blocked(initial.copy(position = position), GameAction.Drink, ActionRequirement.WellTooFar)
        }
        assertTrue((engine.dispatch(initial, GameAction.InspectLocation) as ActionResult.Display).exploration.wellHere)
        assertFalse((engine.dispatch(at("town_start"), GameAction.InspectLocation) as ActionResult.Display).exploration.wellHere)
    }

    @Test fun invalidInventoryIsRejectedBeforeAnyAction() {
        for (inventory in listOf(mapOf("unknown" to 1), mapOf("ration" to 0), mapOf("ration" to -1))) {
            blocked(at("town_well").copy(inventory = inventory), GameAction.Drink, ActionRequirement.InvalidInventory)
        }
    }

    @Test fun suppliesRestoreNaturalRecoveryOnTheNextTimeBoundary() {
        val start = at("town_well").copy(gameTime = 48, hunger = 12, thirst = 10,
            inventory = mapOf("ration" to 1))
        val injured = start.copy(character = start.character.copy(health = 10, effectiveMaxHealth = 35, innerPower = 0))
        val supplied = change(change(injured, eat), GameAction.Drink)
        val recovered = change(supplied, GameAction.Meditate)
        assertEquals(14, recovered.character.health)
        assertEquals(12, recovered.character.innerPower)
        assertEquals(52L, recovered.gameTime)
    }
}
