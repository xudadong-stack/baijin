package com.baijin.game.core

import com.baijin.game.core.content.GameContent
import com.baijin.game.core.content.ItemDefinition
import com.baijin.game.core.content.MartialArtGrant
import kotlinx.serialization.Serializable

@Serializable
enum class EquipmentSlot { WEAPON, HEAD, BODY, HANDS, FEET }

@Serializable
enum class WeaponType { SWORD, SABER, STAFF, WHIP }

@Serializable
data class EquipmentDefinition(
    val slot: EquipmentSlot,
    val weaponType: WeaponType? = null,
    val stats: EquipmentStats = EquipmentStats(),
    val requiredAttributes: Map<Attribute, Int> = emptyMap(),
    val requiredArt: MartialArtGrant? = null,
)

@Serializable
data class EquipmentStats(
    val power: Int = 0,
    val hit: Int = 0,
    val parry: Int = 0,
    val protection: Int = 0,
    val evasion: Int = 0,
) {
    operator fun plus(other: EquipmentStats) = EquipmentStats(
        power + other.power, hit + other.hit, parry + other.parry,
        protection + other.protection, evasion + other.evasion,
    )
}

enum class InventoryEffect { FOOD, HEAL }

data class InventoryUse(
    val effect: InventoryEffect,
    val amount: Int,
    val current: Int,
    val maximum: Int,
    val after: Int,
)

data class InventoryStack(
    val item: ItemDefinition,
    val quantity: Int,
    val worn: Boolean,
    val use: InventoryUse?,
    val equipBlock: ActionRequirement?,
) {
    val available: Int get() = quantity - if (worn) 1 else 0
}

data class InventoryView(
    val stacks: List<InventoryStack>,
    val equipped: Map<EquipmentSlot, String>,
    val equipmentUnlocked: Boolean,
    val stats: EquipmentStats,
)

internal class Inventory(private val content: GameContent) {
    private val items = content.items.associateBy(ItemDefinition::id)
    private val arts = content.martialArts.associateBy { it.id }
    private val staff = content.openingTraining?.staff

    fun equipmentUnlocked(state: GameState) =
        staff?.preparation(state)?.unlocked ?: true

    fun validState(state: GameState): Boolean =
        state.inventory.all { (id, count) -> id in items && count > 0 } &&
            state.equipped.all { (slot, id) ->
                val gear = items[id]?.equipment
                gear?.slot == slot && equipBlock(state, gear) == null && (state.inventory[id] ?: 0) > 0
            }

    fun view(state: GameState): InventoryView {
        val stacks = content.items.mapNotNull { item ->
            val quantity = state.inventory[item.id] ?: return@mapNotNull null
            InventoryStack(
                item, quantity, item.equipment?.let { state.equipped[it.slot] } == item.id, use(state, item),
                item.equipment?.let { equipBlock(state, it) },
            )
        }
        return InventoryView(stacks, state.equipped, equipmentUnlocked(state), stats(state))
    }

    fun stats(state: GameState): EquipmentStats =
        state.equipped.values.fold(EquipmentStats()) { total, id -> total + gear(id).stats }

    fun weapon(state: GameState): EquipmentDefinition? = state.equipped[EquipmentSlot.WEAPON]?.let(::gear)

    fun equip(state: GameState, itemId: String, changed: (GameState) -> ActionResult): ActionResult {
        val gear = items[itemId]?.equipment ?: return ActionResult.Blocked(ActionRequirement.ItemNotEquipment)
        if ((state.inventory[itemId] ?: 0) == 0) return ActionResult.Blocked(ActionRequirement.ItemMissing)
        equipBlock(state, gear)?.let { return ActionResult.Blocked(it) }
        if (state.equipped[gear.slot] == itemId) return ActionResult.Blocked(ActionRequirement.EquipmentChanged)
        return changed(state.copy(equipped = state.equipped + (gear.slot to itemId)))
    }

    fun unequip(
        state: GameState, slot: EquipmentSlot, expectedItemId: String, changed: (GameState) -> ActionResult,
    ): ActionResult {
        if (!equipmentUnlocked(state)) return ActionResult.Blocked(ActionRequirement.EquipmentLocked)
        if (state.equipped[slot] != expectedItemId) return ActionResult.Blocked(ActionRequirement.EquipmentChanged)
        return changed(state.copy(equipped = state.equipped - slot))
    }

    fun heal(state: GameState, itemId: String, changed: (GameState) -> ActionResult): ActionResult {
        val use = items[itemId]?.let { use(state, it) }?.takeIf { it.effect == InventoryEffect.HEAL }
            ?: return ActionResult.Blocked(ActionRequirement.ItemNotHealing)
        if (itemId !in state.inventory) return ActionResult.Blocked(ActionRequirement.ItemMissing)
        if (state.character.health == 0 || use.after == use.current) {
            return ActionResult.Blocked(ActionRequirement.HealingUnavailable)
        }
        return changed(state.consumeOne(itemId).copy(character = state.character.copy(health = use.after)))
    }

    private fun gear(itemId: String) = checkNotNull(items.getValue(itemId).equipment)

    private fun use(state: GameState, item: ItemDefinition): InventoryUse? {
        if (item.id == content.supplies.foodItemId) {
            val amount = content.supplies.hungerReduction
            return InventoryUse(InventoryEffect.FOOD, amount, state.hunger, content.time.maxHunger,
                (state.hunger - amount).coerceAtLeast(0))
        }
        val recover = item.healthRecovery ?: return null
        val health = state.character.health
        val limit = state.character.effectiveMaxHealth
        val after = if (health > 0) health + recover.coerceAtMost(limit - health) else 0
        return InventoryUse(InventoryEffect.HEAL, recover, health, limit, after)
    }

    private fun equipBlock(state: GameState, gear: EquipmentDefinition): ActionRequirement? {
        if (!equipmentUnlocked(state)) return ActionRequirement.EquipmentLocked
        gear.requiredAttributes.entries.firstOrNull { state.character.attributes[it.key] < it.value }?.let {
            return ActionRequirement.EquipmentAttributeRequired(it.key, it.value, state.character.attributes[it.key])
        }
        gear.requiredArt?.let { required ->
            val current = state.martialTraining.levelOf(required.martialArtId)
            if (current < required.level) {
                return ActionRequirement.EquipmentArtRequired(arts.getValue(required.martialArtId).name, required.level, current)
            }
        }
        return null
    }
}

internal fun GameState.consumeOne(itemId: String): GameState {
    val count = inventory.getValue(itemId)
    return copy(inventory = if (count == 1) inventory - itemId else inventory + (itemId to count - 1))
}
