package com.example.futureconflicts.game

/** Move cost sentinel meaning "ground units cannot enter" (e.g. deep sea). */
const val IMPASSABLE: Int = 99

/**
 * Terrain occupying a grid cell.
 *
 * @param moveCost movement points spent to *enter* this tile ([IMPASSABLE] marks
 *   ground-unpassable water/void).
 * @param defense defense "stars" (0–4). Higher = the defender takes less damage
 *   while standing here (see [Combat]).
 */
enum class Terrain(
    val label: String,
    val moveCost: Int,
    val defense: Int,
) {
    PLAINS("Plains", moveCost = 1, defense = 1),
    ROAD("Road", moveCost = 1, defense = 0),
    FOREST("Forest", moveCost = 2, defense = 2),
    MOUNTAIN("Mountain", moveCost = 3, defense = 4),
    CITY("City", moveCost = 1, defense = 3),
    HQ("HQ", moveCost = 1, defense = 4),
    SEA("Sea", moveCost = IMPASSABLE, defense = 0);

    val passable: Boolean get() = moveCost < IMPASSABLE
}
