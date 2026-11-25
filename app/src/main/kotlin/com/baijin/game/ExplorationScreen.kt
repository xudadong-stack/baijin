package com.baijin.game

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.baijin.game.core.Attribute
import com.baijin.game.core.Direction
import com.baijin.game.core.GameAction

internal class ExplorationScreen(private val context: Context, private val session: GameSession) {
    val view = FrameLayout(context)
    private val map = TownMapView(context).apply { id = R.id.town_map }
    private val movementHandler = Handler(Looper.getMainLooper())
    private var heldDirection: Direction? = null
    private val stepWhileHeld = object : Runnable {
        override fun run() {
            val direction = heldDirection ?: return
            val delay = when (session.screen) {
                is GameSession.Screen.Exploring -> {
                    if (!session.move(direction)) return
                    STEP_INTERVAL_MS
                }
                is GameSession.Screen.Saving -> SAVE_POLL_MS
                else -> return
            }
            if (heldDirection == direction) movementHandler.postDelayed(this, delay)
        }
    }
    private val joystick = VirtualJoystickView(context).apply {
        onDirectionChanged = ::holdDirection
        onStep = { session.move(it) }
    }
    private val location = context.gameText(23f).apply {
        id = R.id.location_name
        useDisplayTypeface()
    }
    private val health = meter(R.string.health, R.color.health_ink)
    private val innerPower = meter(R.string.inner_power, R.color.action_ink)
    private val hunger = meter(R.string.hunger, R.color.hunger_ink)
    private val thirst = meter(R.string.thirst, R.color.thirst_ink)
    private val time = context.gameText(14f).apply { id = R.id.game_time }
    private val coins = context.gameText(14f)
    private val notice = context.gameText(14f, R.color.paper).apply {
        id = R.id.action_notice
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val retry = context.gameButton(R.string.retry_save).apply {
        id = R.id.retry_save
        setOnClickListener { session.retrySave() }
    }
    private val feedback = feedback()
    private val questDialogs = QuestDialogs(context, session)
    private val talk = context.gameButton(R.string.talk, primary = true).apply { id = R.id.talk_to_npc }
    private val work = context.gameButton(R.string.housework, primary = true).apply { id = R.id.do_housework }
    private val drawWater = context.gameButton(R.string.housework, primary = true).apply { id = R.id.draw_water }
    private val interaction = context.column().apply {
        addView(talk, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(work, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
    private val drink = context.gameButton(R.string.drink_water, primary = true).apply {
        id = R.id.drink_water
        setOnClickListener { releaseMovement(); session.drink() }
    }
    private val homeTitle = context.gameText(19f).apply { useDisplayTypeface() }
    private val navigationHint = context.gameText(12f)
    private val homeHeader = context.column().apply {
        background = GradientDrawable().apply { setColor(0xEAF1E4CD.toInt()); cornerRadius = context.dp(5).toFloat() }
        setPadding(context.dp(10), context.dp(6), context.dp(10), context.dp(6))
        addView(homeTitle)
        addView(navigationHint)
    }
    private val homeCharacter = context.gameButton(R.string.character).apply { id = R.id.open_character }
    private val homeBag = context.gameButton(R.string.inventory).apply { id = R.id.open_inventory }
    private val homeQuests = context.gameButton(R.string.quests).apply { id = R.id.open_quests }
    private val homeMartialArts = context.gameButton(R.string.martial_arts)
    private val homeMenu = context.gameButton(R.string.menu).apply {
        id = R.id.return_to_menu
        setOnClickListener { session.returnToMenu() }
    }
    private val homeActions = LinearLayout(context).apply {
        addView(homeBag, LayoutParams(context.dp(56), WRAP_CONTENT).apply { marginEnd = context.dp(8) })
        addView(homeQuests, LayoutParams(context.dp(56), WRAP_CONTENT).apply { marginEnd = context.dp(8) })
        addView(homeMartialArts, LayoutParams(context.dp(56), WRAP_CONTENT).apply { marginEnd = context.dp(8) })
        addView(homeCharacter, LayoutParams(context.dp(56), WRAP_CONTENT))
        addView(homeMenu, LayoutParams(context.dp(72), WRAP_CONTENT).apply { marginStart = context.dp(8) })
    }
    private val townPanel = sidePanel()
    private var sceneId: String? = null
    private var characterDialog: AlertDialog? = null
    private val inventoryDialog = InventoryDialog(context, session)

    init {
        val mapPane = FrameLayout(context).apply {
            addView(map, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(homeHeader, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                marginStart = context.dp(16)
                topMargin = context.dp(10)
            })
            addView(homeActions, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                marginEnd = context.dp(12)
                topMargin = context.dp(10)
            })
            val diameter = context.dp(112)
            addView(joystick, FrameLayout.LayoutParams(diameter, diameter, Gravity.BOTTOM or Gravity.START).apply {
                marginStart = context.dp(12)
                bottomMargin = context.dp(12)
            })
            addView(interaction, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = context.dp(12)
                bottomMargin = context.dp(68)
            })
            addView(feedback, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                marginStart = context.dp(132)
                marginEnd = context.dp(12)
                bottomMargin = context.dp(12)
            })
        }
        view.addView(mapPane, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        view.addView(townPanel, FrameLayout.LayoutParams(context.dp(WELL_PANEL_WIDTH), MATCH_PARENT, Gravity.END).apply {
            topMargin = context.dp(72)
            bottomMargin = context.dp(12)
            marginEnd = context.dp(12)
        })
    }

    fun render(game: SaveSlot.ReadResult.Available, locked: Boolean, message: String?, canRetry: Boolean) {
        val scene = game.exploration.scene
        if (sceneId != scene.id) {
            releaseMovement()
            sceneId = scene.id
            val atHome = scene.indoors
            val joystickSize = context.dp(if (atHome) 96 else 112)
            joystick.layoutParams = joystick.layoutParams.apply {
                width = joystickSize
                height = joystickSize
            }

        }
        homeTitle.text = game.exploration.locationName
        navigationHint.setText(if (scene.indoors) R.string.room_exit_hint else R.string.town_explore_hint)
        townPanel.visibility = if (game.exploration.wellHere) View.VISIBLE else View.GONE
        val feedbackMargin = context.dp(if (game.exploration.wellHere) WELL_PANEL_WIDTH + 24 else 12)
        (feedback.layoutParams as FrameLayout.LayoutParams).let {
            if (it.marginEnd != feedbackMargin) {
                it.marginEnd = feedbackMargin
                feedback.layoutParams = it
            }
        }
        map.show(game.state, game.exploration)
        location.text = game.exploration.locationName
        val state = game.state
        val player = state.character
        val healthText = if (player.effectiveMaxHealth < player.maxHealth) {
            context.getString(R.string.injured_health, player.health, player.effectiveMaxHealth, player.maxHealth)
        } else null
        health.show(player.health, player.effectiveMaxHealth, healthText)
        innerPower.show(player.innerPower, player.maxInnerPower)
        hunger.show(state.hunger, game.exploration.timeRules.maxHunger)
        thirst.show(state.thirst, game.exploration.timeRules.maxThirst)
        time.text = context.getString(R.string.game_time, state.gameTime)
        coins.text = context.getString(R.string.coins, state.coins)
        notice.text = message.orEmpty()
        feedback.visibility = if (message == null) View.GONE else View.VISIBLE
        retry.visibility = if (canRetry) View.VISIBLE else View.GONE
        if (canRetry) releaseMovement()
        joystick.isEnabled = !canRetry
        homeCharacter.isEnabled = !locked
        homeMenu.isEnabled = !locked
        listOf(homeBag, homeQuests, homeMartialArts, talk, work, drink, drawWater).forEach { it.isEnabled = !locked }
        val npc = game.exploration.dialogue?.npc
        talk.visibility = if (npc != null) View.VISIBLE else View.GONE
        if (npc != null) talk.text = context.getString(R.string.talk_to_npc, npc.name)
        work.visibility = View.GONE
        drawWater.visibility = View.GONE
        game.exploration.quests.firstOrNull { it.canCompleteHere && it.objective?.locationId != null }
            ?.let { quest ->
                val objective = checkNotNull(quest.objective)
                val round = checkNotNull(quest.round)
                val button = if (game.exploration.wellHere) drawWater else work
                button.text = objective.actionLabel
                button.visibility = View.VISIBLE
                button.setOnClickListener {
                    releaseMovement()
                    session.play(GameAction.CompleteQuestObjective(quest.quest.id, objective.id, round.number))
                }
            }
        drink.visibility = if (game.exploration.wellHere && state.thirst > 0) View.VISIBLE else View.GONE
        val waterGap = if (drink.visibility == View.VISIBLE) context.dp(8) else 0
        (drawWater.layoutParams as LayoutParams).let {
            if (it.marginEnd != waterGap) {
                it.marginEnd = waterGap
                drawWater.layoutParams = it
            }
        }
        homeCharacter.setOnClickListener { showCharacter(game) }
        homeBag.setOnClickListener { releaseMovement(); inventoryDialog.show(game, view) }
        talk.setOnClickListener { releaseMovement(); questDialogs.talk(game) }
        homeQuests.setOnClickListener { releaseMovement(); questDialogs.log(game) }
        homeMartialArts.setOnClickListener { releaseMovement(); questDialogs.martialArts(game) }
        if (canRetry) {
            inventoryDialog.dismiss()
            questDialogs.dismiss()
        }
        inventoryDialog.render(game, locked, message)
        questDialogs.render(game, locked, message)
    }

    fun dismissDialog() {
        characterDialog?.dismiss()
        characterDialog = null
        inventoryDialog.dismiss()
        questDialogs.dismiss()
    }

    fun releaseMovement() {
        joystick.release()
        holdDirection(null)
    }

    private fun holdDirection(direction: Direction?) {
        movementHandler.removeCallbacks(stepWhileHeld)
        heldDirection = direction
        if (direction != null) stepWhileHeld.run()
    }

    private fun feedback(): View = context.column().apply {
        background = GradientDrawable().apply {
            setColor(0xE43B4B46.toInt())
            cornerRadius = context.dp(5).toFloat()
        }
        setPadding(context.dp(10), context.dp(6), context.dp(10), context.dp(6))
        addView(notice, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(retry, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(4) })
    }

    private fun sidePanel(): View {
        val readings = context.column().apply {
            addView(location, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(View(context).apply { setBackgroundColor(context.getColor(R.color.paper_line)) },
                LayoutParams(MATCH_PARENT, context.dp(1)).apply { topMargin = context.dp(8) })
            listOf(health, innerPower, hunger, thirst).forEach {
                addView(it.layout, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(8) })
            }
            addView(time, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(12) })
            addView(coins, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(2) })
        }
        val scroller = ScrollView(context).apply { addView(readings) }
        return context.column().apply {
            background = GradientDrawable().apply {
                setColor(0xF3F1E4CD.toInt())
                setStroke(context.dp(1), context.getColor(R.color.paper_line))
                cornerRadius = context.dp(6).toFloat()
            }
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
            addView(scroller, LayoutParams(MATCH_PARENT, 0, 1f))
            addView(LinearLayout(context).apply {
                addView(drawWater, LayoutParams(0, WRAP_CONTENT, 1f))
                addView(drink, LayoutParams(0, WRAP_CONTENT, 1f))
            }, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(8) })
        }
    }

    private fun meter(label: Int, color: Int): Meter {
        val value = context.gameText(14f).apply { gravity = Gravity.END }
        val line = LinearLayout(context).apply {
            addView(context.gameText(15f).apply { setText(label) }, LayoutParams(0, WRAP_CONTENT, 1f))
            addView(value, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = ColorStateList.valueOf(context.getColor(color))
            progressBackgroundTintList = ColorStateList.valueOf(context.getColor(R.color.paper_line))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val section = context.column().apply {
            addView(line, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(progress, LayoutParams(MATCH_PARENT, context.dp(6)))
        }
        return Meter(section, value, progress)
    }

    private fun showCharacter(game: SaveSlot.ReadResult.Available) {
        if (characterDialog?.isShowing == true) return
        releaseMovement()
        val player = game.state.character
        val page = context.column().apply {
            setPadding(context.dp(24), context.dp(8), context.dp(24), context.dp(12))
            addView(context.gameText(15f).apply {
                text = context.getString(R.string.sex_value, context.getString(player.gender.label()))
            })
            Attribute.entries.chunked(2).forEach { attributes ->
                val row = LinearLayout(context)
                attributes.forEach { attribute ->
                    row.addView(context.gameText(16f).apply {
                        text = context.getString(R.string.attribute_value, context.getString(attribute.label()), player.attributes[attribute])
                    }, LayoutParams(0, WRAP_CONTENT, 1f))
                }
                addView(row, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(5) })
            }
            addView(context.gameText(16f).apply { setText(R.string.appearance) },
                LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(12) })
            addView(context.gameText(16f).apply { text = game.exploration.appearanceDescription })
            run {
                val state = game.state
                val rules = game.exploration.timeRules
                addView(context.gameText(14f).apply {
                    text = context.getString(R.string.home_character_vitals,
                        player.health, player.effectiveMaxHealth, player.innerPower, player.maxInnerPower)
                }, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(12) })
                addView(context.gameText(14f).apply {
                    text = context.getString(R.string.home_character_needs,
                        state.hunger, rules.maxHunger, state.thirst, rules.maxThirst)
                })
                addView(context.gameText(14f).apply {
                    text = context.getString(R.string.home_character_progress, state.gameTime, state.coins)
                })
                addView(context.gameText(14f).apply {
                    text = context.getString(R.string.character_training_progress, state.potential, state.experience)
                })
            }
            addView(context.gameText(14f, R.color.muted_ink).apply {
                text = context.getString(R.string.meditation_cost, game.exploration.timeRules.meditationDuration)
            }, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(12) })
        }
        val scroll = ScrollView(context).apply { addView(page) }
        characterDialog = AlertDialog.Builder(context)
            .setTitle(R.string.character)
            .setView(scroll)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.meditate) { _, _ -> session.meditate() }
            .show()
    }

    private class Meter(val layout: View, private val value: TextView, private val progress: ProgressBar) {
        fun show(current: Int, limit: Int, label: String? = null) {
            value.text = label ?: value.context.getString(R.string.meter_value, current, limit)
            progress.max = limit
            progress.progress = current
        }
    }

    private companion object {
        const val WELL_PANEL_WIDTH = 260
        const val STEP_INTERVAL_MS = 165L
        const val SAVE_POLL_MS = 45L
    }
}
