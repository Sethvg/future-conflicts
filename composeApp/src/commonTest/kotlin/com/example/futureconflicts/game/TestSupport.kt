package com.example.futureconflicts.game

/** Helpers for building small, deterministic scenarios in tests. */

fun flatMap(
    cols: Int,
    rows: Int,
    fill: Terrain = Terrain.PLAINS,
    overrides: Map<Pos, Terrain> = emptyMap(),
): GameMap {
    val tiles = Array(cols * rows) { i -> overrides[Pos(i % cols, i / cols)] ?: fill }
    return GameMap(cols, rows, tiles)
}

/** An occupancy lookup over a fixed unit list, for Movement tests. */
fun occupancy(units: List<Unit>): (Pos) -> Unit? =
    { p -> units.firstOrNull { it.alive && it.pos == p } }

fun battleOf(map: GameMap, vararg units: Unit): Battle =
    Battle(Scenario(map, units.toList()))
