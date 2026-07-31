package com.example.futureconflicts.game

/** Immutable terrain grid. Units live in [Battle], not here. */
class GameMap(
    val cols: Int,
    val rows: Int,
    private val tiles: Array<Terrain>,
) {
    fun inBounds(p: Pos): Boolean = p.x in 0 until cols && p.y in 0 until rows

    operator fun get(p: Pos): Terrain = tiles[p.y * cols + p.x]
    operator fun get(x: Int, y: Int): Terrain = tiles[y * cols + x]

    fun neighbors(p: Pos): List<Pos> = buildList(4) {
        val (x, y) = p
        if (x > 0) add(Pos(x - 1, y))
        if (x < cols - 1) add(Pos(x + 1, y))
        if (y > 0) add(Pos(x, y - 1))
        if (y < rows - 1) add(Pos(x, y + 1))
    }
}

/** A complete starting position: the map, both armies, and the capturable buildings. */
class Scenario(
    val map: GameMap,
    val units: List<Unit>,
    val buildings: List<Building> = emptyList(),
)

/**
 * The built-in "Twin Ridges" skirmish. Hand-authored ASCII terrain (Blue army
 * south, Red army north) so the vertical slice is playable with zero content
 * tooling. Legend: `.` plains · `-` road · `f` forest · `m` mountain · `c` city ·
 * `s` sea · `H` HQ.
 */
object Scenarios {

    private val TWIN_RIDGES_ROWS = listOf(
        "ABH.Y..mm.",
        ".c..ff..m.",
        "...ff.....",
        "ss......c.",
        "s...m.....",
        "....mm....",
        "..------..",
        "....mm....",
        ".c...m...s",
        "....ff..ss",
        ".mm....c..",
        "....Y.HBA.",
    )

    private fun terrainOf(c: Char): Terrain = when (c) {
        '.' -> Terrain.PLAINS
        '-' -> Terrain.ROAD
        'f' -> Terrain.FOREST
        'm' -> Terrain.MOUNTAIN
        'c', 'B', 'Y', 'A' -> Terrain.CITY // production buildings stand on urban tiles
        's' -> Terrain.SEA
        'H' -> Terrain.HQ
        else -> Terrain.PLAINS
    }

    fun twinRidges(): Scenario {
        val rows = TWIN_RIDGES_ROWS.size
        val cols = TWIN_RIDGES_ROWS[0].length
        val tiles = Array(rows * cols) { i -> terrainOf(TWIN_RIDGES_ROWS[i / cols][i % cols]) }
        val map = GameMap(cols, rows, tiles)

        // Buildings derive from the source char (so Barracks/Factory, which share the
        // CITY tile look, stay distinct from neutral cities). HQ + production buildings
        // start owned by their half (north = Red, south = Blue); cities start neutral.
        val buildings = buildList {
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    val side = if (y < rows / 2) Team.ENEMY else Team.PLAYER
                    when (TWIN_RIDGES_ROWS[y][x]) {
                        'H' -> add(Building(Pos(x, y), Building.Kind.HQ, owner = side))
                        'c' -> add(Building(Pos(x, y), Building.Kind.CITY, owner = null))
                        'B' -> add(Building(Pos(x, y), Building.Kind.BARRACKS, owner = side))
                        'Y' -> add(Building(Pos(x, y), Building.Kind.FACTORY, owner = side))
                        'A' -> add(Building(Pos(x, y), Building.Kind.AIRPORT, owner = side))
                    }
                }
            }
        }

        val units = listOf(
            // Red army (enemy) — north
            Unit(UnitType.ARTILLERY, Team.ENEMY, Pos(3, 0)),
            Unit(UnitType.INFANTRY, Team.ENEMY, Pos(1, 1)),
            Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 1)),
            Unit(UnitType.TANK, Team.ENEMY, Pos(4, 2)),
            Unit(UnitType.RECON, Team.ENEMY, Pos(6, 1)),
            // Blue army (player) — south
            Unit(UnitType.ARTILLERY, Team.PLAYER, Pos(5, 11)),
            Unit(UnitType.INFANTRY, Team.PLAYER, Pos(3, 10)),
            Unit(UnitType.INFANTRY, Team.PLAYER, Pos(8, 10)),
            Unit(UnitType.RECON, Team.PLAYER, Pos(2, 9)),
            Unit(UnitType.TANK, Team.PLAYER, Pos(5, 10)),
        )
        return Scenario(map, units, buildings)
    }
}
