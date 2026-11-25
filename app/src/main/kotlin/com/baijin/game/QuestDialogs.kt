package com.baijin.game

import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import com.baijin.game.core.ActionRequirement
import com.baijin.game.core.Exploration
import com.baijin.game.core.GameAction
import com.baijin.game.core.MartialTrainingView
import com.baijin.game.core.QuestStage
import com.baijin.game.core.QuestView
import com.baijin.game.core.content.DialogueTopic
import com.baijin.game.core.content.NpcService
import com.baijin.game.core.content.OpeningSectDefinition

internal class QuestDialogs(private val context: Context, private val session: GameSession) {
    private sealed interface Page {
        data object Menu : Page
        data object Log : Page
        data object MartialArts : Page
        data class Topic(val topic: DialogueTopic) : Page
        data class Service(val service: NpcService) : Page
        data class ConfirmSect(val sect: OpeningSectDefinition) : Page
    }

    private var dialog: AlertDialog? = null
    private var scroll: ScrollView? = null
    private var page: Page = Page.Menu
    private var npcId: String? = null
    private var notice: String? = null

    fun talk(game: SaveSlot.ReadResult.Available) {
        val npc = game.exploration.dialogue?.npc ?: return
        open(npc.name, Page.Menu, npc.id, game)
        session.play(GameAction.TalkToNpc(npc.id))
    }

    fun log(game: SaveSlot.ReadResult.Available) = open(context.getString(R.string.quests), Page.Log, null, game)

    fun martialArts(game: SaveSlot.ReadResult.Available) =
        open(context.getString(R.string.martial_arts), Page.MartialArts, null, game)

    private fun open(title: String, start: Page, npc: String?, game: SaveSlot.ReadResult.Available) {
        dismiss()
        page = start
        npcId = npc
        val scroller = ScrollView(context)
        scroll = scroller
        dialog = AlertDialog.Builder(context).setTitle(title).setView(scroller)
            .setNegativeButton(R.string.close, null).show()
        render(game, locked = false, message = null)
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        scroll = null
        notice = null
    }

    fun render(game: SaveSlot.ReadResult.Available, locked: Boolean, message: String?) {
        if (dialog?.isShowing != true) return
        if (npcId != null && game.exploration.dialogue?.npc?.id != npcId) {
            dismiss()
            return
        }
        notice = message
        draw(game, locked, resetScroll = false)
    }

