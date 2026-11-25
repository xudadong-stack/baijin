package com.baijin.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.baijin.game.core.Direction
import kotlin.math.abs
import kotlin.math.hypot

internal class VirtualJoystickView(context: Context) : View(context) {
    var onDirectionChanged: (Direction?) -> Unit = {}
    var onStep: (Direction) -> Unit = {}
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrow = Path()
    private val circle = RectF()
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var direction: Direction? = null
    private var thumbX = 0f
    private var thumbY = 0f
    private val radius get() = minOf(width, height) * 0.48f

    init {
        id = R.id.movement_joystick
        contentDescription = context.getString(R.string.movement_joystick)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        val r = radius
        if (r <= 0f) return
        val centerX = width / 2f
        val centerY = height / 2f
        canvas.save()
        canvas.translate(centerX, centerY)
        paint.style = Paint.Style.FILL
        paint.color = if (isEnabled) BASE else DISABLED
        canvas.drawCircle(0f, 0f, r, paint)
        circle.set(-r, -r, r, r)
        direction?.let {
            paint.color = HIGHLIGHT
            canvas.drawArc(circle, rotation(it) - 135f, 90f, true, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.026f
        paint.color = if (isFocused) IVORY else OUTLINE
        canvas.drawCircle(0f, 0f, r - paint.strokeWidth, paint)
        paint.style = Paint.Style.FILL
        paint.color = IVORY
        arrow.rewind()
        arrow.moveTo(0f, -0.82f * r)
        arrow.lineTo(0.15f * r, -0.53f * r)
        arrow.lineTo(-0.15f * r, -0.53f * r)
        arrow.close()
        Direction.entries.forEach {
            canvas.save()
            canvas.rotate(rotation(it))
            canvas.drawPath(arrow, paint)
            canvas.restore()
        }
        paint.color = THUMB_EDGE
        canvas.drawCircle(thumbX, thumbY, r * 0.36f, paint)
        paint.color = THUMB
        canvas.drawCircle(thumbX, thumbY, r * 0.31f, paint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val centerX = event.x - width / 2f
                val centerY = event.y - height / 2f
                if (hypot(centerX, centerY) > radius) return false
                pointerId = event.getPointerId(0)
                parent.requestDisallowInterceptTouchEvent(true)
                updateThumb(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index >= 0) updateThumb(event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointerId) release()
            }
            MotionEvent.ACTION_UP -> {
                release()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val pressed = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> Direction.NORTH
            KeyEvent.KEYCODE_DPAD_DOWN -> Direction.SOUTH
            KeyEvent.KEYCODE_DPAD_LEFT -> Direction.WEST
            KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.EAST
            else -> return super.onKeyDown(keyCode, event)
        }
        if (isEnabled) onStep(pressed)
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (isEnabled) {
            actions.forEach { (id, entry) ->
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(id, context.getString(entry.second)))
            }
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val step = actions[action]
        if (isEnabled && step != null) {
            onStep(step.first)
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    fun release() {
        pointerId = MotionEvent.INVALID_POINTER_ID
        thumbX = 0f
        thumbY = 0f
        setDirection(null)
        invalidate()
    }

    private fun updateThumb(x: Float, y: Float) {
        val dx = x - width / 2f
        val dy = y - height / 2f
        val r = radius
        val distance = hypot(dx, dy)
        if (distance < 0.23f * r) {
            thumbX = 0f
            thumbY = 0f
            setDirection(null)
        } else {
            val scale = minOf(distance, r * 0.43f) / distance
            thumbX = dx * scale
            thumbY = dy * scale
            val towardX = if (dx < 0f) Direction.WEST else Direction.EAST
            val towardY = if (dy < 0f) Direction.NORTH else Direction.SOUTH
            val step = when {
                abs(abs(dx) - abs(dy)) < r * 0.10f && direction in listOf(towardX, towardY) -> direction
                abs(dx) > abs(dy) -> towardX
                else -> towardY
            }
            setDirection(step)
        }
        invalidate()
    }

    private fun setDirection(next: Direction?) {
        if (direction == next) return
        direction = next
        onDirectionChanged(next)
    }

    private fun rotation(direction: Direction) = when (direction) {
        Direction.NORTH -> 0f
        Direction.EAST -> 90f
        Direction.SOUTH -> 180f
        Direction.WEST -> 270f
    }

    private companion object {
        val BASE = 0xB43E4846.toInt()
        val DISABLED = 0x88434A43.toInt()
        val OUTLINE = 0x589DA297
        val HIGHLIGHT = 0x506F817A
        val IVORY = 0xFFECE0C6.toInt()
        val THUMB = 0xD75E6B63.toInt()
        val THUMB_EDGE = 0xD43E4B45.toInt()
        val actions = mapOf(
            R.id.move_north to (Direction.NORTH to R.string.move_north),
            R.id.move_south to (Direction.SOUTH to R.string.move_south),
            R.id.move_west to (Direction.WEST to R.string.move_west),
            R.id.move_east to (Direction.EAST to R.string.move_east),
        )
    }
}
