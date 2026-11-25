package com.baijin.game

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.baijin.game.core.ActionRequirement
import com.baijin.game.core.ActionResult
import com.baijin.game.core.Attribute
import com.baijin.game.core.Attributes
import com.baijin.game.core.Direction
import com.baijin.game.core.GameAction
import com.baijin.game.core.GameEngine
import com.baijin.game.core.Gender
import com.baijin.game.core.QuestStage
import com.baijin.game.core.SparringRound
import com.baijin.game.core.content.CharacterCreationDefinition
import com.baijin.game.core.content.ContentIssue
import com.baijin.game.core.content.ContentLoadResult
import com.baijin.game.core.content.ContentLoader
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

internal class GameSession(private val application: Application) {
    sealed interface Screen {
        data object Loading : Screen
        data class ContentInvalid(val issues: List<ContentIssue>) : Screen
        data class Menu(val save: SaveSlot.ReadResult) : Screen

        sealed interface Active : Screen

        data class Creating(
            val previousSave: SaveSlot.ReadResult,
            val rules: CharacterCreationDefinition,
            val attributes: Attributes = rules.baseAttributes,
            val gender: Gender? = null,
            val notice: String? = null,
        ) : Active

        data class Exploring(val game: SaveSlot.ReadResult.Available, val notice: String? = null) : Active
        data class Sparring(val game: SaveSlot.ReadResult.Available, val notice: String? = null) : Active

        data class PlayingSparring(val previous: Sparring, val round: SparringRound, val next: Active) : Screen
        data class Saving(val previous: Active) : Screen
        data class SaveFailed(
            val previous: Active,
            val pending: ActionResult.Changed,
            val noticeAfterSave: String?,
        ) : Screen
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val saveSlot by lazy { SaveSlot(File(application.filesDir, "save.json")) }
    private val observers = mutableSetOf<(Screen) -> Unit>()
    private lateinit var engine: GameEngine

    var screen: Screen = Screen.Loading
        private set

    init {
        worker.execute {
            when (val result = loadSampleContent()) {
                is ContentLoadResult.Loaded -> {
                    val loadedEngine = GameEngine(result.content)
                    val save = saveSlot.read(loadedEngine)
                    main.post {
                        engine = loadedEngine
                        show(Screen.Menu(save))
                    }
                }
                is ContentLoadResult.Invalid -> main.post { show(Screen.ContentInvalid(result.issues)) }
            }
        }
    }

    fun observe(observer: (Screen) -> Unit) {
        observers.add(observer)
        observer(screen)
    }

    fun removeObserver(observer: (Screen) -> Unit) {
        observers.remove(observer)
    }

    fun startNewGame(confirmedSave: SaveSlot.ReadResult? = null) {
        val menu = screen as? Screen.Menu ?: return
        if (menu.save !== SaveSlot.ReadResult.Empty && menu.save !== confirmedSave) return
        show(Screen.Creating(menu.save, engine.creation))
    }

    fun chooseGender(gender: Gender) {
        val creation = screen as? Screen.Creating ?: return
        show(creation.copy(gender = gender, notice = null))
    }

    fun adjustAttribute(attribute: Attribute, delta: Int) {
        val creation = screen as? Screen.Creating ?: return
        val value = creation.attributes[attribute].toLong() + delta
        if (value !in creation.rules.minAttribute.toLong()..creation.rules.maxAttribute.toLong()) return
        val attributes = creation.attributes.withValue(attribute, value.toInt())
        if (creation.rules.remainingPoints(attributes) < 0) return
        show(creation.copy(attributes = attributes, notice = null))
    }

    fun balanceAttributes() {
        val creation = screen as? Screen.Creating ?: return
        show(creation.copy(attributes = creation.rules.balancedAttributes, notice = null))
    }

