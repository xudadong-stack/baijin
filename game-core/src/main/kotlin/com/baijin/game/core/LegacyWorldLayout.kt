package com.baijin.game.core

/** Previous layouts retained to validate saved coordinates before migration. */
internal object LegacyWorldLayout {
    private val walkable = mapOf(
        "town" to listOf("###################.#", "##...###...########.#", "##.........##.....#.#", "###.......###.....#.#", "#####......###.####.#", "##...####.###.....#.#", "##...##.....##.####.#", "####.##..#..#....##.#", "##..................#", ".....................", "######...#####..#####", "######...#####..#####", "######...#####..#####", "######..#######.#####", "###############.#####"),
        "player_home" to listOf("###########", "###..#...##", "###......##", "#........##", "#......##.#", "#.......#.#", "#.........#", "#####.#####"),
    )
    private val homeVersionTwo = listOf("#########", "###.#.###", "##.....##", "###...###",
        "###...###", "#........", "##.....##", "###.#.###", "#########")
    private val landmarks = mapOf(
        Tile(4, 9) to "town_start",
        Tile(4, 7) to "town_granny_gate",
        Tile(2, 5) to "town_granny_yard",
        Tile(8, 7) to "town_well",
        Tile(7, 13) to "town_firewood",
        Tile(9, 5) to "town_training_gate",
        Tile(3, 3) to "town_training_shed",
        Tile(9, 3) to "town_training_east",
        Tile(6, 2) to "town_trial_platform",
        Tile(5, 4) to "town_training_markings",
        Tile(12, 8) to "town_mending_stall",
        Tile(15, 11) to "town_medicine_stall",
        Tile(14, 6) to "town_hall_gate",
        Tile(13, 5) to "town_hall_counter",
        Tile(15, 2) to "town_hall_main_room",
        Tile(17, 5) to "town_hall_watch_room",
    )
    fun valid(position: WorldPosition): Boolean =
        walkable[position.sceneId]?.getOrNull(position.tile.y)?.getOrNull(position.tile.x) == '.'

    fun validHomeInVersionTwo(tile: Tile): Boolean =
        homeVersionTwo.getOrNull(tile.y)?.getOrNull(tile.x) == '.'

    fun destination(position: WorldPosition): String = if (position.sceneId == "player_home") {
        "player_home_start"
    } else landmarks[position.tile] ?: "town_start"
}
