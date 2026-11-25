package com.baijin.game.core.content

import com.baijin.game.core.Attribute
import com.baijin.game.core.Attributes
import com.baijin.game.core.Direction
import com.baijin.game.core.Tile
import kotlinx.serialization.Serializable

@Serializable
data class SceneDefinition(
    override val id: String,
    override val name: String,
    val tiles: List<String>,
    val exits: List<ExitDefinition>,
    val indoors: Boolean = false,
    val terrain: List<String> = emptyList(),
    val scenery: List<SceneryDefinition> = emptyList(),
) : ContentEntry {
    val width get() = tiles.firstOrNull()?.length ?: 0
    val height get() = tiles.size

    fun contains(tile: Tile): Boolean = tiles.getOrNull(tile.y)?.indices?.contains(tile.x) == true

    fun isWalkable(tile: Tile): Boolean = tiles.getOrNull(tile.y)?.getOrNull(tile.x) == '.'
}

@Serializable
data class ExitDefinition(
    override val id: String,
    override val name: String,
    val tile: Tile,
    val direction: Direction,
    val returnTile: Tile,
    val destinationExitId: String? = null,
    val onEntry: Boolean = false,
) : ContentEntry

@Serializable
enum class SceneryKind {
    HOME, HOUSE, SHOP, HALL, TOFU_SHOP, COURTHOUSE_GATE, RESIDENCE_GATE, WELL, TREE, BUSH, GRANNY,
    MEDICINE_STALL,
    BED, TRUNK, TABLE, STOVE, JAR, STOOL, COUNTER, FIREWOOD, TRAINING, BASKET,
    WALL_H, WALL_V, WALL_POST, MENDING, LAUNDRY, GRAIN_MAT, VEGETABLE_PATCH,
    FLOWER_POT, HARVEST_BASKET, TOFU_TABLE, HANDCART, WOODSHED, BENCH, SHOP_SIGN, LANTERNS,
    ROOSTER, HEN, SLEEPING_CAT, GROOMING_CAT, REEDS, MEADOW_GRASS, WASHBASKET, SHOP_BANNER,
}

@Serializable
data class SceneryDefinition(
    val kind: SceneryKind,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float? = null,
    val inscription: String? = null,
    val footprint: List<Tile> = emptyList(),
)

@Serializable
data class LocationDefinition(
    override val id: String,
    override val name: String,
    val sceneId: String,
    val tile: Tile,
    val kind: LandmarkKind? = null,
    val objectTile: Tile? = null,
) : ContentEntry

@Serializable
enum class LandmarkKind { GATE, YARD, WELL, FIREWOOD, PRACTICE, RACK, STALL, HALL }

@Serializable
data class CharacterCreationDefinition(
    val minAttribute: Int,
    val maxAttribute: Int,
    val attributePoints: Int,
    val maxLuckDraw: Int,
    val healthBase: Int,
    val healthPerConstitution: Int,
    val innerPowerBase: Int,
    val innerPowerPerConstitution: Int,
    val initialCoins: Int,
) {
    val attributeRange get() = minAttribute..maxAttribute
    val baseAttributes get() = Attributes(minAttribute, minAttribute, minAttribute, minAttribute)
    val balancedAttributes: Attributes
        get() {
            val each = minAttribute + attributePoints / Attribute.entries.size
            val remainder = attributePoints % Attribute.entries.size
            return Attribute.entries.take(remainder).fold(Attributes(each, each, each, each)) { points, attribute ->
                points.withValue(attribute, each + 1)
            }
        }

    fun remainingPoints(attributes: Attributes): Long = attributePoints - Attribute.entries.sumOf {
        attributes[it].toLong() - minAttribute
    }
}

@Serializable
data class GameTimeDefinition(
    val hungerInterval: Int,
    val maxHunger: Int,
    val thirstInterval: Int,
    val maxThirst: Int,
    val healthRecoveryInterval: Int,
    val healthRecovery: Int,
    val innerPowerRecoveryInterval: Int,
    val innerPowerRecovery: Int,
    val meditationDuration: Int,
    val meditationRecovery: Int,
)
