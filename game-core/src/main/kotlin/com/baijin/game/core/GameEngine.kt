package com.baijin.game.core

import com.baijin.game.core.content.GameContent
import com.baijin.game.core.content.GameTimeDefinition
import com.baijin.game.core.content.ItemDefinition
import com.baijin.game.core.content.LocationDefinition
import com.baijin.game.core.content.NpcService
import com.baijin.game.core.content.SceneDefinition
import com.baijin.game.core.content.SupplyDefinition
import java.util.UUID
import kotlin.random.Random
import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val contentVersion: Int,
    val position: WorldPosition,
    val facing: Direction,
    val character: CharacterState,
    val gameTime: Long,
    val hunger: Int,
    val thirst: Int,
    val coins: Int,
    val inventory: Map<String, Int> = emptyMap(),
    val potential: Int = 0,
    val experience: Int = 0,
    val affinities: Map<String, Int> = emptyMap(),
    val questRounds: Map<String, QuestRound> = emptyMap(),
    val martialTraining: MartialTrainingState = MartialTrainingState(),
    val lastSparring: SparringReceipt? = null,
    val equipped: Map<EquipmentSlot, String> = emptyMap(),
)

sealed interface GameAction {
    data class Start(val gender: Gender, val attributes: Attributes) : GameAction
    data class Move(val direction: Direction) : GameAction
    data object Meditate : GameAction
    data object InspectLocation : GameAction
    data class BuyFood(val npcId: String) : GameAction
    data class BuyArmour(val npcId: String, val itemId: String) : GameAction
    data class Eat(val itemId: String) : GameAction
    data class UseMedicine(val itemId: String) : GameAction
    data class Equip(val itemId: String) : GameAction
    data class Unequip(val slot: EquipmentSlot, val itemId: String) : GameAction
    data object Drink : GameAction
    data class TalkToNpc(val npcId: String, val seed: Long = Random.nextLong()) : GameAction
    data class AcceptQuest(val questId: String, val objectiveId: String, val previousRound: Int) : GameAction
    data class CompleteQuestObjective(val questId: String, val objectiveId: String, val round: Int) : GameAction
    data class TurnInQuest(val questId: String, val round: Int) : GameAction
    data class JoinSect(val npcId: String, val sectId: String) : GameAction
    data class LearnMartialArt(val npcId: String, val artId: String, val targetLevel: Int) : GameAction
    data class BeginSparring(val npcId: String, val seed: Long = Random.nextLong()) : GameAction
    data class AttackSparring(val boutId: String, val roundNumber: Int) : GameAction
    data class ClaimSparringLoot(val boutId: String) : GameAction
    data class ReadBeforeSparring(val boutId: String) : GameAction
}

data class Exploration(
    val scene: SceneDefinition,
    val locations: List<LocationDefinition>,
    val currentLocation: LocationDefinition?,
    val locationName: String,
    val appearanceDescription: String,
    val timeRules: GameTimeDefinition,
    val supplies: SupplyDefinition,
    val items: List<ItemDefinition>,
    val wellHere: Boolean,
    val dialogue: NpcDialogue?,
    val npcMarkers: List<NpcMarker>,
    val quests: List<QuestView>,
    val martialTraining: MartialTrainingView,
    val inventory: InventoryView,
    val armourOffers: List<ArmourOffer> = emptyList(),
    val sectPreparation: SectPreparation? = null,
)

data class ArmourOffer(val item: ItemDefinition, val price: Int)

sealed interface ActionResult {
    data class Changed(
        val state: GameState,
        val exploration: Exploration,
        val sparring: SavedSparring? = null,
        val completedSparringRound: SparringRound? = null,
    ) : ActionResult
    data class Display(
        val state: GameState,
        val exploration: Exploration,
        val sparring: SavedSparring? = null,
    ) : ActionResult
    data class Blocked(val requirement: ActionRequirement) : ActionResult
}

