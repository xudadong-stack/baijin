package com.baijin.game

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ProgressBar
import android.widget.ScrollView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import com.baijin.game.core.ActionRequirement
import com.baijin.game.core.Direction
import com.baijin.game.core.EquipmentSlot
import com.baijin.game.core.EquipmentStats
import com.baijin.game.core.GameAction
import com.baijin.game.core.Gender
import com.baijin.game.core.InventoryEffect
import com.baijin.game.core.InventoryStack
import com.baijin.game.core.InventoryUse
import com.baijin.game.core.EquipmentSlot.*
import com.baijin.game.core.content.ItemDefinition
import com.baijin.game.core.content.ItemKind
import com.baijin.game.core.WeaponType

internal class InventoryDialog(private val context: Context, private val session: GameSession) {
    private enum class Category(@get:StringRes val label: Int) {
        ALL(R.string.inventory_filter_all), GEAR(R.string.equipment),
        SUPPLY(R.string.inventory_filter_supplies), OTHER(R.string.inventory_filter_other);

        fun includes(stack: InventoryStack): Boolean = when (this) {
            ALL -> true
            GEAR -> stack.item.equipment != null
            SUPPLY -> stack.use != null
            OTHER -> stack.item.equipment == null && stack.use == null
        }
    }

    private enum class Metric(
        @get:StringRes val label: Int, val rate: Boolean = false, val value: (EquipmentStats) -> Int,
    ) {
        ATTACK(R.string.equipment_power, value = EquipmentStats::power),
        HIT(R.string.equipment_hit, true, EquipmentStats::hit),
        PARRY(R.string.equipment_parry, true, EquipmentStats::parry),
        PROTECTION(R.string.equipment_protection, value = EquipmentStats::protection),
        EVASION(R.string.equipment_evasion, true, EquipmentStats::evasion);

        fun format(number: Int): String = (if (number > 0) "+" else "") + number + if (rate) "%" else ""
    }

    private var dialog: Dialog? = null
    private var root: LinearLayout? = null
    private var bagScroll: ScrollView? = null
    private var width = 0
    private var compact = false
    private var equipmentPage = false
    private var category = Category.ALL
    private var bagItem: String? = null
    private var slot = WEAPON
    private var candidate: String? = null
    private val portraitArtwork by lazy { TravellerArtwork(context) }
    private val paper get() = context.getColor(R.color.paper)
    private val recess get() = context.getColor(R.color.paper_shade)
    private val line get() = context.getColor(R.color.paper_line)
    private val teal get() = context.getColor(R.color.action_ink)

