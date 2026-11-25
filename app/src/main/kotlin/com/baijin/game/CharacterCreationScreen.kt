package com.baijin.game

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.baijin.game.core.Attribute
import com.baijin.game.core.Gender

internal class CharacterCreationScreen(private val context: Context, private val session: GameSession) {
    val view = ScrollView(context).apply { isFillViewport = true }
    private val gender = RadioGroup(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val male = genderOption(Gender.MALE, R.id.gender_male)
    private val female = genderOption(Gender.FEMALE, R.id.gender_female)
    private val portrait = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val remaining = context.gameText(16f).apply { id = R.id.remaining_points }
    private val balance = context.gameButton(R.string.balance_attributes).apply {
        id = R.id.balance_attributes
        setOnClickListener { session.balanceAttributes() }
    }
    private val confirm = context.gameButton(R.string.confirm_character, primary = true).apply {
        id = R.id.confirm_character
        setOnClickListener { session.confirmCharacter() }
    }
    private val back = context.gameButton(R.string.menu).apply {
        id = R.id.return_to_menu
        setOnClickListener { session.returnToMenu() }
    }
    private val notice = context.gameText(14f, R.color.muted_ink).apply {
        id = R.id.action_notice
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val retry = context.gameButton(R.string.retry_save).apply {
        id = R.id.retry_save
        setOnClickListener { session.retrySave() }
    }
    private val attributes = Attribute.entries.associateWith(::attributeRow)

    init {
        val details = context.column().apply {
            gender.addView(male, RadioGroup.LayoutParams(0, WRAP_CONTENT, 1f))
            gender.addView(female, RadioGroup.LayoutParams(0, WRAP_CONTENT, 1f))
            val identity = context.column().apply {
                addView(context.gameText(30f).apply {
                    setText(R.string.create_character)
                    useDisplayTypeface()
                })
                addView(context.gameText(14f, R.color.muted_ink).apply { setText(R.string.sex) }, fullWidth(10))
                addView(gender, fullWidth())
            }
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(identity, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(portrait, LinearLayout.LayoutParams(context.dp(78), context.dp(114)))
            }, fullWidth())
            addView(context.gameText(13f, R.color.muted_ink).apply {
                setText(R.string.character_requirements)
            }, fullWidth(10))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(remaining, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(balance, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            }, fullWidth(10))
            addView(confirm, fullWidth(8))
            addView(back, fullWidth(6))
            addView(notice, fullWidth(8))
            addView(retry, fullWidth(6))
        }
        val points = context.column().apply {
            attributes.values.forEachIndexed { index, row ->
                addView(row.layout, fullWidth())
                if (index < attributes.size - 1) {
                    addView(View(context).apply { setBackgroundColor(context.getColor(R.color.paper_line)) },
                        LinearLayout.LayoutParams(MATCH_PARENT, context.dp(1)))
                }
            }
        }
        val form = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(24), context.dp(12), context.dp(24), context.dp(12))
            addView(details, LinearLayout.LayoutParams(0, WRAP_CONTENT, 2f))
            addView(points, LinearLayout.LayoutParams(0, WRAP_CONTENT, 3f).apply { marginStart = context.dp(32) })
        }
        view.addView(form, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    fun render(creation: GameSession.Screen.Creating, locked: Boolean, message: String?, canRetry: Boolean) {
        val rule = creation.rules
        val remainingPoints = rule.remainingPoints(creation.attributes)
        when (creation.gender) {
            Gender.MALE -> gender.check(male.id)
            Gender.FEMALE -> gender.check(female.id)
            null -> gender.clearCheck()
        }
        portrait.setImageResource(if (creation.gender == Gender.FEMALE) R.drawable.town_female else R.drawable.town_male)
        portrait.alpha = if (creation.gender == null) 0.40f else 1f
        male.isEnabled = !locked
        female.isEnabled = !locked
        balance.isEnabled = !locked && creation.attributes != rule.balancedAttributes
        remaining.text = context.getString(R.string.remaining_points, remainingPoints, rule.attributePoints)
        attributes.forEach { (attribute, row) ->
            val value = creation.attributes[attribute]
            row.value.text = context.getString(R.string.integer_value, value)
            row.value.contentDescription = context.getString(R.string.attribute_value, context.getString(attribute.label()), value)
            row.decrement.isEnabled = !locked && value > rule.minAttribute
            row.increment.isEnabled = !locked && value < rule.maxAttribute && remainingPoints > 0
        }
        confirm.isEnabled = !locked && creation.gender != null && remainingPoints == 0L
        back.isEnabled = !locked
        notice.text = message.orEmpty()
        notice.visibility = if (message == null) View.GONE else View.VISIBLE
        retry.visibility = if (canRetry) View.VISIBLE else View.GONE
    }

    private fun genderOption(value: Gender, viewId: Int) = RadioButton(context).apply {
        id = viewId
        minHeight = context.dp(48)
        setText(value.label())
        setTextColor(context.getColor(R.color.ink))
        textSize = 16f
        setOnClickListener { session.chooseGender(value) }
    }

    private fun attributeRow(attribute: Attribute): AttributeRow {
        val effect = when (attribute) {
            Attribute.STRENGTH -> R.string.strength_effect
            Attribute.AGILITY -> R.string.agility_effect
            Attribute.INSIGHT -> R.string.insight_effect
            Attribute.CONSTITUTION -> R.string.constitution_effect
        }
        val title = context.getString(attribute.label())
        val label = context.column().apply {
            addView(context.gameText(18f).apply { text = title })
            addView(context.gameText(13f, R.color.muted_ink).apply { setText(effect) })
        }
        val decrement = context.gameButton(R.string.decrement).apply {
            contentDescription = context.getString(R.string.subtract_attribute, title)
            textSize = 19f
            setOnClickListener { session.adjustAttribute(attribute, -1) }
        }
        val increment = context.gameButton(R.string.increment).apply {
            contentDescription = context.getString(R.string.add_attribute, title)
            textSize = 19f
            setOnClickListener { session.adjustAttribute(attribute, 1) }
        }
        val value = context.gameText(18f).apply { gravity = Gravity.CENTER }
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(8), 0, context.dp(8))
            addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8) })
            addView(decrement, LinearLayout.LayoutParams(context.dp(48), WRAP_CONTENT))
            addView(value, LinearLayout.LayoutParams(context.dp(32), WRAP_CONTENT))
            addView(increment, LinearLayout.LayoutParams(context.dp(48), WRAP_CONTENT))
        }
        return AttributeRow(row, value, decrement, increment)
    }

    private fun fullWidth(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
        topMargin = context.dp(top)
    }

    private data class AttributeRow(val layout: View, val value: TextView, val decrement: Button, val increment: Button)
}