    private fun draw(game: SaveSlot.ReadResult.Available, locked: Boolean, resetScroll: Boolean) {
        val scroller = scroll ?: return
        val y = if (resetScroll) 0 else scroller.scrollY
        val body = context.column().apply {
            setPadding(context.dp(24), context.dp(4), context.dp(24), context.dp(12))
        }
        fun changePage(next: Page) {
            page = next
            notice = null
            draw(game, locked = false, resetScroll = true)
        }
        when (val current = page) {
            Page.MartialArts -> body.showMartialArts(game)
            Page.Log -> game.exploration.quests.forEach { view ->
                body.line(view.quest.name, 17f)
                view.round?.let { body.line(context.getString(R.string.quest_round, it.number)) }
                body.questStatus(view)
            }
            Page.Menu -> {
                val dialogue = game.exploration.dialogue ?: return
                game.exploration.quests.filter { it.quest.assignOnDialogue && it.quest.giverNpcId == npcId }
                    .forEach { view ->
                        val block = view.acceptanceBlock
                        if (view.round?.stage == QuestStage.IN_PROGRESS) {
                            view.objective?.assignmentText?.let { body.line(it) }
                        } else if (block is ActionRequirement.HouseworkRetired) {
                            body.line(context.requirementText(block))
                        }
                    }
                val columns = LinearLayout(context)
                val services = context.column()
                val topics = context.column()
                columns.addView(services, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                    marginEnd = context.dp(12)
                })
                columns.addView(topics, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.25f))
                body.addView(columns)
                services.line(context.getString(R.string.npc_services))
                topics.line(context.getString(R.string.dialogue_topics))
                dialogue.npc.services.forEach { service ->
                    services.button(context.getString(service.label()), locked) { changePage(Page.Service(service)) }
                }
                dialogue.topics.forEach { topic ->
                    topics.button(topic.title, locked) { changePage(Page.Topic(topic)) }
                }
            }
            is Page.Topic -> {
                body.line(current.topic.title, 17f)
                body.line(current.topic.text)
                current.topic.service?.let { service ->
                    body.button(context.getString(service.label()), locked, primary = true) {
                        changePage(Page.Service(service))
                    }
                }
            }
            is Page.Service -> {
                body.line(context.getString(current.service.label()), 17f)
                notice?.let { body.line(it) }
                body.service(current.service, game, locked) { sect -> changePage(Page.ConfirmSect(sect)) }
            }
            is Page.ConfirmSect -> {
                body.line(context.getString(R.string.sect_choice_once))
                body.button(context.getString(R.string.confirm_join_sect, current.sect.name), locked, primary = true) {
                    val trainer = checkNotNull(npcId)
                    page = Page.Service(NpcService.TRAINING)
                    session.play(GameAction.JoinSect(trainer, current.sect.id))
                }
                body.button(context.getString(R.string.return_to_training), locked) {
                    changePage(Page.Service(NpcService.TRAINING))
                }
            }
        }
        if (page is Page.Topic || page is Page.Service) {
            body.button(context.getString(R.string.return_to_dialogue), locked) { changePage(Page.Menu) }
        }
        scroller.removeAllViews()
        scroller.addView(body)
        scroller.post { scroller.scrollTo(0, y) }
    }

    private fun LinearLayout.service(
        service: NpcService,
        game: SaveSlot.ReadResult.Available,
        locked: Boolean,
        chooseSect: (OpeningSectDefinition) -> Unit,
    ) {
        val exploration = game.exploration
        when (service) {
            NpcService.HOUSEWORK, NpcService.MESSAGE -> exploration.quests.filter {
                (it.quest.giverNpcId == npcId || it.quest.turnInNpcId == npcId) &&
                    it.quest.id != exploration.sectPreparation?.questId
            }.forEach { questOffer(it, locked) }
            NpcService.MESSAGE_HINT -> messageHint(exploration, locked)
            NpcService.FOOD -> {
                val rules = exploration.supplies
                line(context.getString(R.string.food_offer, rules.foodPrice, game.state.coins, rules.hungerReduction))
                button(context.getString(R.string.buy_one), locked, primary = true) {
                    session.play(GameAction.BuyFood(rules.sellerNpcId))
                }
            }
            NpcService.ARMOUR -> armourShop(game, locked)
            NpcService.SECT -> sectArrangements(exploration, locked)
            NpcService.TRAINING -> training(game, locked, chooseSect)
            NpcService.SPAR -> button(context.getString(R.string.sparring_begin), locked, primary = true) {
                if (session.play(GameAction.BeginSparring(checkNotNull(npcId)))) dismiss()
            }
            NpcService.FIND, NpcService.HERBS -> line(context.getString(R.string.service_unavailable))
        }
    }

    private fun LinearLayout.sectArrangements(exploration: Exploration, locked: Boolean) {
        val preparation = exploration.sectPreparation
        val introduction = exploration.quests.firstOrNull { it.quest.id == preparation?.questId }
        if (preparation == null || introduction == null) {
            line(context.getString(R.string.sect_arrangements_hint))
            return
        }
        if (preparation.stage == QuestStage.READY) {
            introduction.objective?.completionText?.takeUnless { it == notice }?.let { line(it) }
        }
        questOffer(introduction, locked)
        if (preparation.unlocked) line(context.getString(R.string.sect_preparation_guidance))
    }

    private fun LinearLayout.armourShop(game: SaveSlot.ReadResult.Available, locked: Boolean) {
        val exploration = game.exploration
        val seller = checkNotNull(npcId)
        val stockLocked = exploration.sectPreparation?.unlocked == false
        line(context.getString(R.string.coins, game.state.coins))
        if (stockLocked) line(context.getString(R.string.armour_stock_locked))
        exploration.armourOffers.chunked(2).forEach { offers ->
            addView(LinearLayout(context).apply {
                offers.forEachIndexed { column, offer ->
                    val stats = checkNotNull(offer.item.equipment).stats
                    addView(context.column().apply {
                        button(context.getString(R.string.armour_offer, offer.item.name, offer.price),
                            locked || stockLocked, primary = true) {
                            session.play(GameAction.BuyArmour(seller, offer.item.id))
                        }
                        line(context.getString(R.string.armour_offer_stats, stats.protection, stats.evasion), 13f)
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                        if (column > 0) marginStart = context.dp(12)
                    })
                }
            }, row())
        }
    }

    private fun LinearLayout.training(
        game: SaveSlot.ReadResult.Available,
        locked: Boolean,
        chooseSect: (OpeningSectDefinition) -> Unit,
    ) {
        val training = game.exploration.martialTraining
        trainingStatus(training, game.state.potential)
        if (training.sectChoices.isNotEmpty()) {
            val choices = LinearLayout(context)
            training.sectChoices.forEach { sect ->
                choices.addView(context.gameButton(R.string.close).apply {
                    text = context.getString(R.string.choose_sect, sect.name)
                    isEnabled = !locked
                    setOnClickListener { chooseSect(sect) }
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) })
            }
            addView(choices, row())
        }
        val lessons = LinearLayout(context)
        training.lessons.forEachIndexed { index, lesson ->
            val lessonColumn = context.column().apply {
                line(context.getString(R.string.martial_art_level, lesson.art.name, lesson.level), 17f)
                lesson.nextCost?.let { cost ->
                    val target = lesson.level + 1
                    val label = context.getString(R.string.learn_martial_art, lesson.art.name, target, cost)
                    button(label, locked || lesson.requirement != null, primary = true) {
                        session.play(GameAction.LearnMartialArt(checkNotNull(npcId), lesson.art.id, target))
                    }
                }
                lesson.requirement?.let { line(context.requirementText(it)) }
            }
            lessons.addView(lessonColumn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                if (index < training.lessons.lastIndex) marginEnd = context.dp(12)
            })
        }
        addView(lessons, row())
    }

    private fun LinearLayout.showMartialArts(game: SaveSlot.ReadResult.Available) {
        val training = game.exploration.martialTraining
        trainingStatus(training, game.state.potential)
        val learned = training.lessons.filter { it.level > 0 }
        if (learned.isEmpty()) line(context.getString(R.string.no_martial_arts))
        learned.forEach { lesson ->
            line(context.getString(R.string.martial_art_level, lesson.art.name, lesson.level))
        }
        training.staffTraining.forEach { (art, level) -> line(context.getString(R.string.martial_art_level, art.name, level)) }
        training.appliedStaffArt?.let { line(context.getString(R.string.applied_staff_art, it.name)) }
        line(context.getString(R.string.unarmed_power, training.unarmedAttack.power))
    }

    private fun LinearLayout.trainingStatus(training: MartialTrainingView, potential: Int) {
        val sect = training.sect?.name ?: context.getString(R.string.no_sect)
        val applied = training.appliedUnarmedArt?.name ?: context.getString(R.string.no_applied_unarmed_art)
        line(context.getString(R.string.martial_training_status, sect, potential))
        line(context.getString(R.string.applied_unarmed_art, applied))
    }

    private fun LinearLayout.questOffer(view: QuestView, locked: Boolean) {
        val quest = view.quest
        val round = view.round
        if (!quest.assignOnDialogue && view.acceptanceBlock == null && quest.giverNpcId == npcId) {
            line(context.getString(R.string.quest_reward, quest.reward.coins, quest.reward.potential, quest.reward.experience))
            quest.objectives.forEach { objective ->
                val label = if (quest.objectives.size == 1) quest.name else objective.name
                button(context.getString(R.string.accept_quest, label), locked, primary = true) {
                    session.play(GameAction.AcceptQuest(quest.id, objective.id, round?.number ?: 0))
                }
            }
            return
        }
        questStatus(view)
        if (round != null && view.canCompleteHere && view.objective?.npcId == npcId) {
            val objective = checkNotNull(view.objective)
            button(objective.actionLabel, locked, primary = true) {
                session.play(GameAction.CompleteQuestObjective(quest.id, objective.id, round.number))
            }
        }
        if (round?.stage == QuestStage.READY && quest.turnInNpcId == npcId) {
            button(context.getString(R.string.turn_in_quest, quest.name), locked, primary = true) {
                session.play(GameAction.TurnInQuest(quest.id, round.number))
            }
        }
    }

    private fun LinearLayout.messageHint(exploration: Exploration, locked: Boolean) {
        val view = exploration.quests.firstOrNull { view -> view.quest.objectives.any { it.npcId == npcId } }
        val round = view?.round
        val objective = view?.objective
        if (view != null && round != null && objective != null && view.canCompleteHere) {
            line(context.getString(R.string.confirm_message_hint))
            button(objective.actionLabel, locked, primary = true) {
                session.play(GameAction.CompleteQuestObjective(view.quest.id, objective.id, round.number))
            }
        } else if (view != null) {
            questStatus(view)
        }
    }

    private fun LinearLayout.questStatus(view: QuestView) {
        view.objective?.takeIf { it.npcId == null }?.let {
            line(context.getString(R.string.quest_selected_objective, it.name))
        }
        val status = when (view.round?.stage) {
            QuestStage.IN_PROGRESS -> context.getString(R.string.quest_in_progress, view.objective?.actionLabel, view.objectivePlace)
            QuestStage.READY -> context.getString(R.string.quest_ready, view.turnInPlace)
            QuestStage.TURNED_IN -> context.getString(
                if (view.quest.settleOnObjective) R.string.quest_complete else R.string.quest_turned_in,
            )
            null -> view.acceptanceBlock?.let(context::requirementText)
                ?: context.getString(
                    if (view.quest.assignOnDialogue) R.string.quest_start_by_talking else R.string.quest_not_started,
                    view.giverPlace,
                )
        }
        line(status)
        if (view.round?.stage == QuestStage.IN_PROGRESS && !view.quest.settleOnObjective) {
            line(context.getString(R.string.quest_turn_in_place, view.turnInPlace))
        }
        if (view.round?.stage == QuestStage.TURNED_IN) {
            view.acceptanceBlock?.takeUnless { it == ActionRequirement.QuestAlreadyTurnedIn }
                ?.let { line(context.requirementText(it)) }
            if (view.acceptanceBlock == null) line(context.getString(R.string.quest_another_round, view.giverPlace))
        }
    }

    private fun LinearLayout.line(value: String, size: Float = 15f) {
        addView(context.gameText(size).apply { text = value }, row())
    }

    private fun LinearLayout.button(label: String, locked: Boolean, primary: Boolean = false, click: () -> Unit) {
        addView(context.gameButton(R.string.close, primary).apply {
            text = label
            isEnabled = !locked
            setOnClickListener { click() }
        }, row())
    }

    private fun row() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(5) }
}

private fun NpcService.label(): Int = when (this) {
    NpcService.HOUSEWORK -> R.string.housework
    NpcService.FOOD -> R.string.view_food
    NpcService.ARMOUR -> R.string.view_armour
    NpcService.MESSAGE -> R.string.training_message
    NpcService.MESSAGE_HINT -> R.string.view_message_hint
    NpcService.SECT -> R.string.sect_arrangements
    NpcService.TRAINING -> R.string.view_training
    NpcService.SPAR -> R.string.view_sparring
    NpcService.FIND -> R.string.view_find
    NpcService.HERBS -> R.string.view_herbs
}