    fun show(game: SaveSlot.ReadResult.Available, viewport: View) {
        dismiss()
        equipmentPage = false
        category = Category.ALL
        bagItem = null
        slot = WEAPON
        candidate = null
        width = minOf(context.dp(864), viewport.width - context.dp(20))
        val height = minOf(context.dp(400), viewport.height - context.dp(16))
        compact = height < context.dp(345)
        val content = context.column().apply {
            background = surface(paper, line, 9)
            clipToOutline = true
        }
        root = content
        dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            setCanceledOnTouchOutside(true)
            setOnDismissListener { if (dialog === this) clear() }
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.46f)
            }
            show()
            window?.setLayout(width, height)
        }
        render(game, locked = false, notice = null)
    }

    fun dismiss() {
        val previous = dialog
        clear()
        previous?.dismiss()
    }

    private fun clear() {
        dialog = null
        root = null
        bagScroll = null
    }

    fun render(game: SaveSlot.ReadResult.Available, locked: Boolean, notice: String?, resetScroll: Boolean = false) {
        val content = root ?: return
        val scrollY = if (resetScroll) 0 else bagScroll?.scrollY ?: 0
        bagScroll = null
        content.removeAllViews()
        content.addView(header(game, locked, notice), LayoutParams(MATCH_PARENT, context.dp(if (compact) 46 else 52)))
        content.addView(divider(), LayoutParams(MATCH_PARENT, context.dp(1)))
        content.addView(
            if (equipmentPage) equipment(game, locked, notice) else bag(game, locked, notice),
            LayoutParams(MATCH_PARENT, 0, 1f),
        )
        bagScroll?.let { scroll -> scroll.post { scroll.scrollTo(0, scrollY) } }
    }

    private fun header(game: SaveSlot.ReadResult.Available, locked: Boolean, notice: String?) = row().apply {
        setBackgroundColor(0xFFF6EBD6.toInt())
        setPadding(context.dp(16), 0, context.dp(6), 0)
        fun showPage(gear: Boolean) {
            if (equipmentPage != gear) {
                equipmentPage = gear
                render(game, locked, notice)
            }
        }
        addView(tab(R.string.inventory, !equipmentPage, true) { showPage(false) },
            LayoutParams(context.dp(70), MATCH_PARENT))
        addView(tab(R.string.equipment, equipmentPage, true) { showPage(true) },
            LayoutParams(context.dp(70), MATCH_PARENT).apply { marginStart = context.dp(8) })
        addView(View(context), LayoutParams(0, 1, 1f))
        if (!equipmentPage) addView(context.gameText(13f).apply {
            text = context.getString(R.string.coins, game.state.coins)
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        addView(context.gameText(26f, R.color.muted_ink).apply {
            text = "×"
            gravity = Gravity.CENTER
            contentDescription = context.getString(R.string.close)
            interactive { dismiss() }
        }, LayoutParams(context.dp(48), MATCH_PARENT))
    }

    private fun bag(game: SaveSlot.ReadResult.Available, locked: Boolean, notice: String?): View {
        val all = game.exploration.inventory.stacks
        val items = all.filter(category::includes).sortedBy {
            when {
                it.use != null -> 0
                it.item.equipment == null -> 1
                else -> 2
            }
        }
        val selected = items.firstOrNull { it.item.id == bagItem } ?: items.firstOrNull()
        bagItem = selected?.item?.id
        val columns = ((width * BAG_FRACTION - context.dp(28)) / context.dp(68)).toInt().coerceAtLeast(2)
        val sidebar = context.column().apply {
            setPadding(context.dp(14), context.dp(4), context.dp(12), context.dp(6))
            val filters = row().apply {
                Category.entries.forEach { entry ->
                    addView(tab(entry.label, entry == category) {
                        if (category != entry) {
                            category = entry
                            render(game, locked, notice, resetScroll = true)
                        }
                    }, LayoutParams(WRAP_CONTENT, MATCH_PARENT))
                }
            }
            addView(hscroll(filters), LayoutParams(MATCH_PARENT, context.dp(44)))
            addView(divider(), LayoutParams(MATCH_PARENT, context.dp(1)))
            val grid = context.column().apply {
                items.chunked(columns).forEach { group ->
                    addView(row().apply {
                        group.forEach { stack ->
                            addView(itemButton(stack, stack == selected, showQuantity = true) {
                                bagItem = stack.item.id
                                render(game, locked, null)
                            }, LayoutParams(0, WRAP_CONTENT, 1f))
                        }
                        repeat(columns - group.size) { addView(View(context), LayoutParams(0, 1, 1f)) }
                    }, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = context.dp(3) })
                }
                if (items.isEmpty()) line(context.getString(
                    if (all.isEmpty()) R.string.inventory_empty else R.string.inventory_empty_category,
                ), 13f, R.color.muted_ink, 10)
            }
            addView(scroll(grid).also { bagScroll = it }, LayoutParams(MATCH_PARENT, 0, 1f))
        }
        val button = selected?.let { stack ->
            val equipment = stack.item.equipment
            when {
                equipment != null -> footerAction(R.string.inventory_view_slot, locked) {
                    slot = equipment.slot
                    candidate = stack.item.id.takeUnless { stack.worn }
                    equipmentPage = true
                    render(game, locked, null)
                }
                else -> stack.use?.let { use -> supplyAction(stack.item.id, use, locked) }
            }
        }
        val detail = detail(notice, button) {
            selected?.let { showBagItem(it) }
        }
        return split(sidebar, detail, BAG_FRACTION)
    }

    private fun supplyAction(itemId: String, use: InventoryUse, locked: Boolean): View {
        val food = use.effect == InventoryEffect.FOOD
        val blocked = use.current == use.after
        val label = when {
            !blocked -> if (food) R.string.eat_one else R.string.inventory_use_one
            food -> R.string.inventory_not_hungry
            use.current == use.maximum -> R.string.inventory_full_health
            else -> R.string.inventory_unavailable
        }
        return footerAction(label, locked || blocked, if (food) R.id.eat_food else View.NO_ID) {
            session.play(if (food) GameAction.Eat(itemId) else GameAction.UseMedicine(itemId))
        }
    }

    private fun LinearLayout.showBagItem(stack: InventoryStack) {
        val item = stack.item
        val equipment = item.equipment
        heading(item.name, descriptionLabel(stack), tile(icon(item)))
        val quantity = if (stack.worn) context.getString(R.string.inventory_worn_count, stack.quantity, stack.available)
            else context.getString(R.string.inventory_owned, stack.quantity)
        line(quantity, 13f, R.color.muted_ink, gap = 10)
        val description = when (stack.use?.effect) {
            InventoryEffect.FOOD -> context.getString(R.string.inventory_food_description, checkNotNull(stack.use).amount)
            InventoryEffect.HEAL -> context.getString(R.string.inventory_heal_description, checkNotNull(stack.use).amount)
            null -> item.description
        }
        description?.let { line(it, 13f, gap = 8) }
        stack.use?.let { readings(it) }
        if (equipment != null) {
            section(R.string.equipment_attributes)
            stats(equipment.stats)
            stack.equipBlock?.let { requirement(it) }
        }
    }

    private fun equipment(game: SaveSlot.ReadResult.Available, locked: Boolean, notice: String?): View {
        val inventory = game.exploration.inventory
        val stacks = inventory.stacks
        val current = stacks.firstOrNull { it.item.id == inventory.equipped[slot] }
        val options = stacks.filter { it.item.equipment?.slot == slot && !it.worn }
        val selected = options.firstOrNull { it.item.id == candidate }
        candidate = selected?.item?.id
        val shown = selected ?: current
        val action = when {
            selected != null -> footerAction(
                when {
                    selected.equipBlock != null -> R.string.equipment_conditions_missing
                    current == null -> R.string.equipment
                    else -> R.string.equipment_replace
                }, locked || selected.equipBlock != null,
            ) { session.play(GameAction.Equip(selected.item.id)) }
            current != null -> footerAction(R.string.equipment_remove, locked, primary = false) {
                session.play(GameAction.Unequip(slot, current.item.id))
            }
            else -> null
        }
        val detail = detail(notice, action) {
            val label = context.getString(slot.label())
            val status = when {
                selected != null && current != null -> context.getString(R.string.equipment_current, current.item.name)
                selected != null && selected.equipBlock == null -> context.getString(R.string.equipment_can_equip)
                current != null -> context.getString(R.string.equipment_worn)
                else -> context.getString(R.string.equipment_not_worn)
            }
            val subtitle = if (shown == null) status else context.getString(R.string.equipment_slot_title, label, status)
            heading(shown?.item?.name ?: label, subtitle,
                tile(shown?.item?.let(::icon) ?: slot.emptyIcon(), empty = shown == null))
            shown?.item?.equipment?.let { stats(it.stats, current?.item?.equipment?.stats?.takeIf { selected != null }) }
            val block = if (!inventory.equipmentUnlocked) ActionRequirement.EquipmentLocked else selected?.equipBlock
            block?.let { requirement(it) }
            section(R.string.equipment_candidates)
            if (options.isEmpty()) {
                line(context.getString(R.string.equipment_no_candidates), 12f, R.color.muted_ink, 6)
            } else addView(hscroll(row().apply {
                options.forEach { option ->
                    addView(itemButton(option, option == selected, showQuantity = false) {
                        candidate = option.item.id
                        render(game, locked, null)
                    }, LayoutParams(context.dp(66), WRAP_CONTENT))
                }
            }), LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        val rack = rack(game) { chosen ->
            slot = chosen
            candidate = null
            render(game, locked, null)
        }
        return split(rack, detail, 0.515f)
    }

    private fun rack(game: SaveSlot.ReadResult.Available, onSelect: (EquipmentSlot) -> Unit) =
        context.column().apply {
            setPadding(context.dp(12), context.dp(4), context.dp(12), 0)
            val inventory = game.exploration.inventory
            fun control(key: EquipmentSlot): View {
                val item = inventory.stacks.firstOrNull { it.item.id == inventory.equipped[key] }?.item
                val name = item?.name ?: context.getString(R.string.equipment_empty_slot)
                val label = context.getString(key.label())
                return context.column().apply {
                    gravity = Gravity.CENTER
                    isSelected = slot == key
                    contentDescription = context.getString(R.string.equipment_slot_title, label, name)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    interactive { onSelect(key) }
                    addView(context.gameText(12f, if (isSelected) R.color.action_ink else R.color.muted_ink).apply {
                        text = label
                        gravity = Gravity.CENTER
                        setTypeface(typeface, Typeface.BOLD)
                        includeFontPadding = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    })
                    val size = context.dp(if (compact) 40 else 48)
                    addView(tile(item?.let(::icon) ?: key.emptyIcon(), isSelected, item == null),
                        LayoutParams(size, size).apply { topMargin = context.dp(2) })
                    addView(itemName(name, isSelected))
                }
            }
            val left = context.column().apply {
                listOf(HEAD, BODY, FEET).forEach { addView(control(it), LayoutParams(MATCH_PARENT, 0, 1f)) }
            }
            val right = context.column().apply {
                addView(View(context), LayoutParams(MATCH_PARENT, 0, 0.48f))
                listOf(WEAPON, HANDS).forEach { addView(control(it), LayoutParams(MATCH_PARENT, 0, 1f)) }
                addView(View(context), LayoutParams(MATCH_PARENT, 0, 0.52f))
            }
            addView(row().apply {
                addView(left, LayoutParams(0, MATCH_PARENT, 1f))
                addView(portrait(game.state.character.gender, inventory.equipped), LayoutParams(0, MATCH_PARENT, 1.45f).apply {
                    topMargin = context.dp(12)
                    bottomMargin = context.dp(10)
                })
                addView(right, LayoutParams(0, MATCH_PARENT, 1f))
            }, LayoutParams(MATCH_PARENT, 0, 1f))
            addView(divider(), LayoutParams(MATCH_PARENT, context.dp(1)))
            val totals = row().apply {
                gravity = Gravity.CENTER
                Metric.entries.filter { it.value(inventory.stats) != 0 || it == Metric.ATTACK || it == Metric.PROTECTION }
                    .forEach { metric ->
                        addView(context.gameText(11f, R.color.action_ink).apply {
                            text = metricText(metric, metric.value(inventory.stats))
                            setTypeface(typeface, Typeface.BOLD)
                            setSingleLine()
                            setPadding(context.dp(5), 0, context.dp(5), 0)
                        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
                    }
            }
            addView(hscroll(totals).apply { isFillViewport = true },
                LayoutParams(MATCH_PARENT, context.dp(if (compact) 32 else 39)))
        }

    private fun portrait(gender: Gender, equipped: Map<EquipmentSlot, String>) = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x28675843 }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val center = width / 2f
            val feet = height * 0.91f
            val size = minOf(height * 0.82f, width * 1.65f)
            canvas.drawOval(center - size * 0.24f, feet - size * 0.028f,
                center + size * 0.24f, feet + size * 0.018f, shadow)
            portraitArtwork.draw(canvas, gender, Direction.SOUTH, 0, center, feet, size, 1f, paint, equipped)
        }
    }.apply {
        contentDescription = context.getString(R.string.equipment_portrait)
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x36BCAA85, 0x00BCAA85)).apply { cornerRadius = context.dp(60).toFloat() }
    }

    private fun descriptionLabel(stack: InventoryStack): String {
        val item = stack.item
        item.equipment?.let {
            return context.getString(R.string.equipment_slot_title, context.getString(R.string.equipment),
                context.getString(it.slot.label()))
        }
        return context.getString(when {
            stack.use != null -> R.string.inventory_filter_supplies
            item.kind == ItemKind.QUEST -> R.string.inventory_quest_item
            item.kind == ItemKind.MATERIAL -> R.string.inventory_material
            else -> R.string.inventory_other
        })
    }

    private fun LinearLayout.heading(name: String, subtitle: String, picture: View) {
        addView(row().apply {
            addView(picture, LayoutParams(context.dp(49), context.dp(49)))
            addView(context.column().apply {
                addView(context.gameText(23f).apply {
                    useDisplayTypeface()
                    text = name
                    ViewCompat.setAccessibilityHeading(this, true)
                })
                line(subtitle, 12f, R.color.muted_ink)
            }, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = context.dp(10) })
        }, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun LinearLayout.section(@StringRes title: Int) {
        addView(divider(), LayoutParams(MATCH_PARENT, context.dp(1)).apply { topMargin = context.dp(12) })
        line(context.getString(title), 13f, R.color.muted_ink, gap = 7, bold = true)
    }

    private fun LinearLayout.readings(use: InventoryUse) {
        val food = use.effect == InventoryEffect.FOOD
        section(if (food) R.string.hunger else R.string.health)
        addView(row().apply {
            addView(context.gameText(14f).apply {
                text = context.getString(R.string.inventory_effect_before, use.current, use.maximum)
                setTypeface(typeface, Typeface.BOLD)
            })
            if (use.after != use.current) addView(context.gameText(14f, R.color.action_ink).apply {
                text = context.getString(R.string.inventory_effect_after, use.after, use.maximum)
                setPadding(context.dp(10), 0, 0, 0)
            })
        }, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(5) })
        addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = use.maximum
            progress = use.current
            progressTintList = ColorStateList.valueOf(context.getColor(if (food) R.color.hunger_ink else R.color.health_ink))
            progressBackgroundTintList = ColorStateList.valueOf(recess)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(MATCH_PARENT, context.dp(6)).apply { topMargin = context.dp(6) })
    }

    private fun LinearLayout.requirement(requirement: ActionRequirement) =
        line(context.requirementText(requirement), 12f, R.color.health_ink, gap = 6)

    private fun LinearLayout.stats(values: EquipmentStats, comparedTo: EquipmentStats? = null) {
        val chips = row()
        Metric.entries.forEach { metric ->
            val value = metric.value(values)
            val previous = comparedTo?.let(metric.value) ?: 0
            if (value == 0 && previous == 0) return@forEach
            val delta = value - previous
            val display = metricText(metric, value)
            chips.addView(context.gameText(12f, if (comparedTo != null && delta < 0) R.color.health_ink else R.color.ink).apply {
                text = if (comparedTo != null && delta != 0)
                    context.getString(R.string.equipment_stat_delta, display, metric.format(delta)) else display
                setPadding(context.dp(6), context.dp(3), context.dp(6), context.dp(3))
                background = surface(recess, line, 4)
                setSingleLine()
            }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = context.dp(4) })
        }
        if (chips.childCount > 0) addView(hscroll(chips), LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = context.dp(8)
        })
    }

    private fun metricText(metric: Metric, value: Int) =
        context.getString(R.string.equipment_stat_value, context.getString(metric.label), metric.format(value))

    private fun split(left: View, right: View, fraction: Float) = row().apply {
        addView(left, LayoutParams(0, MATCH_PARENT, fraction))
        addView(divider(), LayoutParams(context.dp(1), MATCH_PARENT))
        addView(right, LayoutParams(0, MATCH_PARENT, 1f - fraction))
    }

    private fun detail(notice: String?, button: View?, draw: LinearLayout.() -> Unit) = context.column().apply {
        setBackgroundColor(0xFFF5E9D2.toInt())
        setPadding(context.dp(16), context.dp(if (compact) 10 else 15), context.dp(16), context.dp(9))
        addView(scroll(context.column().apply(draw)), LayoutParams(MATCH_PARENT, 0, 1f))
        if (button != null || notice != null) addView(row().apply {
            addView(context.gameText(12f, R.color.action_ink).apply {
                text = notice.orEmpty()
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8) })
            button?.let { addView(it, LayoutParams(WRAP_CONTENT, WRAP_CONTENT)) }
        }, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(4) })
    }

    private fun footerAction(
        @StringRes label: Int, locked: Boolean, viewId: Int = View.NO_ID, primary: Boolean = true, onClick: () -> Unit,
    ) = context.gameButton(label, primary).apply {
        id = viewId
        minWidth = context.dp(90)
        isEnabled = !locked
        setOnClickListener { onClick() }
    }

    private fun tab(@StringRes label: Int, selected: Boolean, heading: Boolean = false, onClick: () -> Unit) =
        context.column().apply {
            isSelected = selected
            minimumWidth = context.dp(44)
            contentDescription = context.getString(label)
            interactive(onClick)
            addView(context.gameText(if (heading) 20f else 13f, if (selected) R.color.action_ink else R.color.muted_ink).apply {
                setText(label)
                setSingleLine()
                gravity = Gravity.CENTER
                setPadding(context.dp(7), 0, context.dp(7), 0)
                if (heading) useDisplayTypeface()
                if (selected) setTypeface(typeface, Typeface.BOLD)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(MATCH_PARENT, 0, 1f))
            addView(View(context).apply { setBackgroundColor(if (selected) teal else Color.TRANSPARENT) },
                LayoutParams(MATCH_PARENT, context.dp(2)))
        }

    private fun itemButton(stack: InventoryStack, selected: Boolean, showQuantity: Boolean, onClick: () -> Unit) =
        context.column().apply {
            gravity = Gravity.CENTER
            val item = stack.item
            isSelected = selected
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = if (showQuantity) context.getString(
                if (stack.worn) R.string.inventory_worn_accessibility else R.string.inventory_item_accessibility,
                item.name, stack.quantity,
            ) else item.name
            interactive(onClick)
            val size = context.dp(if (showQuantity && !compact) 52 else 46)
            val picture = tile(icon(item), selected, quantity = stack.quantity.takeIf { showQuantity },
                worn = showQuantity && stack.worn, blocked = stack.equipBlock != null &&
                    stack.equipBlock != ActionRequirement.EquipmentLocked)
            addView(picture, LayoutParams(size, size).apply { topMargin = context.dp(4) })
            addView(itemName(item.name, selected), LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

    private fun itemName(name: String, selected: Boolean) =
        context.gameText(11f, if (selected) R.color.action_ink else R.color.ink).apply {
            text = name
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            if (selected) setTypeface(typeface, Typeface.BOLD)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    private fun tile(
        @DrawableRes drawable: Int, selected: Boolean = false, empty: Boolean = false,
        quantity: Int? = null, worn: Boolean = false, blocked: Boolean = false,
    ) = FrameLayout(context).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        background = when {
            empty -> GradientDrawable().apply {
                setColor(if (selected) 0xFFD6DFD0.toInt() else 0xFFDCCCA9.toInt())
                val color = if (selected) teal else 0xFF9C8B6D.toInt()
                setStroke(context.dp(if (selected) 2 else 1), color, context.dp(3).toFloat(), context.dp(2).toFloat())
                cornerRadius = context.dp(5).toFloat()
            }
            selected -> surface(0xFFD6DFD0.toInt(), teal, 5, 2)
            else -> GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFF8ECD2.toInt(), recess)).apply {
                setStroke(context.dp(1), 0xFFA28D68.toInt())
                cornerRadius = context.dp(5).toFloat()
            }
        }
        addView(ImageView(context).apply { setImageResource(drawable) },
            FrameLayout.LayoutParams(context.dp(34), context.dp(34), Gravity.CENTER).apply {
                if (quantity != null) bottomMargin = context.dp(4)
            })
        if (quantity != null) addView(context.gameText(10f).apply {
            text = context.getString(R.string.inventory_stack_count, quantity)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setSingleLine()
            background = surface(0xFFF9EFD9.toInt(), radius = 2)
            setPadding(context.dp(2), 0, context.dp(2), 0)
        }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = context.dp(2)
            marginStart = context.dp(2)
            bottomMargin = context.dp(2)
        })
        if (worn) addView(View(context).apply { background = surface(teal, radius = 6) },
            FrameLayout.LayoutParams(context.dp(6), context.dp(6), Gravity.BOTTOM or Gravity.START).apply {
                bottomMargin = context.dp(4)
                marginStart = context.dp(4)
            })
        if (blocked) addView(ImageView(context).apply {
            setImageResource(android.R.drawable.ic_lock_lock)
            imageTintList = ColorStateList.valueOf(context.getColor(R.color.muted_ink))
        }, FrameLayout.LayoutParams(context.dp(13), context.dp(13), Gravity.TOP or Gravity.END).apply {
            topMargin = context.dp(3)
            marginEnd = context.dp(3)
        })
    }

    private fun row() = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private fun divider() = View(context).apply { setBackgroundColor(line) }
    private fun scroll(body: View) = ScrollView(context).apply {
        isFillViewport = true
        addView(body, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
    private fun hscroll(body: View) = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(body)
    }

    private fun LinearLayout.line(value: String, size: Float, color: Int = R.color.ink, gap: Int = 0, bold: Boolean = false) {
        addView(context.gameText(size, color).apply {
            text = value
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(gap) })
    }

    private fun View.interactive(onClick: () -> Unit) {
        isFocusable = true
        foreground = RippleDrawable(
            ColorStateList.valueOf(0x25345354),
            StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), surface(Color.TRANSPARENT, teal, 4))
                addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
            }, surface(Color.WHITE, radius = 4),
        )
        setOnClickListener { onClick() }
    }

    private fun surface(fill: Int, border: Int? = null, radius: Int, stroke: Int = 1) = GradientDrawable().apply {
        setColor(fill)
        border?.let { setStroke(context.dp(stroke), it) }
        cornerRadius = context.dp(radius).toFloat()
    }

    companion object {
        private const val BAG_FRACTION = 0.55f
    }
}

