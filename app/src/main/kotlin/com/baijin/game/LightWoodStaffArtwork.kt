package com.baijin.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.baijin.game.core.Gender

internal class LightWoodStaffArtwork {
    data class Hold(val x: Int, val y: Int, val angle: Int)

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    fun hold(gender: Gender, row: Int, frame: Int): Hold =
        (if (gender == Gender.MALE) maleHolds else femaleHolds)[row][frame]

    fun draw(canvas: Canvas, frame: RectF, hold: Hold) {
        val checkpoint = canvas.save()
        canvas.translate(frame.left, frame.top)
        canvas.scale(frame.width() / 256f, frame.height() / 320f)
        canvas.translate(hold.x.toFloat(), hold.y.toFloat())
        canvas.rotate(hold.angle.toFloat())

        ink.color = 0xFF49372A.toInt()
        ink.strokeWidth = 13f
        canvas.drawLine(0f, -112f, 0f, 61f, ink)
        ink.color = 0xFF946A40.toInt()
        ink.strokeWidth = 9f
        canvas.drawLine(0f, -112f, 0f, 61f, ink)
        ink.color = 0xFFE0B579.toInt()
        ink.strokeWidth = 2.5f
        canvas.drawLine(-2f, -110f, -2f, 58f, ink)

        canvas.restoreToCount(checkpoint)
    }

    companion object {
        const val ITEM_ID = "sample_light_wood_staff"

        // Right-hand positions in 256 × 320 atlas cells, ordered south, west, east, north.
        private val maleHolds = listOf(
            listOf(Hold(92, 223, -14), Hold(76, 219, -20), Hold(71, 219, -15)),
            listOf(Hold(102, 221, -24), Hold(76, 208, -31), Hold(69, 208, -28)),
            listOf(Hold(129, 225, 33), Hold(109, 225, 38), Hold(163, 208, 28)),
            listOf(Hold(180, 216, 14), Hold(181, 212, 18), Hold(172, 212, 16)),
        )
        private val femaleHolds = listOf(
            listOf(Hold(94, 222, -14), Hold(86, 209, -18), Hold(79, 208, -15)),
            listOf(Hold(105, 219, -24), Hold(76, 207, -31), Hold(69, 207, -28)),
            listOf(Hold(145, 222, 30), Hold(119, 225, 35), Hold(164, 207, 27)),
            listOf(Hold(186, 216, 14), Hold(175, 212, 18), Hold(170, 214, 16)),
        )
    }
}