    fun confirmCharacter() {
        val creation = screen as? Screen.Creating ?: return
        val gender = creation.gender ?: return
        when (val result = engine.dispatch(null, GameAction.Start(gender, creation.attributes))) {
            is ActionResult.Changed -> commit(creation, result)
            is ActionResult.Blocked -> show(creation.copy(notice = application.requirementText(result.requirement)))
            is ActionResult.Display -> error("Start did not produce a new game state")
        }
    }

    fun continueGame() {
        val menu = screen as? Screen.Menu ?: return
        if (menu.save is SaveSlot.ReadResult.Available) {
            show(activeScreen(menu.save, menu.save.state.lastSparring?.sparedNotice))
        }
    }

    fun returnToMenu(): Boolean = when (val current = screen) {
        is Screen.Creating -> {
            show(Screen.Menu(current.previousSave))
            true
        }
        is Screen.Exploring -> {
            show(Screen.Menu(current.game))
            true
        }
        is Screen.Sparring -> {
            show(Screen.Menu(current.game))
            true
        }
        is Screen.PlayingSparring -> {
            val saved = when (val next = current.next) {
                is Screen.Sparring -> next.game
                is Screen.Exploring -> next.game
                is Screen.Creating -> error("A completed round cannot open character creation")
            }
            show(Screen.Menu(saved))
            true
        }
        is Screen.Saving, is Screen.SaveFailed -> true
        else -> false
    }

    fun move(direction: Direction) = play(GameAction.Move(direction))

    fun meditate() = play(GameAction.Meditate)

    fun drink() = play(GameAction.Drink)

    fun play(action: GameAction): Boolean {
        val previous = screen
        val game = when (previous) {
            is Screen.Exploring -> previous.game
            is Screen.Sparring -> previous.game
            else -> return false
        }
        val state = game.state
        return when (val result = engine.dispatch(state, action, game.sparring)) {
            is ActionResult.Changed -> {
                val notice = when (action) {
                    is GameAction.TalkToNpc -> result.exploration.quests.firstOrNull {
                        it.quest.giverNpcId == action.npcId && it.round != state.questRounds[it.quest.id]
                    }?.objective?.assignmentText
                    is GameAction.AcceptQuest -> application.getString(
                        R.string.quest_accepted, result.exploration.quests.first { it.quest.id == action.questId }.quest.name,
                    )
                    is GameAction.CompleteQuestObjective -> {
                        val quest = result.exploration.quests.first { it.quest.id == action.questId }
                        val text = checkNotNull(quest.objective).completionText
                        if (quest.round?.stage == QuestStage.TURNED_IN) application.getString(
                            R.string.quest_objective_paid, text, result.state.coins - state.coins,
                            result.state.potential - state.potential, result.state.experience - state.experience,
                        ) else text
                    }
                    is GameAction.TurnInQuest -> if (result.exploration.sectPreparation?.questId == action.questId) {
                        application.getString(R.string.sect_preparation_unlocked, result.state.coins - state.coins)
                    } else application.getString(
                        R.string.quest_paid, result.state.coins - state.coins,
                        result.state.potential - state.potential, result.state.experience - state.experience,
                    )
                    is GameAction.JoinSect -> application.getString(
                        R.string.sect_joined, checkNotNull(result.exploration.martialTraining.sect).name,
                    )
                    is GameAction.LearnMartialArt -> application.getString(
                        R.string.martial_art_learned,
                        result.exploration.martialTraining.lessons.first { it.art.id == action.artId }.art.name,
                        action.targetLevel, state.potential - result.state.potential,
                    )
                    is GameAction.BuyFood -> application.getString(R.string.food_bought, state.coins - result.state.coins)
                    is GameAction.BuyArmour -> application.getString(
                        R.string.armour_bought, result.exploration.items.first { it.id == action.itemId }.name,
                        state.coins - result.state.coins,
                    )
                    is GameAction.Eat -> application.getString(R.string.food_eaten, state.hunger - result.state.hunger)
                    is GameAction.UseMedicine -> application.getString(
                        R.string.inventory_healed, result.state.character.health - state.character.health,
                    )
                    is GameAction.Equip -> {
                        val item = result.exploration.items.first { it.id == action.itemId }
                        val slot = checkNotNull(item.equipment).slot
                        application.getString(
                            if (slot in state.equipped) R.string.inventory_replaced else R.string.inventory_equipped, item.name,
                        )
                    }
                    is GameAction.Unequip -> application.getString(R.string.inventory_removed,
                        result.exploration.items.first { it.id == action.itemId }.name)
                    GameAction.Drink -> application.getString(R.string.water_drunk, state.thirst - result.state.thirst)
                    GameAction.Meditate -> {
                        application.getString(
                            R.string.meditation_result,
                            result.state.gameTime - state.gameTime,
                            result.state.character.health - state.character.health,
                            result.state.character.innerPower - state.character.innerPower,
                        )
                    }
                    is GameAction.AttackSparring -> result.state.lastSparring
                        ?.takeIf { it.boutId == action.boutId }?.sparedNotice
                    else -> null
                }
                commit(previous, result, notice)
                true
            }
            is ActionResult.Blocked -> {
                show(activeScreen(game, application.requirementText(result.requirement)))
                false
            }
            is ActionResult.Display -> false
        }
    }

