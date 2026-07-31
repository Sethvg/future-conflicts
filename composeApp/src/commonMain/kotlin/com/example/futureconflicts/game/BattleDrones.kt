package com.example.futureconflicts.game

/**
 * The **Drone Command** flight: launching, autonomous scouting, and replacement cooldowns.
 *
 * At the start of a side's turn (before it takes manual control) each owned Drone Command
 * tops the flight up to `level` drones — subject to a per-slot [Drones.REBUILD_COOLDOWN]
 * after a loss — and then flies every drone automatically:
 *  - **scout**: move to the tile that reveals the most currently-fogged ground;
 *  - **return**: once fuel is only just enough to fly home, head back to the Drone Command
 *    (landing on it refuels via the normal [refuelOrCrash] path).
 *
 * Drones are free, unarmed, and shredded by anti-air — the cost is logistics, not gold.
 * Every choice here is **deterministic**: ties break on a fixed (y, x) ordering so the jvm
 * host tests, Android, and iOS all produce identical flights.
 *
 * (Split out of [Battle] per the core's no-monolith rule.)
 */

/** Run the whole drone phase for [team]: replacements, then autonomous flight. */
internal fun Battle.runDronePhase(team: Team) {
    if (winner != null) return // never spawn onto a finished board
    tickDroneCooldowns(team)
    launchDrones(team)
    flyDrones(team)
}

/** Count down each side's pending replacement slots. */
internal fun Battle.tickDroneCooldowns(team: Team) {
    val pending = droneCooldowns[team] ?: return
    if (pending.isEmpty()) return
    for (i in pending.indices) pending[i] = pending[i] - 1
    pending.removeAll { it <= 0 }
}

/** Top the flight up to the owned Drone Commands' total level, spreading launches across
 *  the bases (round-robin) so a captured forward base doesn't swallow every replacement. */
internal fun Battle.launchDrones(team: Team) {
    val commands = buildings.values
        .filter { it.owner == team && it.kind == Building.Kind.DRONE_COMMAND }
        .sortedWith(compareBy({ it.pos.y }, { it.pos.x }))
    if (commands.isEmpty()) return

    val capacity = commands.sumOf { it.level }
    val onCooldown = droneCooldowns[team]?.size ?: 0
    val alive = units.count { it.alive && it.team == team && it.type == UnitType.DRONE }
    var toLaunch = capacity - onCooldown - alive
    if (toLaunch <= 0) return

    var progressed = true
    while (toLaunch > 0 && progressed) {
        progressed = false
        for (c in commands) {
            if (toLaunch <= 0) break
            val spawn = spawnTileFor(c.pos, UnitType.DRONE) ?: continue
            units.add(Unit(UnitType.DRONE, team, spawn).also { it.hasActed = true })
            toLaunch--
            progressed = true
        }
    }
}

/** Fly every drone: scout unseen ground, or return to base when fuel runs low. */
internal fun Battle.flyDrones(team: Team) {
    val drones = units.filter { it.alive && it.team == team && it.type == UnitType.DRONE }
    if (drones.isEmpty()) return
    val bases = buildings.values
        .filter { it.owner == team && it.kind == Building.Kind.DRONE_COMMAND }
        .map { it.pos }

    // The autonomous flight shouldn't rewrite the turn banner with per-move chatter.
    val banner = message

    for (d in drones) {
        val dest = droneDestination(d, team, bases)
        if (dest != null && dest != d.pos) {
            // Drones are flown by the AI, but still move through the Action funnel so the
            // sim stays deterministic/serializable. Clear the flag so the move validates…
            d.hasActed = false
            apply(Action.Wait(d.pos, dest))
        }
        // …and always spend it again: the player never gets manual control of a drone,
        // and the enemy planner must never re-drive one.
        d.hasActed = true
    }

    if (winner == null) message = banner
}

/** Where a drone should move this turn: home if fuel is low, else the most revealing tile. */
internal fun Battle.droneDestination(drone: Unit, team: Team, bases: List<Pos>): Pos? {
    val reach = reachableFor(drone)

    val nearestBase = bases.minWithOrNull(compareBy({ it.manhattan(drone.pos) }, { it.y }, { it.x }))
    if (nearestBase != null && needsToReturn(drone, nearestBase)) {
        // Land on the base if we can reach it, else close the distance (deterministic ties).
        if (reach.containsKey(nearestBase)) return nearestBase
        return reach.keys.minWithOrNull(
            compareBy({ it.manhattan(nearestBase) }, { it.y }, { it.x }),
        )
    }

    // Scout: pick the reachable tile that would uncover the most fog (honours the fog
    // toggle and the REVEAL boon by going through the team's own view).
    val visible = visibleTiles(team)
    if (visible.size >= allTiles.size) return null // nothing left to scout
    return reach.keys.maxWithOrNull(
        compareBy({ revealCountAt(drone, it, visible) }, { -it.y }, { -it.x }),
    )
}

/** Fuel is spent per *turn*, not per tile, so convert the trip home into turns first. */
private fun Battle.needsToReturn(drone: Unit, base: Pos): Boolean {
    val perTurn = effectiveMove(drone).coerceAtLeast(1)
    val turnsHome = (base.manhattan(drone.pos) + perTurn - 1) / perTurn
    return drone.fuel <= turnsHome * Battle.FUEL_BURN_PER_TURN + Drones.RETURN_FUEL_MARGIN
}

/** How many currently-fogged tiles a drone standing on [tile] would reveal. */
private fun Battle.revealCountAt(drone: Unit, tile: Pos, visible: Set<Pos>): Int {
    val r = Vision.sightOf(drone, map[tile])
    var n = 0
    for (dy in -r..r) {
        for (dx in -r..r) {
            if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > r) continue
            val p = Pos(tile.x + dx, tile.y + dy)
            if (map.inBounds(p) && p !in visible) n++
        }
    }
    return n
}

/** Note a lost drone so its slot stays empty for the rebuild cooldown. [fromCrash] adds a
 *  turn so a fuel loss and a combat loss cost the same, despite the crash landing after
 *  the cooldown tick. */
internal fun Battle.noteDroneLost(team: Team, fromCrash: Boolean = false) {
    val turns = Drones.REBUILD_COOLDOWN + if (fromCrash) 1 else 0
    droneCooldowns.getOrPut(team) { mutableListOf() }.add(turns)
}
