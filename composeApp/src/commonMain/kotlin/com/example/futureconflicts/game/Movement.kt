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
     * @param budget movement points available (defaults to the unit's base move;
     *   callers pass the Commander/Elite-adjusted value).
     * @return every tile the unit can *end its move* on, mapped to the move-point
     *   cost to get there. Always includes the unit's own tile at cost 0.
     */
    fun reachable(
        map: GameMap,
        unit: Unit,
        occupant: (Pos) -> Unit?,
        budget: Int = unit.type.maxMove,
    ): Map<Pos, Int> {
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

            // Movement rules come from the unit's class (ground/air/naval), not type checks.
            val cls = unit.unitClass
            for (n in map.neighbors(cur)) {
                val terrain = map[n]
                if (!unit.canEnter(terrain)) continue // e.g. ground can't enter sea; air flies over
                val blocker = occupant(n)
                // Enemies block ground/naval movement; aircraft overfly them.
                if (!cls.ignoresTerrain && blocker != null && blocker.team != unit.team) continue
                val next = curCost + if (cls.ignoresTerrain) 1 else terrain.moveCost
                if (next <= budget && next < (cost[n] ?: Int.MAX_VALUE)) {
                    cost[n] = next
                }
            }
        }

        // Can't END on a tile occupied by another unit (allies included).
        return cost.filterKeys { it == unit.pos || occupant(it) == null }
    }
}
