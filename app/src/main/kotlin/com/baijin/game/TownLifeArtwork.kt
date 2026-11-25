package com.baijin.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.baijin.game.core.content.SceneDefinition
import com.baijin.game.core.content.SceneryKind
import kotlin.math.PI
import kotlin.math.sin

/** Small household details in map units; animation never advances game state. */
internal class TownLifeArtwork {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val signInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val path = Path()

    fun draw(canvas: Canvas, sprite: TownArtwork.Sprite, height: Float, phase: Float) {
        when (sprite.detail) {
            SceneryKind.LAUNDRY, SceneryKind.VEGETABLE_PATCH, SceneryKind.GRAIN_MAT, SceneryKind.STOVE,
            SceneryKind.SHOP_SIGN, SceneryKind.HALL, SceneryKind.COURTHOUSE_GATE -> Unit
            else -> return
        }
        canvas.save()
        canvas.translate(sprite.centerX, sprite.feetY)
        when (sprite.detail) {
            SceneryKind.LAUNDRY -> laundry(canvas, sprite.width, height, phase)
            SceneryKind.VEGETABLE_PATCH -> vegetables(canvas, sprite.width, height)
            SceneryKind.GRAIN_MAT -> grain(canvas, sprite.width, height)
            SceneryKind.STOVE -> steam(canvas, sprite.width, height, phase)
            SceneryKind.SHOP_SIGN -> sprite.inscription?.let { hangingSign(canvas, it, sprite.width, height) }
            SceneryKind.HALL -> sprite.inscription?.let { plaque(canvas, it, height, .57f) }
            SceneryKind.COURTHOUSE_GATE -> sprite.inscription?.let { plaque(canvas, it, height, .61f) }
            else -> Unit
        }
        canvas.restore()
    }

    fun pose(canvas: Canvas, sprite: TownArtwork.Sprite, phase: Float): Boolean {
        val wave = sin(phase * PI * 2 + sprite.centerX).toFloat()
        when (sprite.detail) {
            SceneryKind.ROOSTER, SceneryKind.HEN -> {
                canvas.save()
                canvas.translate(wave * sprite.width * .18f, -kotlin.math.abs(wave) * .018f)
                canvas.rotate(wave * 2.5f, sprite.centerX, sprite.feetY)
            }
            SceneryKind.SLEEPING_CAT, SceneryKind.GROOMING_CAT -> {
                canvas.save()
                canvas.scale(1f, 1f + wave * .012f, sprite.centerX, sprite.feetY)
            }
            else -> return false
        }
        return true
    }

    private fun hangingSign(canvas: Canvas, inscription: String, width: Float, height: Float) {
        signInk.color = 0xFF4D3929.toInt()
        signInk.textSize = width * .39f
        inscription.forEachIndexed { index, letter ->
            canvas.drawText(letter.toString(), -width * .10f, -height * .47f + index * width * .44f, signInk)
        }
    }

    private fun plaque(canvas: Canvas, inscription: String, height: Float, aboveSill: Float) {
        val center = -height * aboveSill
        signInk.color = 0xFF493A30.toInt()
        canvas.drawRoundRect(-.46f, center - .16f, .46f, center + .16f, .035f, .035f, signInk)
        signInk.color = 0xFF96795A.toInt()
        canvas.drawRoundRect(-.42f, center - .13f, .42f, center + .13f, .025f, .025f, signInk)
        signInk.color = 0xFF544136.toInt()
        canvas.drawRoundRect(-.39f, center - .105f, .39f, center + .105f, .02f, .02f, signInk)
        signInk.color = 0xFFF1E4CD.toInt()
        signInk.textSize = .22f
        val baseline = center - (signInk.ascent() + signInk.descent()) / 2f
        val middle = (inscription.length - 1) / 2f
        inscription.forEachIndexed { index, letter ->
            canvas.drawText(letter.toString(), (index - middle) * .28f, baseline, signInk)
        }
    }