@StringRes
private fun EquipmentSlot.label(): Int = when (this) {
    WEAPON -> R.string.equipment_weapon
    HEAD -> R.string.equipment_head
    BODY -> R.string.equipment_body
    HANDS -> R.string.equipment_hands
    FEET -> R.string.equipment_feet
}

@DrawableRes
private fun EquipmentSlot.emptyIcon(): Int = when (this) {
    WEAPON -> R.drawable.item_sword_empty
    HEAD -> R.drawable.item_cap_empty
    BODY -> R.drawable.item_coat_empty
    HANDS -> R.drawable.item_bracers_empty
    FEET -> R.drawable.item_shoes_empty
}

@DrawableRes
private fun icon(item: ItemDefinition): Int {
    val gear = item.equipment
    return when (gear?.slot) {
        WEAPON -> when {
            gear.weaponType == WeaponType.STAFF -> R.drawable.item_staff
            item.id == "willow_sword" -> R.drawable.item_willow
            else -> R.drawable.item_sword
        }
        HEAD -> R.drawable.item_cap
        BODY -> R.drawable.item_coat
        HANDS -> R.drawable.item_bracers
        FEET -> R.drawable.item_shoes
        null -> when {
            item.healthRecovery != null -> R.drawable.item_medicine
            item.id == "ration" -> R.drawable.item_ration
            item.id == "crucian_carp" -> R.drawable.item_fish
            item.kind == ItemKind.MATERIAL -> R.drawable.item_cloth
            else -> R.drawable.item_token
        }
    }
}
