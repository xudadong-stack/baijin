package com.baijin.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.annotation.DrawableRes
import com.baijin.game.core.content.SceneDefinition
import com.baijin.game.core.content.SceneryKind

internal class TownArtwork(private val context: Context) {
    val stone = mapTexture(context, R.drawable.town_v2_stone, 3.4f, 2.2f)
    val yard = mapTexture(context, R.drawable.town_v2_yard, 7f, 5f)
    private val garden = bitmap(R.drawable.town_v2_garden)
    private val images = mutableMapOf<SceneryKind, Bitmap>()

    data class Sprite(
        val image: Bitmap?, val centerX: Float, val feetY: Float, val width: Float,
        val height: Float? = null, val depth: Float = feetY, val shadow: Boolean = true,
        val inscription: String? = null,
        val detail: SceneryKind? = null,
    )

    fun groundCover(scene: SceneDefinition): List<Sprite> = scene.scenery.filter {
        it.kind == SceneryKind.TREE || it.kind == SceneryKind.BUSH
    }.map { Sprite(garden, it.x, it.y + .2f, it.width * 1.7f,
        height = it.width, shadow = false) } + scene.scenery.filter {
        it.kind == SceneryKind.GRAIN_MAT || it.kind == SceneryKind.VEGETABLE_PATCH
            || it.kind == SceneryKind.MEADOW_GRASS
    }.map {
        Sprite(when (it.kind) {
            SceneryKind.GRAIN_MAT -> images.getOrPut(it.kind) { bitmap(R.drawable.home_mat) }
            SceneryKind.MEADOW_GRASS -> images.getOrPut(it.kind) { bitmap(R.drawable.town_village_meadow_grass) }
            else -> null
        },
            it.x, it.y, it.width, it.height, shadow = false, detail = it.kind)
    }

    fun scenery(scene: SceneDefinition): List<Sprite> = scene.scenery.filter {
        it.kind != SceneryKind.GRAIN_MAT && it.kind != SceneryKind.VEGETABLE_PATCH
            && it.kind != SceneryKind.MEADOW_GRASS
    }.map { prop ->
        Sprite(if (prop.kind == SceneryKind.LAUNDRY) null else images.getOrPut(prop.kind) { bitmap(resource(prop.kind)) },
            prop.x, prop.y, prop.width, prop.height,
            shadow = prop.kind !in setOf(SceneryKind.LAUNDRY, SceneryKind.REEDS, SceneryKind.SHOP_SIGN, SceneryKind.LANTERNS, SceneryKind.SHOP_BANNER),
            inscription = prop.inscription, detail = prop.kind)
    }.sortedBy(Sprite::depth)

    @DrawableRes
    private fun resource(kind: SceneryKind): Int = when (kind) {
        SceneryKind.HOME -> R.drawable.home_exterior
        SceneryKind.HOUSE -> R.drawable.town_v2_house
        SceneryKind.SHOP -> R.drawable.town_v2_shop
        SceneryKind.HALL -> R.drawable.town_v2_hall
        SceneryKind.TOFU_SHOP -> R.drawable.town_house
        SceneryKind.MEDICINE_STALL -> R.drawable.town_v2_medicine
        SceneryKind.COURTHOUSE_GATE -> R.drawable.town_open_gate
        SceneryKind.RESIDENCE_GATE -> R.drawable.town_gate
        SceneryKind.WELL -> R.drawable.town_v2_well
        SceneryKind.TREE -> R.drawable.town_v2_tree
        SceneryKind.BUSH -> R.drawable.town_v2_bush
        SceneryKind.GRANNY -> R.drawable.town_v2_granny
        SceneryKind.BED -> R.drawable.home_bed
        SceneryKind.TRUNK -> R.drawable.home_trunk
        SceneryKind.TABLE, SceneryKind.COUNTER -> R.drawable.home_table
        SceneryKind.STOVE -> R.drawable.home_stove
        SceneryKind.JAR -> R.drawable.home_jar
        SceneryKind.STOOL -> R.drawable.home_stool
        SceneryKind.FIREWOOD -> R.drawable.town_v2_firewood
        SceneryKind.TRAINING -> R.drawable.town_v2_training
        SceneryKind.BASKET -> R.drawable.town_v2_courtyard
        SceneryKind.WALL_H -> R.drawable.town_v2_wall_h
        SceneryKind.WALL_V -> R.drawable.town_v2_wall_v
        SceneryKind.WALL_POST -> R.drawable.town_v2_post
        SceneryKind.MENDING -> R.drawable.town_v2_mending
        SceneryKind.FLOWER_POT -> R.drawable.town_village_flower_pot
        SceneryKind.HARVEST_BASKET -> R.drawable.town_village_harvest_basket
        SceneryKind.TOFU_TABLE -> R.drawable.town_village_tofu_table
        SceneryKind.HANDCART -> R.drawable.town_village_handcart
        SceneryKind.WOODSHED -> R.drawable.town_village_woodshed
        SceneryKind.BENCH -> R.drawable.town_village_bench
        SceneryKind.SHOP_SIGN -> R.drawable.town_village_shop_sign
        SceneryKind.LANTERNS -> R.drawable.town_village_lanterns
        SceneryKind.ROOSTER -> R.drawable.town_village_rooster
        SceneryKind.HEN -> R.drawable.town_village_hen
        SceneryKind.SLEEPING_CAT -> R.drawable.town_village_sleeping_cat
        SceneryKind.GROOMING_CAT -> R.drawable.town_village_grooming_cat
        SceneryKind.REEDS -> R.drawable.town_village_reeds
        SceneryKind.MEADOW_GRASS -> R.drawable.town_village_meadow_grass
        SceneryKind.WASHBASKET -> R.drawable.town_village_washbasket
        SceneryKind.SHOP_BANNER -> R.drawable.town_village_shop_banner
        SceneryKind.LAUNDRY, SceneryKind.GRAIN_MAT, SceneryKind.VEGETABLE_PATCH -> error("Ground or canvas detail has no scenery bitmap")
    }

    private fun bitmap(@DrawableRes id: Int): Bitmap = BitmapFactory.decodeResource(context.resources, id)
}

internal fun mapTexture(context: Context, @DrawableRes resource: Int, width: Float, height: Float = width): Paint {
    val image = BitmapFactory.decodeResource(context.resources, resource)
    val pattern = BitmapShader(image, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
    pattern.setLocalMatrix(Matrix().apply { setScale(width / image.width, height / image.height) })
    return Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = pattern }
}