sealed interface ActionRequirement {
    data object StartRequired : ActionRequirement
    data class ContentVersionMismatch(val expected: Int, val actual: Int) : ActionRequirement
    data class UnknownLocation(val locationId: String) : ActionRequirement
    data class UnknownScene(val sceneId: String) : ActionRequirement
    data object InvalidPosition : ActionRequirement
    data object InvalidCharacterState : ActionRequirement
    data class AttributeOutOfRange(val min: Int, val max: Int) : ActionRequirement
    data object AttributeBudgetMismatch : ActionRequirement
    data object PathBlocked : ActionRequirement
    data object ExitUnavailable : ActionRequirement
    data object TimeLimitReached : ActionRequirement
    data object InvalidInventory : ActionRequirement
    data object EquipmentLocked : ActionRequirement
    data object EquipmentChanged : ActionRequirement
    data object ItemNotEquipment : ActionRequirement
    data object ItemMissing : ActionRequirement
    data object ItemNotHealing : ActionRequirement
    data object HealingUnavailable : ActionRequirement
    data class EquipmentAttributeRequired(val attribute: Attribute, val level: Int, val current: Int) : ActionRequirement
    data class EquipmentArtRequired(val name: String, val level: Int, val current: Int) : ActionRequirement
    data object SellerUnavailable : ActionRequirement
    data object SellerTooFar : ActionRequirement
    data object ArmourUnavailable : ActionRequirement
    data object ArmourShopTooFar : ActionRequirement
    data class CoinsMissing(val amount: Int) : ActionRequirement
    data object InventoryFull : ActionRequirement
    data object ItemNotEdible : ActionRequirement
    data object FoodMissing : ActionRequirement
    data object NotHungry : ActionRequirement
    data object WellTooFar : ActionRequirement
    data object NotThirsty : ActionRequirement
    data object InvalidQuestState : ActionRequirement
    data object QuestUnavailable : ActionRequirement
    data object QuestLocation : ActionRequirement
    data object QuestRoundChanged : ActionRequirement
    data object QuestAlreadyActive : ActionRequirement
    data object QuestAlreadyTurnedIn : ActionRequirement
    data class HouseworkRetired(val experience: Int) : ActionRequirement
    data object RewardLimit : ActionRequirement
    data object InvalidMartialTraining : ActionRequirement
    data object TrainingUnavailable : ActionRequirement
    data object TrainingLocation : ActionRequirement
    data class AlreadyInSect(val name: String) : ActionRequirement
    data object SectRequired : ActionRequirement
    data class MartialLevelRequired(val name: String, val level: Int) : ActionRequirement
    data class PotentialMissing(val amount: Int) : ActionRequirement
    data object MartialLevelChanged : ActionRequirement
    data object OpeningTrainingComplete : ActionRequirement
    data object SparringUnavailable : ActionRequirement
    data object SparringChanged : ActionRequirement
    data object InvalidSparring : ActionRequirement
}

