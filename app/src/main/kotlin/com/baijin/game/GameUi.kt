package com.baijin.game

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.baijin.game.core.Attribute
import com.baijin.game.core.Gender
import kotlin.math.roundToInt

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

internal fun Context.gameText(size: Float, @ColorRes color: Int = R.color.ink) = TextView(this).apply {
    textSize = size
    setTextColor(context.getColor(color))
}

internal fun TextView.useDisplayTypeface() {
    typeface = Typeface.create("serif", Typeface.NORMAL)
}

internal fun Context.gameButton(@StringRes label: Int, primary: Boolean = false) = Button(this).apply {
    setText(label)
    setAllCaps(false)
    textSize = 15f
    gravity = Gravity.CENTER
    minimumWidth = 0
    minWidth = 0
    minimumHeight = dp(48)
    minHeight = dp(48)
    setPadding(dp(8), 0, dp(8), 0)
    styleGameButton(primary)
}

internal fun Button.styleGameButton(primary: Boolean) {
    val activeInk = if (primary) R.color.paper else R.color.ink
    setTextColor(ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(context.getColor(R.color.muted_ink), context.getColor(activeInk)),
    ))
    val outline = if (primary) R.color.action_ink else R.color.paper_line
    val fill = if (primary) R.color.action_ink else R.color.paper
    val backgroundStates = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), context.buttonSurface(R.color.paper_shade, R.color.paper_line))
        addState(intArrayOf(android.R.attr.state_focused), context.buttonSurface(fill, activeInk))
        addState(intArrayOf(), context.buttonSurface(fill, outline))
    }
    val ripple = if (primary) 0x30FFFFFF else 0x26345354
    background = RippleDrawable(ColorStateList.valueOf(ripple), backgroundStates, null)
    backgroundTintList = null
    stateListAnimator = null
}

private fun Context.buttonSurface(@ColorRes fill: Int, @ColorRes outline: Int) = GradientDrawable().apply {
    setColor(getColor(fill))
    setStroke(dp(1), getColor(outline))
    cornerRadius = dp(5).toFloat()
}

@StringRes
internal fun Attribute.label(): Int = when (this) {
    Attribute.STRENGTH -> R.string.strength
    Attribute.AGILITY -> R.string.agility
    Attribute.INSIGHT -> R.string.insight
    Attribute.CONSTITUTION -> R.string.constitution
}

@StringRes
internal fun Gender.label(): Int = when (this) {
    Gender.MALE -> R.string.male
    Gender.FEMALE -> R.string.female
}
