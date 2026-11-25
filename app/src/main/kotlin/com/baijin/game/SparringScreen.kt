package com.baijin.game

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.OneShotPreDrawListener
import com.baijin.game.core.GameAction
import com.baijin.game.core.SavedSparring
import com.baijin.game.core.SparringFighter
import com.baijin.game.core.SparringResult

internal class SparringScreen(private val context: Context, private val session: GameSession) {
    private val handler = Handler(Looper.getMainLooper())
    private val advanceCue = Runnable { advancePlayback() }
    private var playback: GameSession.Screen.PlayingSparring? = null
    private var cues = emptyList<SparringCue>()
    private var nextCue = 0
    private var shownBattle: SavedSparring? = null
    private var latestPending = false
    private var hasUnread = false
    private var dialog: AlertDialog? = null

    private val title = context.gameText(22f).apply { useDisplayTypeface() }
    private val roundLabel = context.gameText(14f, R.color.muted_ink)
    private val player = FighterPanel(context)
    private val opponent = FighterPanel(context)
    private val log = readingText()
    private val logScroll = ScrollView(context).apply { addView(log) }
    private val resultText = readingText()
    private val resultScroll = ScrollView(context).apply { addView(resultText) }
    private val notice = context.gameText(14f, R.color.muted_ink).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val attack = context.gameButton(R.string.sparring_attack, primary = true)
    private val moves = context.gameButton(R.string.sparring_moves).apply {
        setOnClickListener { showMenu(R.string.sparring_moves, R.string.sparring_no_moves) }
    }
    private val items = context.gameButton(R.string.sparring_items).apply {
        setOnClickListener { showMenu(R.string.sparring_items, R.string.sparring_no_items) }
    }
    private val more = context.gameButton(R.string.sparring_more).apply {
        setOnClickListener { showMenu(R.string.sparring_more, R.string.sparring_no_more) }
    }
    private val actions = listOf(attack, moves, items, more)
    private val skip = context.gameButton(R.string.sparring_skip).apply {
        visibility = View.GONE
        setOnClickListener {
            val round = playback ?: return@setOnClickListener
            handler.removeCallbacks(advanceCue)
            while (nextCue < cues.size) showNextCue()
            session.finishSparringPlayback(round)
        }
    }
    private val latest = context.gameButton(R.string.sparring_latest).apply {
        visibility = View.GONE
        setOnClickListener { followLatest() }
    }
    private val confirm = context.gameButton(R.string.sparring_claim, primary = true)
    private val review = context.gameButton(R.string.sparring_log).apply {
        setOnClickListener {
            val text = readingText().apply {
                setPadding(context.dp(20), context.dp(4), context.dp(20), context.dp(12))
                this.text = log.text
            }
            dialog = AlertDialog.Builder(context).setTitle(R.string.sparring_log)
                .setView(ScrollView(context).apply { addView(text) })
                .setPositiveButton(R.string.close, null).show()
        }
    }
    private val retry = context.gameButton(R.string.retry_save).apply {
        setOnClickListener { session.retrySave() }
    }
    private val combatControls = buttonRow(actions)
    private val resultControls = buttonRow(listOf(review, confirm))