class GameEngine(
    private val content: GameContent,
    private val drawLuck: (IntRange) -> Int = { it.random() },
) {
    private val locations = content.locations.associateBy(LocationDefinition::id)
    private val locationsByScene = content.locations.groupBy(LocationDefinition::sceneId)
    private val scenes = content.scenes.associateBy(SceneDefinition::id)
    private val exits = content.scenes.flatMap { scene -> scene.exits.map { it.id to (scene to it) } }.toMap()
    val creation get() = content.creation
    val contentVersion get() = content.contentVersion
    private val time get() = content.time
    private val supplies get() = content.supplies
    private val items = content.items.associateBy(ItemDefinition::id)
    private val itemIds = items.keys
    private val staff = content.openingTraining?.staff
    private val armourShop = content.openingArmourShop
    private val armourOffers = armourShop?.stock.orEmpty().map { ArmourOffer(items.getValue(it.itemId), it.price) }
    private val martialTraining = MartialTraining(content)
    private val quests = StarterQuests(content, martialTraining)
    private val inventory = Inventory(content)

    private fun at(state: GameState, locationId: String): Boolean = locations[locationId]?.let {
        state.position == WorldPosition(it.sceneId, it.tile)
    } == true

    fun startSparring(state: GameState?, npcId: String, seed: Long = Random.nextLong()): SparringStart {
        val inspection = when (val result = dispatch(state, GameAction.InspectLocation)) {
            is ActionResult.Display -> result
            is ActionResult.Blocked -> return SparringStart.InvalidExploration(result.requirement)
            is ActionResult.Changed -> error("Inspecting must preserve the exploration state")
        }
        val definition = content.openingSparring?.takeIf { it.npcId == npcId }
            ?: return SparringStart.Unavailable
        val npc = inspection.exploration.dialogue?.npc
        if (npc?.id != npcId || NpcService.SPAR !in npc.services) return SparringStart.WrongLocation
        val character = inspection.state.character
        if (character.health == 0) return SparringStart.NoHealth
        val training = inspection.exploration.martialTraining
        val weapon = inventory.weapon(inspection.state)
        val equipmentStats = inspection.exploration.inventory.stats
        val opponent = definition.opponent
        return SparringStart.Started(SparringState(
            encounterId = definition.id,
            opponentId = npcId,
            opponentName = npc.name,
            explorationAtStart = inspection.state,
            player = SparringFighter(
                strength = character.attributes.strength,
                agility = character.attributes.agility,
                luck = character.luck,
                health = character.health,
                maxHealth = character.maxHealth,
                effectiveMaxHealth = character.effectiveMaxHealth,
                innerPower = character.innerPower,
                maxInnerPower = character.maxInnerPower,
                attack = weapon?.let { martialTraining.weaponAttack(inspection.state, it) } ?: training.unarmedAttack,
                defense = definition.playerDefense.copy(
                    protection = (definition.playerDefense.protection.toLong() + equipmentStats.protection)
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    baseParryPercent = definition.playerDefense.baseParryPercent + equipmentStats.parry,
                    evasionBonus = definition.playerDefense.evasionBonus + equipmentStats.evasion,
                ),
                attackLines = weapon?.let { martialTraining.weaponAttackLines(it.weaponType) }
                    ?: training.appliedUnarmedArt?.openingAttackLines.orEmpty(),
            ),
            opponent = SparringFighter(
                strength = opponent.strength,
                agility = opponent.agility,
                luck = npc.luck,
                health = opponent.maxHealth,
                maxHealth = opponent.maxHealth,
                effectiveMaxHealth = opponent.maxHealth,
                innerPower = opponent.maxInnerPower,
                maxInnerPower = opponent.maxInnerPower,
                attack = unarmedAttack(opponent.strength),
                defense = opponent.defense,
            ),
            damageVariance = definition.damageVariance,
            nextRoundSeed = seed,
        ))
    }

    fun dispatch(state: GameState?, action: GameAction, sparring: SavedSparring? = null): ActionResult {
        if (action is GameAction.Start && sparring == null) return start(action)
        if (state == null) return ActionResult.Blocked(ActionRequirement.StartRequired)
        versionMismatch(state.contentVersion)?.let { return it }
        val scene = scenes[state.position.sceneId]
            ?: return ActionResult.Blocked(ActionRequirement.UnknownScene(state.position.sceneId))
        if (!scene.isWalkable(state.position.tile)) return ActionResult.Blocked(ActionRequirement.InvalidPosition)
        if (!hasValidCharacterState(state)) return ActionResult.Blocked(ActionRequirement.InvalidCharacterState)
        if (!quests.validState(state)) return ActionResult.Blocked(ActionRequirement.InvalidQuestState)
        if (!martialTraining.validState(state)) {
            return ActionResult.Blocked(ActionRequirement.InvalidMartialTraining)
        }
        if (!inventory.validState(state)) {
            return ActionResult.Blocked(ActionRequirement.InvalidInventory)
        }
        if (sparring != null) return playSparring(state, scene, sparring, action)

        return when (action) {
            GameAction.InspectLocation -> ActionResult.Display(state, describe(state, scene))
            is GameAction.Move -> move(state, scene, action.direction)
            GameAction.Meditate -> advance(state, scene, time.meditationDuration, meditating = true)
            is GameAction.BuyFood -> buyFood(state, scene, action.npcId)
            is GameAction.BuyArmour -> buyArmour(state, scene, action)
            is GameAction.Eat -> eat(state, scene, action.itemId)
            is GameAction.UseMedicine -> inventory.heal(state, action.itemId) { ActionResult.Changed(it, describe(it, scene)) }
            is GameAction.Equip -> inventory.equip(state, action.itemId) { ActionResult.Changed(it, describe(it, scene)) }
            is GameAction.Unequip -> inventory.unequip(state, action.slot, action.itemId) {
                ActionResult.Changed(it, describe(it, scene))
            }
            GameAction.Drink -> drink(state, scene)
            is GameAction.TalkToNpc -> {
                if (quests.npcAt(state)?.npc?.id != action.npcId) {
                    ActionResult.Blocked(ActionRequirement.QuestLocation)
                } else {
                    val next = quests.assignOnDialogue(state, action.npcId, action.seed)
                    if (next == state) ActionResult.Display(state, describe(state, scene))
                    else ActionResult.Changed(next, describe(next, scene))
                }
            }
            is GameAction.AcceptQuest -> questResult(quests.accept(state, action), scene)
            is GameAction.CompleteQuestObjective -> questResult(quests.complete(state, action), scene)
            is GameAction.TurnInQuest -> questResult(quests.turnIn(state, action), scene)
            is GameAction.JoinSect -> withTrainer(state, action.npcId) {
                martialTraining.join(state, action.sectId) { next -> ActionResult.Changed(next, describe(next, scene)) }
            }
            is GameAction.LearnMartialArt -> withTrainer(state, action.npcId) {
                martialTraining.learn(state, action.artId, action.targetLevel) { next ->
                    ActionResult.Changed(next, describe(next, scene))
                }
            }
            is GameAction.BeginSparring -> when (val start = startSparring(state, action.npcId, action.seed)) {
                is SparringStart.Started -> ActionResult.Changed(
                    state, describe(state, scene), SavedSparring(UUID.randomUUID().toString(), start.state),
                )
                is SparringStart.InvalidExploration -> ActionResult.Blocked(start.requirement)
                else -> ActionResult.Blocked(ActionRequirement.SparringUnavailable)
            }
            is GameAction.AttackSparring, is GameAction.ClaimSparringLoot, is GameAction.ReadBeforeSparring ->
                ActionResult.Blocked(ActionRequirement.SparringChanged)
            is GameAction.Start -> error("Start is handled before inspecting an existing character")
        }
    }

    private fun playSparring(
        state: GameState,
        scene: SceneDefinition,
        battle: SavedSparring,
        action: GameAction,
    ): ActionResult {
        val fight = battle.fight
        val definition = content.openingSparring
        val npc = quests.npcAt(state)?.npc
        if (definition?.id != fight.encounterId || definition.npcId != fight.opponentId || npc?.id != fight.opponentId) {
            return ActionResult.Blocked(ActionRequirement.InvalidSparring)
        }
        fun changed(next: GameState, sparring: SavedSparring? = null) =
            ActionResult.Changed(next, describe(next, scene), sparring)

        val stale = ActionResult.Blocked(ActionRequirement.SparringChanged)
        return when (action) {
            GameAction.InspectLocation -> {
                if (!validSparring(state, battle)) return ActionResult.Blocked(ActionRequirement.InvalidSparring)
                ActionResult.Display(state, describe(state, scene), battle)
            }
            is GameAction.AttackSparring -> {
                if (action.boutId != battle.boutId || battle.result != null) return stale
                val next = fight.attack(action.roundNumber) ?: return stale
                val after = state.afterRound(next.player)
                val settled = when (next.outcome) {
                    SparringOutcome.IN_PROGRESS -> changed(after, battle.copy(fight = next))
                    SparringOutcome.VICTORY -> {
                        val loot = definition.drawLoot(after, Random(next.nextRoundSeed)::nextLong)
                            ?: return ActionResult.Blocked(ActionRequirement.InventoryFull)
                        changed(after, battle.copy(
                            fight = next.copy(explorationAtStart = null),
                            result = SparringResult.Victory(loot),
                        ))
                    }
                    SparringOutcome.DEFEAT -> {
                        val affinity = after.affinities[npc.id] ?: npc.initialAffinity
                        if (npc.morality + affinity < 0) {
                            changed(after, battle.copy(fight = next, result = SparringResult.Death))
                        } else {
                            changed(after.afterRound(next.player, health = 1).copy(
                                lastSparring = SparringReceipt(battle.boutId, "${npc.name}收手，放过了你。"),
                            ))
                        }
                    }
                }
                settled.copy(completedSparringRound = next.rounds.last())
            }
            is GameAction.ClaimSparringLoot -> {
                val victory = battle.result as? SparringResult.Victory ?: return stale
                if (action.boutId != battle.boutId) return stale
                val inventory = state.inventory.toMutableMap()
                for ((id, quantity) in victory.loot) {
                    val count = (inventory[id] ?: 0).toLong() + quantity
                    if (count > Int.MAX_VALUE) return ActionResult.Blocked(ActionRequirement.InventoryFull)
                    inventory[id] = count.toInt()
                }
                changed(state.copy(inventory = inventory, lastSparring = SparringReceipt(battle.boutId)))
            }
            is GameAction.ReadBeforeSparring -> {
                if (action.boutId != battle.boutId || battle.result != SparringResult.Death) return stale
                val before = fight.explorationAtStart ?: return ActionResult.Blocked(ActionRequirement.InvalidSparring)
                changed(before)
            }
            else -> stale
        }
    }

    private fun validSparring(state: GameState, battle: SavedSparring): Boolean {
        val fight = battle.fight
        if (battle.boutId.isBlank() || battle.boutId == state.lastSparring?.boutId) return false
        if (fight.opponentName.isBlank() || fight.damageVariance < 0) return false
        if (state != state.afterRound(fight.player)) return false
        if (listOf(fight.player, fight.opponent).any {
                it.effectiveMaxHealth !in 1..it.maxHealth || it.health !in 0..it.effectiveMaxHealth ||
                    it.maxInnerPower <= 0 || it.innerPower !in 0..it.maxInnerPower || it.luck !in 1..100
            }) return false
        if (fight.player.maxHealth != state.character.maxHealth || fight.player.maxInnerPower != state.character.maxInnerPower ||
            (fight.player.health == 0 && fight.opponent.health == 0)) return false
        if (fight.rounds.withIndex().any { (index, round) -> round.number != index + 1 || round.strikes.isEmpty() }) return false
        val before = fight.explorationAtStart
        if (before != null && (before.character.health <= 0 || !hasValidCharacterState(before) ||
                before.afterRound(fight.player) != state)) return false
        return when (val result = battle.result) {
            null -> before != null && fight.outcome == SparringOutcome.IN_PROGRESS
            is SparringResult.Victory -> before == null && fight.rounds.isNotEmpty() && fight.player.health > 0 &&
                fight.outcome == SparringOutcome.VICTORY && result.loot.all { (id, quantity) -> id in itemIds && quantity > 0 }
            SparringResult.Death -> before != null && fight.rounds.isNotEmpty() && fight.outcome == SparringOutcome.DEFEAT
        }
    }

    private fun questResult(change: QuestChange, scene: SceneDefinition): ActionResult = when (change) {
        is QuestChange.Changed -> ActionResult.Changed(change.state, describe(change.state, scene))
        is QuestChange.Blocked -> ActionResult.Blocked(change.requirement)
    }

    private fun withTrainer(state: GameState, npcId: String, action: () -> ActionResult): ActionResult {
        val npc = quests.npcAt(state)?.npc
        if (npc == null || npc.id != npcId || NpcService.TRAINING !in npc.services) {
            return ActionResult.Blocked(ActionRequirement.TrainingLocation)
        }
        return action()
    }

    private fun buyFood(state: GameState, scene: SceneDefinition, npcId: String): ActionResult {
        val seller = content.npcs.firstOrNull { it.id == npcId && it.id == supplies.sellerNpcId }
            ?: return ActionResult.Blocked(ActionRequirement.SellerUnavailable)
        if (!at(state, seller.locationId)) return ActionResult.Blocked(ActionRequirement.SellerTooFar)
        return buyOne(state, scene, supplies.foodItemId, supplies.foodPrice)
    }

    private fun buyArmour(state: GameState, scene: SceneDefinition, action: GameAction.BuyArmour): ActionResult {
        val offer = armourOffers.firstOrNull { it.item.id == action.itemId }
        if (armourShop?.npcId != action.npcId || offer == null) {
            return ActionResult.Blocked(ActionRequirement.ArmourUnavailable)
        }
        if (quests.npcAt(state)?.npc?.id != action.npcId) {
            return ActionResult.Blocked(ActionRequirement.ArmourShopTooFar)
        }
        if (staff?.preparation(state)?.unlocked == false) return ActionResult.Blocked(ActionRequirement.EquipmentLocked)
        return buyOne(state, scene, action.itemId, offer.price)
    }

    private fun buyOne(state: GameState, scene: SceneDefinition, itemId: String, price: Int): ActionResult {
        if (state.coins < price) return ActionResult.Blocked(ActionRequirement.CoinsMissing(price - state.coins))
        val count = state.inventory[itemId] ?: 0
        if (count == Int.MAX_VALUE) return ActionResult.Blocked(ActionRequirement.InventoryFull)
        val next = state.copy(coins = state.coins - price, inventory = state.inventory + (itemId to count + 1))
        return ActionResult.Changed(next, describe(next, scene))
    }

    private fun eat(state: GameState, scene: SceneDefinition, itemId: String): ActionResult {
        if (itemId != supplies.foodItemId) return ActionResult.Blocked(ActionRequirement.ItemNotEdible)
        val count = state.inventory[itemId] ?: 0
        if (count == 0) return ActionResult.Blocked(ActionRequirement.FoodMissing)
        if (state.hunger == 0) return ActionResult.Blocked(ActionRequirement.NotHungry)
        val next = state.consumeOne(itemId).copy(hunger = (state.hunger - supplies.hungerReduction).coerceAtLeast(0))
        return ActionResult.Changed(next, describe(next, scene))
    }

    private fun drink(state: GameState, scene: SceneDefinition): ActionResult {
        if (!at(state, supplies.wellLocationId)) return ActionResult.Blocked(ActionRequirement.WellTooFar)
        if (state.thirst == 0) return ActionResult.Blocked(ActionRequirement.NotThirsty)
        val next = state.copy(thirst = (state.thirst - supplies.thirstReduction).coerceAtLeast(0))
        return ActionResult.Changed(next, describe(next, scene))
    }

    private fun start(action: GameAction.Start): ActionResult {
        if (Attribute.entries.any { action.attributes[it] !in creation.attributeRange }) {
            return ActionResult.Blocked(ActionRequirement.AttributeOutOfRange(creation.minAttribute, creation.maxAttribute))
        }
        if (creation.remainingPoints(action.attributes) != 0L) {
            return ActionResult.Blocked(ActionRequirement.AttributeBudgetMismatch)
        }
        val location = locations[content.startLocationId]
            ?: return ActionResult.Blocked(ActionRequirement.UnknownLocation(content.startLocationId))
        val scene = scenes.getValue(location.sceneId)
        val range = 1..creation.maxLuckDraw
        val draw = drawLuck(range)
        check(draw in range) { "The luck draw must be in $range" }
        val state = openingState(location, action.gender, action.attributes, draw)
        return ActionResult.Changed(state, describe(state, scene))
    }

    fun restoreLegacyLocation(contentVersion: Int, locationId: String): ActionResult {
        if (contentVersion != 1 && !isBeforeHomeRenovation(contentVersion)) {
            versionMismatch(contentVersion)?.let { return it }
        }
        val location = locations[locationId]
            ?: return ActionResult.Blocked(ActionRequirement.UnknownLocation(locationId))
        val state = openingState(location, Gender.MALE, creation.balancedAttributes, minOf(44, creation.maxLuckDraw))
        return dispatch(state, GameAction.InspectLocation)
    }

    fun restoreVersionOne(state: GameState): ActionResult {
        if (state.contentVersion != 1) return restoreSavedGame(state)
        if (!LegacyWorldLayout.valid(state.position)) return ActionResult.Blocked(ActionRequirement.InvalidPosition)
        val id = if (state.position.sceneId == "town" && state.position.tile == Tile(4, 7)) {
            "granny_service"
        } else LegacyWorldLayout.destination(state.position)
        val destination = locations.getValue(id)
        val migrated = state.copy(contentVersion = content.contentVersion,
            position = WorldPosition(destination.sceneId, destination.tile))
        return inspectRestoredGame(migrated, null)
    }

    fun restoreSavedGame(state: GameState, battle: SavedSparring? = null): ActionResult {
        if (!isBeforeHomeRenovation(state.contentVersion)) return inspectRestoredGame(state, battle)
        val position = state.position
        val home = scenes[position.sceneId]?.takeIf { it.id == "player_home" }
        if (home != null && !LegacyWorldLayout.validHomeInVersionTwo(position.tile)) {
            return ActionResult.Blocked(ActionRequirement.InvalidPosition)
        }
        val destination = if (home != null && !home.isWalkable(position.tile)) {
            WorldPosition(home.id, locations.getValue("player_home_start").tile)
        } else position
        val beforeBattle = battle?.fight?.explorationAtStart
        if (beforeBattle != null && beforeBattle.contentVersion != state.contentVersion) {
            return ActionResult.Blocked(ActionRequirement.InvalidSparring)
        }
        val migratedBattle = battle?.copy(fight = battle.fight.copy(
            explorationAtStart = beforeBattle?.copy(contentVersion = contentVersion),
        ))
        return inspectRestoredGame(state.copy(contentVersion = contentVersion, position = destination), migratedBattle)
    }

    private fun inspectRestoredGame(state: GameState, battle: SavedSparring?): ActionResult {
        val inspected = dispatch(state, GameAction.InspectLocation, battle)
        if (inspected !is ActionResult.Display) return inspected
        val settled = quests.settlePreviouslyCompletedObjectives(state)
        if (settled == state) return inspected
        val settledBattle = battle?.copy(fight = battle.fight.copy(
            explorationAtStart = battle.fight.explorationAtStart?.let(quests::settlePreviouslyCompletedObjectives),
        ))
        return ActionResult.Display(settled, describe(settled, inspected.exploration.scene), settledBattle)
    }

    private fun isBeforeHomeRenovation(version: Int): Boolean = version == 2 && contentVersion == 3

    private fun openingState(location: LocationDefinition, gender: Gender, attributes: Attributes, draw: Int): GameState {
        val min = creation.minAttribute
        val luck = draw + (attributes.insight - min) + (attributes.constitution - min)
        val weights = content.appearance.weights
        val appearanceScore = (attributes.strength - min).toLong() * weights.strength +
            (attributes.agility - min).toLong() * weights.agility +
            (attributes.insight - min).toLong() * weights.insight +
            (attributes.constitution - min).toLong() * weights.constitution + luck / 2
        val maxHealth = creation.healthBase + creation.healthPerConstitution * attributes.constitution
        val maxInnerPower = creation.innerPowerBase + creation.innerPowerPerConstitution * attributes.constitution
        val character = CharacterState(
            gender, attributes, luck, content.appearance.tierFor(appearanceScore),
            maxHealth, maxHealth, maxHealth, maxInnerPower, maxInnerPower,
        )
        return GameState(
            content.contentVersion, WorldPosition(location.sceneId, location.tile), Direction.SOUTH, character,
            gameTime = 0, hunger = 0, thirst = 0, coins = creation.initialCoins,
        )
    }

    private fun move(state: GameState, scene: SceneDefinition, direction: Direction): ActionResult {
        val tile = state.position.tile
        val next = tile.step(direction)
        val exit = scene.exits.firstOrNull { !it.onEntry && it.tile == tile && it.direction == direction }
            ?: scene.exits.firstOrNull { it.onEntry && it.tile == next }
        if (exit != null) {
            val (destinationScene, destinationExit) = exit.destinationExitId?.let(exits::get)
                ?: return ActionResult.Blocked(ActionRequirement.ExitUnavailable)
            val moved = state.copy(
                position = WorldPosition(destinationScene.id, destinationExit.returnTile),
                facing = destinationExit.direction.opposite,
            )
            return advance(moved, destinationScene, 1, meditating = false)
        }
        if (!scene.isWalkable(next)) return ActionResult.Blocked(ActionRequirement.PathBlocked)
        val moved = state.copy(position = state.position.copy(tile = next), facing = direction)
        return advance(moved, scene, 1, meditating = false)
    }

    private fun advance(state: GameState, scene: SceneDefinition, units: Int, meditating: Boolean): ActionResult {
        if (state.gameTime > Long.MAX_VALUE - units) return ActionResult.Blocked(ActionRequirement.TimeLimitReached)

        var clock = state.gameTime
        var hunger = state.hunger
        var thirst = state.thirst
        val character = state.character
        var health = character.health
        var innerPower = character.innerPower
        repeat(units) {
            clock++
            if (clock % time.hungerInterval == 0L && hunger < time.maxHunger) hunger++
            if (clock % time.thirstInterval == 0L && thirst < time.maxThirst) thirst++
            if (hunger < time.maxHunger && thirst < time.maxThirst) {
                if (clock % time.healthRecoveryInterval == 0L) {
                    health += minOf(time.healthRecovery, character.effectiveMaxHealth - health)
                }
                val recovery = when {
                    meditating -> time.meditationRecovery
                    clock % time.innerPowerRecoveryInterval == 0L -> time.innerPowerRecovery
                    else -> 0
                }
                innerPower += minOf(recovery, character.maxInnerPower - innerPower)
            }
        }
        val next = state.copy(
            gameTime = clock,
            hunger = hunger,
            thirst = thirst,
            character = character.copy(health = health, innerPower = innerPower),
        )
        return ActionResult.Changed(next, describe(next, scene))
    }

    private fun describe(state: GameState, scene: SceneDefinition): Exploration {
        val landmarks = locationsByScene[scene.id].orEmpty()
        val here = landmarks.firstOrNull { it.tile == state.position.tile }
        val exit = scene.exits.firstOrNull { it.tile == state.position.tile }
        val description = content.appearance.descriptions[state.character.appearanceTier - 1]
        val appearance = when (state.character.gender) {
            Gender.MALE -> description.male
            Gender.FEMALE -> description.female
        }
        val dialogue = quests.npcAt(state)
        return Exploration(scene, landmarks, here, here?.name ?: exit?.name ?: scene.name, appearance, time,
            supplies, content.items, at(state, supplies.wellLocationId), dialogue,
            quests.markers(scene.id), quests.views(state), martialTraining.view(state), inventory.view(state),
            if (dialogue?.npc?.id == armourShop?.npcId) armourOffers else emptyList(),
            staff?.preparation(state))
    }

    private fun versionMismatch(actual: Int): ActionResult.Blocked? =
        if (actual == content.contentVersion) null
        else ActionResult.Blocked(ActionRequirement.ContentVersionMismatch(content.contentVersion, actual))

    private fun hasValidCharacterState(state: GameState): Boolean {
        val character = state.character
        return state.gameTime >= 0 && state.coins >= 0 &&
            state.hunger in 0..time.maxHunger && state.thirst in 0..time.maxThirst &&
            Attribute.entries.all { character.attributes[it] > 0 } &&
            character.luck in 1..100 && character.appearanceTier in 1..content.appearance.descriptions.size &&
            character.effectiveMaxHealth in 1..character.maxHealth && character.health in 0..character.effectiveMaxHealth &&
            character.maxInnerPower > 0 && character.innerPower in 0..character.maxInnerPower
    }
}
