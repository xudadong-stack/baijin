package com.baijin.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.baijin.game.core.Direction
import com.baijin.game.core.content.SceneDefinition

/** Shared room shell; furniture and occupied tiles are supplied by each scene. */
internal class HomeArtwork(context: Context) {
    private val floor = mapTexture(context, R.drawable.home_floorboards, 8f, 4.6f)
    private val plaster = mapTexture(context, R.drawable.home_plaster, 3.4f)
    private val timber = mapTexture(context, R.drawable.home_timber, 3.8f, 1.6f)
    private val stone = mapTexture(context, R.drawable.home_stone, 2.2f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sill = BitmapFactory.decodeResource(context.resources, R.drawable.home_sill)
    private val rect = RectF()
    private val cutaway by lazy { PlayerHomeCutaway(context, floor, plaster, timber, stone) }

    fun drawRoom(canvas: Canvas, scene: SceneDefinition) {
        val w = scene.width.toFloat()
        val h = scene.height.toFloat()
        paint.color = 0xFF303632.toInt()
        canvas.drawPaint(paint)
        if (scene.id == SCENE_ID) {
            cutaway.draw(canvas, paint)
            return
        }
        canvas.drawRect(.2f, .15f, w - .2f, h - .1f, stone)
        canvas.drawRect(.85f, .9f, w - .85f, h - .85f, floor)
        canvas.drawRect(.3f, .05f, w - .3f, .75f, plaster)
        canvas.drawRect(.3f, .02f, w - .3f, .18f, timber)
        for (x in 1 until scene.width step 2) canvas.drawRect(x.toFloat(), .1f, x + .12f, .9f, timber)
        // Each boundary cell is a wall except for an actual door at that boundary.
        for (y in 1 until scene.height) {
            for (x in listOf(0, scene.width - 1)) {
                if (scene.exits.any { it.tile.x == x && it.tile.y == y }) {
                    canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, stone)
                } else {
                    canvas.drawRect(x + .20f, y - .1f, x + .80f, y + 1f, timber)
                    canvas.drawRect(x + .30f, y - .1f, x + .70f, y + .9f, plaster)
                }
            }
        }
        for (x in 1 until scene.width - 1) {
            if (scene.exits.any { it.tile.x == x && it.tile.y == scene.height - 1 }) {
                canvas.drawRect(x.toFloat(), h - 1f, x + 1f, h, stone)
                rect.set(x - .1f, h - .9f, x + 1.1f, h)
                canvas.drawBitmap(sill, null, rect, paint)
            } else {
                canvas.drawRect(x.toFloat(), h - .8f, x + 1f, h - .2f, plaster)
                canvas.drawRect(x.toFloat(), h - .2f, x + 1f, h, stone)
                canvas.drawRect(x.toFloat(), h - .87f, x + 1f, h - .76f, timber)
            }
        }
        scene.exits.filter { it.direction == Direction.EAST }.forEach {
            rect.set(w - .8f, it.tile.y + .12f, w - .12f, it.tile.y + .95f)
            canvas.drawBitmap(sill, null, rect, paint)
        }
    }

    companion object { const val SCENE_ID = "player_home" }
}

/** Continuous wall runs for the four-room 9 × 9 player home. */
private class PlayerHomeCutaway(
    context: Context,
    private val floor: Paint,
    plaster: Paint,
    private val timber: Paint,
    private val stone: Paint,
) {
    private val wallBase = wallStroke(stone, .46f)
    private val wallFrame = wallStroke(timber, .42f)
    private val wallTop = wallStroke(plaster, .28f)
    private val window = BitmapFactory.decodeResource(context.resources, R.drawable.home_window)
    private val mat = BitmapFactory.decodeResource(context.resources, R.drawable.home_mat)
    private val rect = RectF()

    private val slab = Path().apply {
        runFrom(.22f, .13f, 8.78f, .13f, 8.78f, 7.42f, 7.71f, 7.42f, 7.71f, 8.84f, .22f, 8.84f)
        close()
    }
    private val floorboards = Path().apply {
        runFrom(.47f, .38f, 8.53f, .38f, 8.53f, 7.19f, 7.46f, 7.19f, 7.46f, 8.54f, .47f, 8.54f)
        close()
    }
    private val kitchen = Path().apply {
        runFrom(.47f, 4.69f, 2.55f, 4.69f, 2.55f, 5.69f, 4.50f, 5.69f, 4.50f, 8.54f, .47f, 8.54f)
        close()
    }
    private val walls = Path().apply {
        // The eastern run is split at the outside door. The lower run folds around the recess.
        runFrom(8.53f, 5.16f, 8.53f, .38f, .47f, .38f, .47f, 8.54f,
            7.46f, 8.54f, 7.46f, 7.19f, 8.53f, 7.19f, 8.53f, 6.08f)
        // Both bedroom doors open onto the same hall at different depths.
        runFrom(.47f, 3.69f, 2.85f, 3.69f)
        runFrom(4.11f, 3.69f, 5.96f, 3.69f, 5.96f, 4.21f)
        runFrom(4.50f, .38f, 4.50f, 3.69f)
        runFrom(7.05f, 4.69f, 8.53f, 4.69f)
        // The kitchen has its own side doorway off the hall.
        runFrom(.47f, 4.69f, 2.55f, 4.69f, 2.55f, 5.69f, 4.50f, 5.69f, 4.50f, 6.10f)
        runFrom(4.50f, 7.19f, 4.50f, 8.54f)
    }

    fun draw(canvas: Canvas, bitmapPaint: Paint) {
        canvas.drawPath(slab, stone)
        canvas.drawPath(floorboards, floor)
        canvas.drawPath(kitchen, stone)
        canvas.drawRect(8.27f, 5.37f, 9f, 5.87f, stone)
        canvas.drawRect(2.97f, 3.62f, 3.99f, 3.76f, timber)
        canvas.drawRect(6.07f, 4.62f, 6.94f, 4.76f, timber)
        canvas.drawRect(4.42f, 6.22f, 4.58f, 7.06f, timber)
        canvas.drawRect(8.49f, 5.39f, 8.59f, 5.85f, timber)

        drawBitmap(canvas, mat, bitmapPaint, 1.99f, 2.43f, 2.51f, 2.98f)
        drawBitmap(canvas, mat, bitmapPaint, 6.91f, 3.98f, 7.62f, 4.47f)
        drawBitmap(canvas, mat, bitmapPaint, 4.90f, 7.12f, 7.01f, 8.16f)

        canvas.save()
        canvas.translate(0f, .15f)
        canvas.drawPath(walls, wallBase)
        canvas.restore()
        canvas.drawPath(walls, wallFrame)
        canvas.drawPath(walls, wallTop)
        drawBitmap(canvas, window, bitmapPaint, 1.94f, .17f, 2.84f, .56f)
        drawBitmap(canvas, window, bitmapPaint, 5.75f, .17f, 6.91f, .58f)
    }

    private fun drawBitmap(
        canvas: Canvas, bitmap: Bitmap, paint: Paint,
        left: Float, top: Float, right: Float, bottom: Float,
    ) {
        rect.set(left, top, right, bottom)
        canvas.drawBitmap(bitmap, null, rect, paint)
    }

    private fun wallStroke(paint: Paint, width: Float) = Paint(paint).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }

    private fun Path.runFrom(x: Float, y: Float, vararg points: Float) {
        moveTo(x, y)
        for (index in points.indices step 2) lineTo(points[index], points[index + 1])
    }
}