    fun retrySave() {
        val failure = screen as? Screen.SaveFailed ?: return
        commit(failure.previous, failure.pending, failure.noticeAfterSave)
    }

    fun finishSparringPlayback(playback: Screen.PlayingSparring) {
        if (screen === playback) show(playback.next)
    }

    private fun commit(previous: Screen.Active, change: ActionResult.Changed, notice: String? = null) {
        show(Screen.Saving(previous))
        worker.execute {
            val next = try {
                saveSlot.write(change.state, change.sparring)
                val saved = activeScreen(SaveSlot.ReadResult.Available(change.state, change.exploration, change.sparring), notice)
                val round = change.completedSparringRound
                if (previous is Screen.Sparring && round != null) Screen.PlayingSparring(previous, round, saved) else saved
            } catch (error: IOException) {
                Log.w(TAG, "Could not commit the save slot", error)
                Screen.SaveFailed(previous, change, notice)
            }
            main.post {
                if (observers.isEmpty() && next is Screen.PlayingSparring) show(next.next) else show(next)
            }
        }
    }

    private fun activeScreen(game: SaveSlot.ReadResult.Available, notice: String?): Screen.Active =
        if (game.sparring == null) Screen.Exploring(game, notice) else Screen.Sparring(game, notice)

    private fun show(next: Screen) {
        screen = next
        observers.forEach { it(next) }
    }

    private fun loadSampleContent(): ContentLoadResult = try {
        application.assets.open(SAMPLE_ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
            ContentLoader.load(reader.readText())
        }
    } catch (_: IOException) {
        val issue = ContentIssue(SAMPLE_ASSET, application.getString(R.string.content_unreadable))
        ContentLoadResult.Invalid(listOf(issue))
    }