    val view = LinearLayout(context).apply {
        val gap = context.dp(12)
        setPadding(gap, gap, gap, gap)
        setBackgroundColor(context.getColor(R.color.paper))
        val fighters = ScrollView(context).apply {
            isFillViewport = true
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.ink))
                cornerRadius = context.dp(6).toFloat()
            }
            addView(context.column().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(gap, gap, gap, gap)
                addView(player.view)
                addView(View(context).apply { setBackgroundColor(context.getColor(R.color.action_ink)) },
                    LinearLayout.LayoutParams(MATCH_PARENT, context.dp(1)).apply {
                        topMargin = gap
                        bottomMargin = gap
                    })
                addView(opponent.view)
            })
        }
        addView(fighters, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginEnd = gap })
        addView(context.column().apply {
            val header = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = context.dp(48)
                addView(context.column().apply {
                    addView(title)
                    addView(roundLabel)
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(latest)
                addView(skip, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    marginStart = context.dp(4)
                })
            }
            addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(context.getColor(R.color.paper_shade))
                    cornerRadius = context.dp(4).toFloat()
                }
                addView(logScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(resultScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply {
                topMargin = context.dp(6)
                bottomMargin = context.dp(8)
            })
            addView(notice)
            addView(retry)
            addView(combatControls)
            addView(resultControls)
        }, LinearLayout.LayoutParams(0, MATCH_PARENT, 2.7f))
    }

    init {
        logScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            if (!latestPending && !logScroll.canScrollVertically(1)) setUnread(false)
        }
    }

    fun render(game: SaveSlot.ReadResult.Available, locked: Boolean, message: String?, canRetry: Boolean) {
        val battle = checkNotNull(game.sparring)
        val fight = battle.fight
        if (shownBattle !== battle) {
            val previous = shownBattle
            val justPlayed = playback?.round === fight.rounds.lastOrNull() && playback != null
            if (!justPlayed && (previous?.boutId != battle.boutId || previous.fight.rounds != fight.rounds)) {
                log.text = if (fight.rounds.isEmpty()) context.getText(R.string.sparring_first_attack) else {
                    SpannableStringBuilder().apply {
                        fight.rounds.forEachIndexed { index, round ->
                            if (index > 0) append("\n\n")
                            append(formatLines(round.logLines()))
                        }
                    }
                }
                followLatest()
            }
            shownBattle = battle
        }
        stopPlayback()
        val result = battle.result
        title.text = when (result) {
            is SparringResult.Victory -> context.getText(R.string.sparring_victory)
            SparringResult.Death -> context.getText(R.string.sparring_death)
            null -> context.getString(R.string.sparring_title, fight.opponentName)
        }
        roundLabel.text = context.getString(R.string.sparring_round, fight.rounds.size + if (result == null) 1 else 0)
        player.name.setText(R.string.sparring_player)
        opponent.name.text = fight.opponentName
        showReadings(SparringReadings(fight.player, fight.opponent))
        logScroll.visibility = if (result == null) View.VISIBLE else View.GONE
        resultScroll.visibility = if (result != null) View.VISIBLE else View.GONE
        combatControls.visibility = if (result == null) View.VISIBLE else View.GONE
        resultControls.visibility = if (result != null) View.VISIBLE else View.GONE
        latest.visibility = if (result == null && hasUnread) View.VISIBLE else View.GONE
        notice.text = message.orEmpty()
        notice.visibility = if (message != null) View.VISIBLE else View.GONE
        retry.visibility = if (canRetry) View.VISIBLE else View.GONE
        actions.forEach { it.isEnabled = !locked }
        confirm.isEnabled = !locked
        attack.setOnClickListener { session.play(GameAction.AttackSparring(battle.boutId, fight.rounds.size + 1)) }
        if (result != null) showResult(game, battle, result)
    }

    fun play(round: GameSession.Screen.PlayingSparring) {
        if (playback === round) return
        render(round.previous.game, locked = true, message = null, canRetry = false)
        val fight = checkNotNull(round.previous.game.sparring).fight
        playback = round
        cues = round.round.playback(SparringReadings(fight.player, fight.opponent))
        nextCue = 0
        roundLabel.text = context.getString(R.string.sparring_round, round.round.number)
        skip.visibility = View.VISIBLE
        advancePlayback()
    }

    fun leave() {
        stopPlayback()
        shownBattle = null
        latestPending = false
        setUnread(false)
        dismissDialog()
    }

    fun dismissDialog() {
        dialog?.dismiss()
        dialog = null
    }

    private fun stopPlayback() {
        handler.removeCallbacks(advanceCue)
        playback = null
        cues = emptyList()
        skip.visibility = View.GONE
    }

    private fun advancePlayback() {
        val round = playback ?: return
        if (nextCue == cues.size) {
            session.finishSparringPlayback(round)
            return
        }
        val delay = showNextCue().delayAfterMillis
        if (delay == 0L) session.finishSparringPlayback(round) else handler.postDelayed(advanceCue, delay)
    }

    private fun showNextCue(): SparringCue {
        val firstCue = nextCue == 0
        val cue = cues[nextCue++]
        val follow = latestPending || !logScroll.canScrollVertically(1)
        val lines = formatLines(cue.lines)
        if (firstCue && playback?.round?.number == 1) log.text = lines else {
            log.append(if (firstCue) "\n\n" else "\n")
            log.append(lines)
        }
        showReadings(cue.readings)
        if (follow) followLatest() else setUnread(true)
        return cue
    }

    private fun followLatest() {
        setUnread(false)
        if (latestPending) return
        latestPending = true
        OneShotPreDrawListener.add(logScroll) {
            latestPending = false
            logScroll.scrollTo(0, log.height)
        }
    }

    private fun setUnread(unread: Boolean) {
        hasUnread = unread
        latest.visibility = if (unread && logScroll.visibility == View.VISIBLE) View.VISIBLE else View.GONE
    }

    private fun showReadings(readings: SparringReadings) {
        player.render(readings.player)
        opponent.render(readings.opponent)
    }

    private fun showResult(game: SaveSlot.ReadResult.Available, battle: SavedSparring, result: SparringResult) {
        val summary = when (result) {
            is SparringResult.Victory -> if (result.loot.isEmpty()) context.getString(R.string.sparring_no_loot) else {
                val names = game.exploration.items.associate { it.id to it.name }
                result.loot.entries.joinToString("\n") { (id, quantity) ->
                    context.getString(R.string.sparring_loot_item, names.getValue(id), quantity)
                }
            }
            SparringResult.Death -> context.getString(R.string.sparring_killed, battle.fight.opponentName)
        }
        val decisiveRound = battle.fight.rounds.last().logLines()
        val text = formatLines(decisiveRound).append("\n\n").append(summary)
        if (resultText.text.toString() != text.toString()) {
            resultText.text = text
            resultScroll.scrollTo(0, 0)
        }
        when (result) {
            is SparringResult.Victory -> {
                confirm.setText(R.string.sparring_claim)
                confirm.setOnClickListener { session.play(GameAction.ClaimSparringLoot(battle.boutId)) }
            }
            SparringResult.Death -> {
                confirm.setText(R.string.sparring_read_before)
                confirm.setOnClickListener { session.play(GameAction.ReadBeforeSparring(battle.boutId)) }
            }
        }
    }

    private fun formatLines(lines: List<SparringLogLine>) = SpannableStringBuilder().apply {
        lines.forEachIndexed { index, line ->
            if (index > 0) append('\n')
            line.moveName?.let { name ->
                val start = length
                append(name)
                setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append('　')
            }
            append(line.text)
        }
    }

    private fun readingText() = context.gameText(16f).apply {
        setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        setLineSpacing(context.dp(5).toFloat(), 1f)
    }

    private fun buttonRow(buttons: List<View>) = LinearLayout(context).apply {
        buttons.forEachIndexed { index, button ->
            addView(button, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = context.dp(6)
            })
        }
    }

    private fun showMenu(title: Int, message: Int) {
        dialog = AlertDialog.Builder(context).setTitle(title).setMessage(message)
            .setPositiveButton(R.string.close, null).show()
    }
}

