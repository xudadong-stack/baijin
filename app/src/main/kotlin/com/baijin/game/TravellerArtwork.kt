package com.baijin.game

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.baijin.game.core.Direction
import com.baijin.game.core.EquipmentSlot
import com.baijin.game.core.Gender

internal class TravellerArtwork(context: Context) {
    private val male = BitmapFactory.decodeResource(context.resources, R.drawable.hero_walk_male)
    private val female = BitmapFactory.decodeResource(context.resources, R.drawable.hero_walk_female)
    private val source = Rect()
    private val destination = RectF()
    private val staff = LightWoodStaffArtwork()
    private val armour = TravellerArmourArtwork(context)

    fun draw(canvas: Canvas, gender: Gender, facing: Direction, frame: Int,
             x: Float, feet: Float, height: Float, depth: Float, paint: Paint, equipped: Map<EquipmentSlot, String>) {
        val image = if (gender == Gender.MALE) male else female
        val row = when (facing) {
            Direction.SOUTH -> 0
            Direction.WEST -> 1
            Direction.EAST -> 2
            Direction.NORTH -> 3
        }
        val w = image.width / 3
        val h = image.height / 4
        source.set(frame * w, row * h, (frame + 1) * w, (row + 1) * h)
        // Atlas cells are 256 x 320, with a 280 px body and the feet at y=300.
        val width = height * 256f / 280f
        val fullHeight = height * 320f / 280f / depth
        val bottom = feet + height * 20f / 280f / depth
        destination.set(x - width / 2, bottom - fullHeight, x + width / 2, bottom)

        fun drawOutfit() {
            canvas.drawBitmap(image, source, destination, paint)
            armour.draw(canvas, gender, row, frame, destination, paint, equipped)
        }

        val hold = if (equipped[EquipmentSlot.WEAPON] == LightWoodStaffArtwork.ITEM_ID) staff.hold(gender, row, frame) else null
        // When facing west, the right hand is on the far side of the body.
        if (hold != null && facing == Direction.WEST) staff.draw(canvas, destination, hold)
        drawOutfit()
        if (hold != null && facing != Direction.WEST) {
            staff.draw(canvas, destination, hold)
            val scaleX = destination.width() / w
            val scaleY = destination.height() / h
            val gripX = destination.left + hold.x * scaleX
            val gripY = destination.top + hold.y * scaleY
            val checkpoint = canvas.save()
            canvas.clipRect(gripX - 11 * scaleX, gripY - 7 * scaleY, gripX + 11 * scaleX, gripY + 8 * scaleY)
            drawOutfit()
            canvas.restoreToCount(checkpoint)
        }
    }
}
