package com.example.futureconflicts.game

/**
 * Fog of war as a **per-player view** derived from the authoritative world — the
 * sim itself is never hidden from itself (important for determinism/multiplayer).
 *
 * A team sees the union of its units' sight radii (Manhattan) plus a radius around
 * its owned buildings. Terrain shapes it:
 *  - a unit on a **mountain** sees farther ([MOUNTAIN_SIGHT_BONUS]);
 *  - an enemy on a **forest** tile is *hidden* unless one of your units is adjacent
 *    (the classic ambush rule) — so forests reward scouting and punish blind pushes.
 */
object Vision {
    const val MOUNTAIN_SIGHT_BONUS = 1
    const val BUILDING_SIGHT = 2

    fun sightOf(unit: Unit, terrain: Terrain): Int =
        unit.type.vision + if (terrain == Terrain.MOUNTAIN) MOUNTAIN_SIGHT_BONUS else 0

    /** All tiles [team] can currently see. */
    fun visibleTiles(
        map: GameMap,
        team: Team,
        units: List<Unit>,
        buildings: Collection<Building>,
    ): Set<Pos> {
        val visible = HashSet<Pos>()
        for (u in units) if (u.alive && u.team == team) {
            addRadius(map, visible, u.pos, sightOf(u, map[u.pos]))
        }
        for (b in buildings) if (b.owner == team) {
            addRadius(map, visible, b.pos, BUILDING_SIGHT)
        }
        return visible
    }

    /** Can [viewer] see [unit], given the viewer's [visible] tiles? */
    fun isUnitVisible(
        map: GameMap,
        viewer: Team,
        unit: Unit,
        visible: Set<Pos>,
        units: List<Unit>,
    ): Boolean {
        if (unit.team == viewer) return true
        if (unit.pos !in visible) return false
        if (map[unit.pos] == Terrain.FOREST) {
            // Hidden in the trees unless a viewer's unit is right next to it.
            return units.any { it.alive && it.team == viewer && it.pos.manhattan(unit.pos) == 1 }
        }
        return true
    }

    private fun addRadius(map: GameMap, set: MutableSet<Pos>, center: Pos, r: Int) {
        for (dy in -r..r) {
            for (dx in -r..r) {
                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > r) continue
                val p = Pos(center.x + dx, center.y + dy)
                if (map.inBounds(p)) set.add(p)
            }
        }
    }
}