private class FighterPanel(context: Context) {
    val name = context.gameText(20f, R.color.paper).apply { useDisplayTypeface() }
    private val health = context.gameText(14f, R.color.paper)
    private val originalHealthLimit = context.gameText(12f, R.color.paper_line)
    private val innerPower = context.gameText(14f, R.color.paper)
    private val healthBar = track(context, R.color.health_ink)
    private val innerPowerBar = track(context, R.color.thirst_ink)
    val view = context.column().apply {
        addView(name)
        addView(health)
        addView(healthBar, LinearLayout.LayoutParams(MATCH_PARENT, context.dp(6)))
        addView(originalHealthLimit)
        addView(innerPower, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = context.dp(4)
        })
        addView(innerPowerBar, LinearLayout.LayoutParams(MATCH_PARENT, context.dp(6)))
    }

    fun render(fighter: SparringFighter) {
        val context = view.context
        health.text = context.getString(R.string.sparring_health, fighter.health, fighter.effectiveMaxHealth)
        originalHealthLimit.text = context.getString(R.string.sparring_original_health_limit, fighter.maxHealth)
        originalHealthLimit.visibility = if (fighter.effectiveMaxHealth < fighter.maxHealth) View.VISIBLE else View.GONE
        innerPower.text = context.getString(R.string.sparring_inner_power, fighter.innerPower, fighter.maxInnerPower)
        healthBar.max = fighter.maxHealth
        healthBar.secondaryProgress = fighter.effectiveMaxHealth
        healthBar.progress = fighter.health
        innerPowerBar.max = fighter.maxInnerPower
        innerPowerBar.progress = fighter.innerPower
    }

    private fun track(context: Context, tint: Int) =
        ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            progressTintList = ColorStateList.valueOf(context.getColor(tint))
            secondaryProgressTintList = ColorStateList.valueOf(context.getColor(R.color.paper_line))
            progressBackgroundTintList = ColorStateList.valueOf(context.getColor(R.color.action_ink))
        }
}
