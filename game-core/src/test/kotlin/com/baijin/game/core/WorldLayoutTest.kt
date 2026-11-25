package com.baijin.game.core

import com.baijin.game.core.content.loadBundledSample
import org.junit.Assert.*
import org.junit.Test

class WorldLayoutTest {
    private val content = loadBundledSample()
    private val engine = GameEngine(content) { 44 }
    private val initial = (engine.dispatch(null, GameAction.Start(Gender.MALE, Attributes(7, 7, 7, 7))) as ActionResult.Changed).state

    @Test fun townStreamsAreConnectedAndOnlyEndAtTheSceneBoundary() {
        for (id in listOf("town", "town_east")) {
            val scene = content.scenes.single { it.id == id }
            val water = scene.terrain.flatMapIndexed { y, row -> row.mapIndexedNotNull { x, cell ->
                if (cell in "whv") Tile(x, y) else null
            } }.toSet()
            val reached = mutableSetOf(water.first())
            val pending = ArrayDeque(reached)
            while (pending.isNotEmpty()) {
                val here = pending.removeFirst()
                Direction.entries.map(here::step).filter { it in water && reached.add(it) }.forEach(pending::addLast)
            }
            assertEquals("$id has disconnected stream sections", water, reached)
            water.forEach { tile ->
                val neighbours = Direction.entries.count { tile.step(it) in water }
                assertTrue("$id stream stops inside town at $tile", neighbours >= 2 ||
                    tile.x == 0 || tile.x == scene.width - 1 || tile.y == 0 || tile.y == scene.height - 1)
            }
        }
        val west = content.scenes.single { it.id == "town" }
        val east = content.scenes.single { it.id == "town_east" }
        assertEquals('w', west.terrain[8].last())
        assertEquals('w', east.terrain[8].first())
    }

    @Test fun westKeepsBuildingLayoutAndMarksAllRoadCrossingsAsBridges() {
        val town = content.scenes.single { it.id == "town" }
        assertEquals(20, town.width)
        assertEquals(12, town.height)
        val bridges = town.terrain.flatMapIndexed { y, row -> row.mapIndexedNotNull { x, tile ->
            if (tile == 'h' || tile == 'v') Tile(x, y) else null
        } }.toSet()
        assertEquals(setOf(Tile(7, 1), Tile(14, 4), Tile(7, 6), Tile(4, 9)), bridges)
        assertEquals(setOf(Tile(6, 5), Tile(2, 7), Tile(16, 3), Tile(11, 6)),
            town.exits.filter { it.destinationExitId?.endsWith("home_door") == true ||
                it.id == "town_tofu_door" || it.id == "town_tailor_door" }.map { it.tile }.toSet())
        town.terrain.forEachIndexed { y, row -> row.forEachIndexed { x, tile ->
            if (tile == 'w') assertFalse(town.isWalkable(Tile(x, y)))
        } }
    }

    @Test fun pavedRoutesLeadToTheWaterPointAndEastTownEntrances() {
        val west = content.scenes.single { it.id == "town" }
        val southWellApproach = listOf(Tile(7, 5), Tile(7, 6), Tile(7, 7), Tile(6, 7), Tile(6, 8), Tile(5, 8))
        assertTrue(southWellApproach.all { west.isWalkable(it) && west.terrain[it.y][it.x] in "rhv" })

        val east = content.scenes.single { it.id == "town_east" }
        val paved = east.terrain.flatMapIndexed { y, row -> row.mapIndexedNotNull { x, surface ->
            Tile(x, y).takeIf { surface in "rhv" }
        } }.toSet()
        assertTrue(paved.all(east::isWalkable))
        val reached = mutableSetOf(east.exits.single { it.id == "town_east_west_exit" }.tile)
        val pending = ArrayDeque(reached)
        while (pending.isNotEmpty()) {
            val here = pending.removeFirst()
            Direction.entries.map(here::step).filter { it in paved && reached.add(it) }.forEach(pending::addLast)
        }
        assertEquals(paved, reached)
        assertTrue(east.exits.filter { it.onEntry }.all { it.tile in reached && it.returnTile in reached })
        assertTrue(content.locations.single { it.id == "town_medicine_stall" }.tile in reached)
    }

