package com.baijin.game.core

import kotlinx.serialization.Serializable

@Serializable
data class Tile(val x: Int, val y: Int) {
    fun step(direction: Direction) = Tile(x + direction.dx, y + direction.dy)
}

@Serializable
data class WorldPosition(val sceneId: String, val tile: Tile)

@Serializable
enum class Direction(val dx: Int, val dy: Int) {
    NORTH(0, -1),
    SOUTH(0, 1),
    WEST(-1, 0),
    EAST(1, 0);

    val opposite: Direction
        get() = when (this) {
            NORTH -> SOUTH
            SOUTH -> NORTH
            WEST -> EAST
            EAST -> WEST
        }
}
