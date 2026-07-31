package com.example.futureconflicts.game

/**
 * Movement reachability via Dijkstra over terrain move-cost. The board is tiny
 * (~120 tiles) so a plain "repeatedly pick the cheapest frontier tile" search is
 * more than fast enough and avoids pulling in a priority-queue dependency.
 *
 * Rules: a unit may pass *through* friendly units but may not pass through or
 * stop on enemies, and may not stop on a tile occupied by any other unit.
 */
object Movement {

    /**
     * @param occupant returns the (living) unit standing on a tile, or null.
     * @return every tile the unit can *end its move* on, mapped to the move-point
     *   cost to get there. Always includes the unit's own tile at cost 0.
     */
    fun reachable(map: GameMap, unit: Unit, occupant: (Pos) -> Unit?): Map<Pos, Int> {
        val budget = unit.type.maxMove
        val cost = HashMap<Pos, Int>()
        cost[unit.pos] = 0
        val settled = HashSet<Pos>()

        while (true) {
            // Pick the cheapest not-yet-settled frontier tile.
            var cur: Pos? = null
            var curCost = Int.MAX_VALUE
            for ((p, c) in cost) {
                if (p !in settled && c < curCost) {
                    cur = p; curCost = c
                }
            }
            if (cur == null) break
            settled.add(cur)

            for (n in map.neighbors(cur)) {
                val terrain = map[n]
                if (!terrain.passable) continue
                val blocker = occupant(n)
                if (blocker != null && blocker.team != unit.team) continue // enemy blocks passage
                val next = curCost + terrain.moveCost
                if (next <= budget && next < (cost[n] ?: Int.MAX_VALUE)) {
                    cost[n] = next
                }
            }
        }

        // Can't END on a tile occupied by another unit (allies included).
        return cost.filterKeys { it == unit.pos || occupant(it) == null }
    }
}