    fun water(canvas: Canvas, scene: SceneDefinition, phase: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .017f
        paint.strokeCap = Paint.Cap.ROUND
        scene.terrain.forEachIndexed { y, row -> row.forEachIndexed { x, tile ->
            if (tile == 'w' && (x + y) % 3 == 0) {
                val wave = sin((phase * 2 * PI + x * .7 + y)).toFloat()
                paint.color = 0xFFE4EAD2.toInt()
                paint.alpha = (65 + wave * 30).toInt()
                val cx = x + .5f + wave * .025f
                val cy = y + .5f
                canvas.drawArc(cx - .12f, cy - .12f, cx + .12f, cy + .06f, 20f, 125f, false, paint)
                canvas.drawLine(cx - .055f, cy + .11f, cx + .06f, cy + .11f, paint)
            }
        } }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    private fun laundry(canvas: Canvas, width: Float, height: Float, phase: Float) {
        val half = width / 2
        paint.color = 0xFF8C7557.toInt()
        paint.strokeWidth = .045f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(-half, 0f, -half, -height, paint)
        canvas.drawLine(half, 0f, half, -height, paint)
        paint.color = 0xFF726751.toInt()
        paint.strokeWidth = .018f
        path.reset(); path.moveTo(-half, -height + .08f)
        path.quadTo(0f, -height + .24f, half, -height + .08f)
        canvas.drawPath(path, paint)
        val colours = intArrayOf(0xFFB6C2B5.toInt(), 0xFFE7D6B0.toInt(), 0xFFA9B5BB.toInt())
        repeat(3) { i ->
            val x = (i - 1) * width * .25f
            val top = -height + .14f
            val bottom = top + height * if (i == 1) .61f else .49f
            val sway = sin((phase * 2 * PI + i)).toFloat() * .035f
            val edge = width * .105f
            path.reset(); path.moveTo(x - edge, top); path.lineTo(x + edge, top)
            path.lineTo(x + edge + sway, bottom)
            path.quadTo(x + sway, bottom + .035f, x - edge + sway, bottom)
            path.close()
            paint.style = Paint.Style.FILL; paint.color = colours[i]
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = .012f; paint.color = 0x99746852.toInt()
            canvas.drawPath(path, paint)
            canvas.drawLine(x, top + .06f, x + sway * .7f, bottom - .05f, paint)
            paint.color = 0xFF92734D.toInt(); paint.strokeWidth = .025f
            canvas.drawLine(x - edge * .7f, top - .035f, x - edge * .7f, top + .045f, paint)
            canvas.drawLine(x + edge * .7f, top - .035f, x + edge * .7f, top + .045f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun vegetables(canvas: Canvas, width: Float, height: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0x659D815B
        canvas.drawRoundRect(-width / 2, -height, width / 2, 0f, .15f, .15f, paint)
        repeat(3) { row ->
            val y = -height + height * (row + .6f) / 3
            paint.color = 0x65977751
            paint.strokeWidth = .018f
            canvas.drawLine(-width * .44f, y + .11f, width * .44f, y + .11f, paint)
            repeat(6) { column ->
                val x = -width / 2 + width * (column + .6f) / 6
                paint.color = 0xFF859162.toInt()
                canvas.drawOval(x - .10f, y - .06f, x + .015f, y + .04f, paint)
                paint.color = 0xFF697C51.toInt()
                canvas.drawOval(x - .015f, y - .09f, x + .10f, y + .035f, paint)
                paint.color = 0xFFA6AF77.toInt()
                canvas.drawOval(x - .035f, y - .065f, x + .04f, y + .01f, paint)
            }
        }
    }

    private fun grain(canvas: Canvas, width: Float, height: Float) {
        paint.strokeWidth = .018f
        paint.strokeCap = Paint.Cap.ROUND
        repeat(7) { row -> repeat(12) { column ->
            val x = -width * .4f + column * width * .071f + sin((row * 13 + column).toFloat()) * .012f
            val y = -height * .85f + row * height * .105f
            paint.color = if ((row + column) % 3 == 0) 0xFFB99A59.toInt() else 0xFFE6CE8C.toInt()
            canvas.drawLine(x, y, x + .015f, y - .012f, paint)
        } }
    }

    private fun steam(canvas: Canvas, width: Float, height: Float, phase: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        repeat(3) { i ->
            val progress = (phase + i / 3f) % 1f
            val y = -height + .09f - progress * .72f
            val x = sin((progress * PI * 2 + i)).toFloat() * .08f
            paint.color = 0xFFF4EBD8.toInt()
            paint.alpha = (sin(progress * PI) * 130).toInt()
            paint.strokeWidth = width * .06f
            path.reset(); path.moveTo(x - .04f, y)
            path.cubicTo(x - .12f, y - .07f, x + .12f, y - .12f, x + .04f, y - .20f)
            canvas.drawPath(path, paint)
        }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }
}