    private companion object {
        const val SAMPLE_ASSET = "content/sample.json"
        const val TAG = "GameSession"
    }
}

internal fun Context.requirementText(requirement: ActionRequirement): String = when (requirement) {
    ActionRequirement.StartRequired -> getString(R.string.start_required)
    is ActionRequirement.ContentVersionMismatch ->
        getString(R.string.content_version_mismatch, requirement.actual, requirement.expected)
    is ActionRequirement.UnknownLocation -> getString(R.string.location_missing, requirement.locationId)
    is ActionRequirement.UnknownScene -> getString(R.string.scene_missing, requirement.sceneId)
    ActionRequirement.InvalidPosition -> getString(R.string.position_invalid)
    ActionRequirement.InvalidCharacterState -> getString(R.string.character_invalid)
    is ActionRequirement.AttributeOutOfRange -> getString(R.string.attribute_out_of_range, requirement.min, requirement.max)
    ActionRequirement.AttributeBudgetMismatch -> getString(R.string.attribute_budget_mismatch)
    ActionRequirement.PathBlocked -> getString(R.string.path_blocked)
    ActionRequirement.ExitUnavailable -> getString(R.string.exit_unavailable)
    ActionRequirement.TimeLimitReached -> getString(R.string.time_limit_reached)
    ActionRequirement.InvalidInventory -> getString(R.string.inventory_invalid)
    ActionRequirement.EquipmentLocked -> getString(R.string.equipment_locked)
    ActionRequirement.EquipmentChanged -> getString(R.string.equipment_changed)
    ActionRequirement.ItemNotEquipment -> getString(R.string.item_not_equipment)
    ActionRequirement.ItemMissing -> getString(R.string.inventory_item_missing)
    ActionRequirement.ItemNotHealing -> getString(R.string.item_not_healing)
    ActionRequirement.HealingUnavailable -> getString(R.string.inventory_healing_unavailable)
    is ActionRequirement.EquipmentAttributeRequired -> getString(
        R.string.equipment_requirement, getString(requirement.attribute.label()), requirement.level, requirement.current,
    )
    is ActionRequirement.EquipmentArtRequired ->
        getString(R.string.equipment_art_requirement, requirement.name, requirement.level, requirement.current)
    ActionRequirement.SellerUnavailable -> getString(R.string.seller_unavailable)
    ActionRequirement.SellerTooFar -> getString(R.string.seller_too_far)
    ActionRequirement.ArmourUnavailable -> getString(R.string.armour_unavailable)
    ActionRequirement.ArmourShopTooFar -> getString(R.string.armour_shop_too_far)
    is ActionRequirement.CoinsMissing -> getString(R.string.coins_missing, requirement.amount)
    ActionRequirement.InventoryFull -> getString(R.string.inventory_full)
    ActionRequirement.ItemNotEdible -> getString(R.string.item_not_edible)
    ActionRequirement.FoodMissing -> getString(R.string.food_missing)
    ActionRequirement.NotHungry -> getString(R.string.not_hungry)
    ActionRequirement.WellTooFar -> getString(R.string.well_too_far)
    ActionRequirement.NotThirsty -> getString(R.string.not_thirsty)
    ActionRequirement.InvalidQuestState -> getString(R.string.quest_state_invalid)
    ActionRequirement.QuestUnavailable -> getString(R.string.quest_unavailable)
    ActionRequirement.QuestLocation -> getString(R.string.quest_wrong_location)
    ActionRequirement.QuestRoundChanged -> getString(R.string.quest_round_changed)
    ActionRequirement.QuestAlreadyActive -> getString(R.string.quest_already_active)
    ActionRequirement.QuestAlreadyTurnedIn -> getString(R.string.quest_already_turned_in)
    is ActionRequirement.HouseworkRetired -> getString(R.string.housework_retired, requirement.experience)
    ActionRequirement.RewardLimit -> getString(R.string.quest_reward_limit)
    ActionRequirement.InvalidMartialTraining -> getString(R.string.invalid_martial_training)
    ActionRequirement.TrainingUnavailable -> getString(R.string.training_unavailable)
    ActionRequirement.TrainingLocation -> getString(R.string.training_location)
    is ActionRequirement.AlreadyInSect -> getString(R.string.already_in_sect, requirement.name)
    ActionRequirement.SectRequired -> getString(R.string.sect_required)
    is ActionRequirement.MartialLevelRequired ->
        getString(R.string.martial_level_required, requirement.name, requirement.level)
    is ActionRequirement.PotentialMissing -> getString(R.string.potential_missing, requirement.amount)
    ActionRequirement.MartialLevelChanged -> getString(R.string.martial_level_changed)
    ActionRequirement.OpeningTrainingComplete -> getString(R.string.opening_training_complete)
    ActionRequirement.SparringUnavailable -> getString(R.string.sparring_unavailable)
    ActionRequirement.SparringChanged -> getString(R.string.sparring_changed)
    ActionRequirement.InvalidSparring -> getString(R.string.sparring_invalid)
}
