package com.baijin.game.core

import com.baijin.game.core.content.loadBundledSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryTest {
    private val content = loadBundledSample()
    private val engine = GameEngine(content) { 44 }
    private val staff = "sample_light_wood_staff"
    private val introduction = "sample_sect_introduction"
    private val master = "sample_master"

    @Test
    fun introductionUnlocksEquipmentAndWearingChangesCombatWithoutSpendingItems() {
        val original = atNpc(start(), master).copy(inventory = mapOf(
            "cloth_cap" to 1, "traveler_coat" to 1, "cloth_wristguard" to 2, "cloth_shoes" to 1,
            "bamboo_training_sword" to 1, "willow_sword" to 1,
        ))
        assertFalse(view(original).equipmentUnlocked)
        assertEquals(ActionResult.Blocked(ActionRequirement.EquipmentLocked),
            engine.dispatch(original, GameAction.Equip("cloth_wristguard")))
        assertEquals(ActionResult.Blocked(ActionRequirement.SectRequired),
            engine.dispatch(original, GameAction.AcceptQuest(introduction, "confirm_trial", 0)))
        var state = change(original.copy(potential = 26), GameAction.JoinSect(master, "qingheng"))
        state = change(state, GameAction.LearnMartialArt(master, "basic_unarmed", 1))
        state = change(state, GameAction.LearnMartialArt(master, "shouxian_palm", 1))
        state = state.copy(martialTraining = state.martialTraining.copy(
            levels = state.martialTraining.levels + ("sample_basic_staff" to 2),
        ))
        state = change(state, GameAction.AcceptQuest(introduction, "confirm_trial", 0))
        val turnIn = GameAction.TurnInQuest(introduction, 1)
        assertEquals(ActionResult.Blocked(ActionRequirement.QuestRoundChanged), engine.dispatch(state, turnIn))
        state = change(state, GameAction.CompleteQuestObjective(introduction, "confirm_trial", 1))
        assertEquals(ActionResult.Blocked(ActionRequirement.RewardLimit),
            engine.dispatch(state.copy(coins = Int.MAX_VALUE), turnIn))
        assertEquals(ActionResult.Blocked(ActionRequirement.InventoryFull),
            engine.dispatch(state.copy(inventory = state.inventory + (staff to Int.MAX_VALUE)), turnIn))
        val paid = change(state, turnIn)
        assertEquals(original.coins + 20, paid.coins)
        assertEquals(original.inventory + (staff to 1), paid.inventory)
        assertEquals(2, paid.martialTraining.levelOf("sample_basic_staff"))
        assertEquals(1, paid.martialTraining.levelOf("sample_training_staff"))
        assertEquals("sample_training_staff", paid.martialTraining.appliedStaffArtId)
        assertTrue(view(paid).equipmentUnlocked)
        assertEquals(ActionResult.Blocked(ActionRequirement.QuestRoundChanged),
            engine.dispatch(paid, GameAction.TurnInQuest(introduction, 1)))

        state = listOf(staff, "cloth_cap", "traveler_coat", "cloth_wristguard", "cloth_shoes")
            .fold(paid) { next, item -> change(next, GameAction.Equip(item)) }
        assertEquals(5, view(state).equipped.size)
        assertEquals(1, view(state).stacks.single { it.item.id == "cloth_wristguard" }.available)
        assertEquals(EquipmentStats(power = 2, hit = 8, protection = 5, evasion = 8), view(state).stats)
        val fight = engine.startSparring(atNpc(state, "town_sparring_partner"), "town_sparring_partner", 11L)
            as SparringStart.Started
        assertEquals(19, fight.state.player.attack.power)
        assertEquals(8, fight.state.player.attack.hitBonus)
        assertEquals(5, fight.state.player.defense.protection)
        assertEquals(8, fight.state.player.defense.evasionBonus)
        assertEquals(ActionResult.Blocked(ActionRequirement.EquipmentArtRequired("基本剑术", 2, 0)),
            engine.dispatch(state, GameAction.Equip("willow_sword")))

        val replaced = change(state, GameAction.Equip("bamboo_training_sword"))
        assertEquals("bamboo_training_sword", replaced.equipped[EquipmentSlot.WEAPON])
        assertEquals(paid.inventory, replaced.inventory)
        assertEquals(ActionResult.Blocked(ActionRequirement.EquipmentChanged),
            engine.dispatch(replaced, GameAction.Unequip(EquipmentSlot.WEAPON, staff)))
        val empty = change(replaced, GameAction.Unequip(EquipmentSlot.WEAPON, "bamboo_training_sword"))
        assertEquals(paid.inventory, empty.inventory)
        assertFalse(EquipmentSlot.WEAPON in empty.equipped)
        assertEquals(original.gameTime, empty.gameTime)
        assertEquals(ActionResult.Blocked(ActionRequirement.InvalidInventory),
            engine.dispatch(empty.copy(equipped = mapOf(EquipmentSlot.HEAD to staff)), GameAction.InspectLocation))
    }

    @Test
    fun seamstressUnlocksAndSellsTheFullOpeningOutfitFromTheIntroductionBudget() {
        val seller = "town_seamstress"
        val coat = "traveler_coat"
        val buyCoat = GameAction.BuyArmour(seller, coat)
        val original = start()
        assertEquals(ActionResult.Blocked(ActionRequirement.ArmourShopTooFar), engine.dispatch(original, buyCoat))
        val before = atNpc(original, seller)
        val prices = mapOf("cloth_cap" to 4, coat to 8, "cloth_wristguard" to 4, "cloth_shoes" to 4)
        val shop = (engine.dispatch(before, GameAction.InspectLocation) as ActionResult.Display).exploration
        assertEquals(prices, shop.armourOffers.associate { it.item.id to it.price })
        assertFalse(shop.inventory.equipmentUnlocked)
        assertEquals(ActionResult.Blocked(ActionRequirement.EquipmentLocked), engine.dispatch(before, buyCoat))

        var state = atNpc(original.copy(coins = 0, potential = 26), master)
        state = change(state, GameAction.JoinSect(master, "qingheng"))
        state = change(state, GameAction.LearnMartialArt(master, "basic_unarmed", 1))
        state = change(state, GameAction.LearnMartialArt(master, "shouxian_palm", 1))
        state = change(state, GameAction.AcceptQuest(introduction, "confirm_trial", 0))
        state = change(state, GameAction.CompleteQuestObjective(introduction, "confirm_trial", 1))
        val paid = atNpc(change(state, GameAction.TurnInQuest(introduction, 1)), seller)
        assertEquals(ActionResult.Blocked(ActionRequirement.ArmourUnavailable),
            engine.dispatch(paid, GameAction.BuyArmour(seller, staff)))
        assertEquals(ActionResult.Blocked(ActionRequirement.InventoryFull),
            engine.dispatch(paid.copy(inventory = paid.inventory + (coat to Int.MAX_VALUE)), buyCoat))

        val shopped = prices.keys.fold(paid) { next, item -> change(next, GameAction.BuyArmour(seller, item)) }
        assertEquals(paid.copy(coins = 0, inventory = paid.inventory + prices.mapValues { 1 }), shopped)
        assertEquals(ActionResult.Blocked(ActionRequirement.CoinsMissing(8)), engine.dispatch(shopped, buyCoat))
        val dressed = (prices.keys + staff).fold(shopped) { next, item -> change(next, GameAction.Equip(item)) }
        assertEquals(5, view(dressed).equipped.size)
        val spare = change(dressed.copy(coins = 8), buyCoat)
        assertEquals(dressed.equipped, spare.equipped)
        assertEquals(1, view(spare).stacks.first { it.item.id == coat }.available)
    }

    @Test
    fun medicineForecastAndConsumptionRespectTheEffectiveHealthCap() {
        val original = start()
        val state = original.copy(
            character = original.character.copy(health = 20, effectiveMaxHealth = 26),
            inventory = mapOf("wound_balm" to 2),
        )
        assertEquals(InventoryUse(InventoryEffect.HEAL, 12, 20, 26, 26), view(state).stacks.single().use)
        val healed = change(state, GameAction.UseMedicine("wound_balm"))
        assertEquals(26, healed.character.health)
        assertEquals(mapOf("wound_balm" to 1), healed.inventory)
        assertEquals(state.gameTime, healed.gameTime)
        assertEquals(ActionResult.Blocked(ActionRequirement.HealingUnavailable),
            engine.dispatch(healed, GameAction.UseMedicine("wound_balm")))
    }

    private fun start() = change(null, GameAction.Start(Gender.MALE, engine.creation.balancedAttributes))

    private fun change(state: GameState?, action: GameAction) = (engine.dispatch(state, action) as ActionResult.Changed).state

    private fun view(state: GameState) =
        (engine.dispatch(state, GameAction.InspectLocation) as ActionResult.Display).exploration.inventory

    private fun atNpc(state: GameState, id: String): GameState {
        val npc = content.npcs.first { it.id == id }
        val location = content.locations.first { it.id == npc.locationId }
        return state.copy(position = WorldPosition(location.sceneId, location.tile))
    }
}