    @Test fun everyDoorCommitsTheCorrectSceneAndReturnSquareInOneStep() {
        val exits = content.scenes.flatMap { scene -> scene.exits.map { it.id to (scene to it) } }.toMap()
        content.scenes.forEach { scene -> scene.exits.filter { it.onEntry }.forEach { exit ->
            val before = initial.copy(position = WorldPosition(scene.id, exit.returnTile), gameTime = 20)
            val (targetScene, targetExit) = exits.getValue(checkNotNull(exit.destinationExitId))
            val entered = engine.dispatch(before, GameAction.Move(exit.direction)) as ActionResult.Changed
            assertEquals(WorldPosition(targetScene.id, targetExit.returnTile), entered.state.position)
            assertEquals(21L, entered.state.gameTime)
            val returned = engine.dispatch(entered.state, GameAction.Move(targetExit.direction)) as ActionResult.Changed
            assertEquals(before.position, returned.state.position)
            assertEquals(22L, returned.state.gameTime)
        } }
    }

    @Test fun aBridgeCrossesTheStreamButSteppingIntoWaterIsBlocked() {
        val westBank = initial.copy(position = WorldPosition("town", Tile(13, 4)))
        val bridge = engine.dispatch(westBank, GameAction.Move(Direction.EAST)) as ActionResult.Changed
        assertEquals(Tile(14, 4), bridge.state.position.tile)
        assertEquals(ActionResult.Blocked(ActionRequirement.PathBlocked), engine.dispatch(bridge.state, GameAction.Move(Direction.SOUTH)))
        val eastBank = engine.dispatch(bridge.state, GameAction.Move(Direction.EAST)) as ActionResult.Changed
        assertEquals(Tile(15, 4), eastBank.state.position.tile)
        assertEquals(2L, eastBank.state.gameTime)
        val southOfNorthBridge = initial.copy(position = WorldPosition("town", Tile(7, 2)))
        val northBridge = engine.dispatch(southOfNorthBridge, GameAction.Move(Direction.NORTH)) as ActionResult.Changed
        assertEquals(Tile(7, 1), northBridge.state.position.tile)
        assertEquals(ActionResult.Blocked(ActionRequirement.PathBlocked), engine.dispatch(northBridge.state, GameAction.Move(Direction.WEST)))
        val northBank = engine.dispatch(northBridge.state, GameAction.Move(Direction.NORTH)) as ActionResult.Changed
        assertEquals(Tile(7, 0), northBank.state.position.tile)
        assertEquals(2L, northBank.state.gameTime)
    }

    @Test fun allServicesAreReachableThroughDoorsAndHomeFurnitureStillBlocks() {
        val visited = mutableMapOf(initial.position to initial)
        val queue = ArrayDeque(listOf(initial))
        while (queue.isNotEmpty()) {
            val state = queue.removeFirst()
            Direction.entries.forEach { direction ->
                val result = engine.dispatch(state, GameAction.Move(direction))
                if (result is ActionResult.Changed && visited.putIfAbsent(result.state.position, result.state) == null) queue.addLast(result.state)
            }
        }
        content.locations.forEach { assertTrue(it.id, WorldPosition(it.sceneId, it.tile) in visited) }
        val granny = content.locations.single { it.id == "granny_service" }
        assertEquals(19L, visited.getValue(WorldPosition(granny.sceneId, granny.tile)).gameTime)
        val home = content.scenes.single { it.id == "player_home" }
        val door = home.exits.single()
        for (y in 0 until home.height) for (x in 0 until home.width) {
            val tile = Tile(x, y)
            if (home.isWalkable(tile) && tile != door.tile) {
                assertTrue("Home square $tile is sealed off", WorldPosition(home.id, tile) in visited)
            }
        }
        assertEquals(5L, visited.getValue(WorldPosition("town", Tile(7, 5))).gameTime)
        assertEquals(ActionResult.Blocked(ActionRequirement.PathBlocked), engine.dispatch(initial, GameAction.Move(Direction.EAST)))
        val hallway = initial.copy(position = WorldPosition(home.id, Tile(5, 4)))
        assertEquals(ActionResult.Blocked(ActionRequirement.PathBlocked), engine.dispatch(hallway, GameAction.Move(Direction.NORTH)))
        val facade = initial.copy(position = WorldPosition("town", Tile(7, 4)))
        assertEquals(ActionResult.Blocked(ActionRequirement.PathBlocked), engine.dispatch(facade, GameAction.Move(Direction.WEST)))
    }
}
