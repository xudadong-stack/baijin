package com.baijin.game

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.baijin.game.core.EquipmentSlot
import com.baijin.game.core.Gender

internal class TravellerArmourArtwork(context: Context) {
    private val resources = context.resources
    private val male by lazy { BitmapFactory.decodeResource(resources, R.drawable.hero_armour_male) }
    private val female by lazy { BitmapFactory.decodeResource(resources, R.drawable.hero_armour_female) }
    private val source = Rect()
    private val destination = RectF()

    fun draw(canvas: Canvas, gender: Gender, row: Int, frame: Int, body: RectF, paint: Paint,
             equipped: Map<EquipmentSlot, String>) {
        val scaleX = body.width() / 256f
        val scaleY = body.height() / 320f
        val left = body.left + 48 * scaleX
        val right = left + 160 * scaleX
        val atlasX = frame * 160
        for (layer in Layer.entries) {
            if (equipped[layer.slot] != layer.itemId) continue
            val atlasY = row * 327 + layer.offset
            source.set(atlasX, atlasY, atlasX + 160, atlasY + layer.bottom - layer.top)
            destination.set(left, body.top + layer.top * scaleY, right, body.top + layer.bottom * scaleY)
            canvas.drawBitmap(if (gender == Gender.MALE) male else female, source, destination, paint)
        }
    }

    // Each 160 × 327 atlas cell packs four strips cropped from the same 256 × 320 body frame.
    private enum class Layer(val slot: EquipmentSlot, val itemId: String, val top: Int, val bottom: Int, val offset: Int) {
        CAP(EquipmentSlot.HEAD, "cloth_cap", 12, 94, 0),
        COAT(EquipmentSlot.BODY, "traveler_coat", 132, 252, 82),
        WRISTGUARDS(EquipmentSlot.HANDS, "cloth_wristguard", 170, 233, 202),
        SHOES(EquipmentSlot.FEET, "cloth_shoes", 242, 304, 265),
    }
}
